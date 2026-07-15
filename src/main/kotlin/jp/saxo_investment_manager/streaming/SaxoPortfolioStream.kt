package jp.saxo_investment_manager.streaming

import jp.saxo_investment_manager.api.AccountBalanceDto
import jp.saxo_investment_manager.api.PositionDto
import jp.saxo_investment_manager.config.SaxoProperties
import jp.saxo_investment_manager.config.SaxoTokenProvider
import jp.saxo_investment_manager.saxo.AccountClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import java.net.URI
import java.util.UUID

/**
 * Streams live account state from Saxo — the aggregate balance and open net positions — over a
 * single WebSocket connection, exposing them as hot [balances] and [positions] flows.
 *
 * Unlike prices/charts (per-instrument, reference-counted), account state is a single context for
 * the whole client, so this holds exactly two fixed subscriptions:
 * `port/v1/balances/subscriptions` and `port/v1/netpositions/subscriptions`. The balance is a
 * single-entity subscription whose deltas are field-level (deep-merged like an info-price); net
 * positions is a collection subscription whose deltas carry whole position objects keyed by
 * `NetPositionId`, upserted and re-emitted as a full valued list. The retained state and its merge
 * rules live in [PortfolioState].
 *
 * The subscription endpoints require an explicit `ClientKey` in their arguments (there is no `/me`
 * convenience for subscriptions), resolved once via [AccountClient.me] and cached.
 */
@Service
class SaxoPortfolioStream(
    private val saxoWebClient: WebClient,
    private val tokenProvider: SaxoTokenProvider,
    private val properties: SaxoProperties,
    private val objectMapper: ObjectMapper,
    private val accountClient: AccountClient,
) : DisposableBean {
    private val log = LoggerFactory.getLogger(javaClass)
    private val contextId = "acct" + UUID.randomUUID().toString().replace("-", "").take(40)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wsClient = ReactorNettyWebSocketClient()

    private val _balances =
        MutableSharedFlow<AccountBalanceDto>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val balances: SharedFlow<AccountBalanceDto> = _balances

    private val _positions =
        MutableSharedFlow<List<PositionDto>>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val positions: SharedFlow<List<PositionDto>> = _positions

    private val state = PortfolioState(objectMapper)

    // Reference ids of the current connection's two subscriptions; null until created, cleared on
    // reconnect so the subscriptions are recreated against the fresh socket.
    @Volatile
    private var balanceRef: String? = null
    @Volatile
    private var netPosRef: String? = null

    @Volatile
    private var clientKey: String? = null

    private val connectMutex = Mutex()
    @Volatile
    private var connectionStarted = false

    override fun destroy() {
        scope.cancel()
        log.info("Saxo portfolio stream scope cancelled")
    }

    /** Ensures the connection is up and both subscriptions exist. */
    suspend fun start() {
        ensureConnection()
        ensureSubscriptions()
    }

    /** The latest known balance from the retained snapshot, if any has arrived. */
    fun currentBalance(): AccountBalanceDto? = state.currentBalance()

    /** The latest known net positions; empty once subscribed but holding nothing. */
    fun currentPositions(): List<PositionDto> = state.currentPositions()

    private suspend fun ensureConnection() {
        if (connectionStarted) return
        connectMutex.withLock {
            if (connectionStarted) return
            connectionStarted = true
            scope.launch { connectionLoop() }
        }
    }

    private suspend fun clientKey(): String =
        clientKey ?: accountClient.me().clientKey.also { clientKey = it }

    private suspend fun ensureSubscriptions() {
        createBalanceSubscription()
        createNetPositionsSubscription()
    }

    private suspend fun createBalanceSubscription() {
        if (balanceRef != null) return
        val referenceId = "bal" + UUID.randomUUID().toString().replace("-", "").take(20)
        val body = mapOf(
            "ContextId" to contextId,
            "ReferenceId" to referenceId,
            "Arguments" to mapOf("ClientKey" to clientKey()),
            "RefreshRate" to 1000,
        )
        val response =
            saxoWebClient.post().uri("/port/v1/balances/subscriptions").bodyValue(body).retrieve().awaitBody<JsonNode>()
        val snapshot = response.get("Snapshot") as? ObjectNode ?: objectMapper.createObjectNode()
        balanceRef = referenceId
        _balances.tryEmit(state.applyBalanceSnapshot(snapshot))
        log.debug("Subscribed balance stream as {}", referenceId)
    }

    private suspend fun createNetPositionsSubscription() {
        if (netPosRef != null) return
        val referenceId = "npos" + UUID.randomUUID().toString().replace("-", "").take(20)
        val body = mapOf(
            "ContextId" to contextId,
            "ReferenceId" to referenceId,
            "Arguments" to mapOf(
                "ClientKey" to clientKey(),
                "FieldGroups" to listOf("NetPositionBase", "NetPositionView", "DisplayAndFormat"),
            ),
            "RefreshRate" to 1000,
        )
        val response = saxoWebClient.post().uri("/port/v1/netpositions/subscriptions")
            .bodyValue(body).retrieve().awaitBody<JsonNode>()
        netPosRef = referenceId
        val positions = state.applyPositionsSnapshot(response.get("Snapshot")?.get("Data"))
        _positions.tryEmit(positions)
        log.debug("Subscribed net-positions stream as {} ({} positions)", referenceId, positions.size)
    }

    private suspend fun connectionLoop() {
        val acc = Accumulator()
        while (scope.isActive) {
            balanceRef = null
            netPosRef = null
            state.clear()
            acc.reset()
            try {
                val token = tokenProvider.accessToken()
                val uri = URI("${properties.environment.streamingBaseUrl}/connect?contextId=$contextId")
                val headers = HttpHeaders().apply { set(HttpHeaders.AUTHORIZATION, "BEARER $token") }
                val handler = WebSocketHandler { session ->
                    session.receive()
                        .doOnNext { message ->
                            val db = message.payload
                            val bytes = ByteArray(db.readableByteCount())
                            db.read(bytes)
                            onBytes(acc, bytes)
                        }
                        .then()
                }
                // Re-create the subscriptions shortly after the socket opens.
                val resubscribe = scope.launch {
                    delay(500)
                    runCatching { ensureSubscriptions() }
                }
                log.info("Connecting Saxo portfolio stream ({})", properties.environment)
                try {
                    wsClient.execute(uri, headers, handler).awaitFirstOrNull()
                } finally {
                    resubscribe.cancel()
                }
                log.warn("Saxo portfolio stream closed; reconnecting")
            } catch (e: Exception) {
                log.warn("Saxo portfolio stream error: {}", e.message)
            }
            delay(2_000)
        }
    }

    private fun onBytes(acc: Accumulator, frame: ByteArray) {
        acc.append(frame)
        val result = StreamingMessageParser.parse(acc.bytes())
        acc.consume(result.consumed)
        result.messages.forEach(::handleMessage)
    }

    private fun handleMessage(msg: StreamingMessage) {
        when (msg.referenceId) {
            "_heartbeat" -> Unit
            "_disconnect" -> log.warn("Saxo sent _disconnect; portfolio socket will reconnect")
            "_resetsubscriptions" -> scope.launch {
                balanceRef = null
                netPosRef = null
                state.clear()
                runCatching { ensureSubscriptions() }
            }

            balanceRef -> {
                if (msg.payloadFormat != 0) return // only JSON handled
                _balances.tryEmit(state.applyBalanceDelta(msg.payload))
            }

            netPosRef -> {
                if (msg.payloadFormat != 0) return
                val data = objectMapper.readTree(msg.payload).get("Data") ?: return
                _positions.tryEmit(state.applyPositionsDelta(data))
            }

            else -> Unit
        }
    }

    /** Reassembles Saxo messages that may be split across, or concatenated within, WebSocket frames. */
    private class Accumulator {
        private var buf = ByteArray(0)
        fun append(bytes: ByteArray) {
            buf += bytes
        }

        fun bytes(): ByteArray = buf
        fun consume(n: Int) {
            if (n > 0) buf = buf.copyOfRange(n, buf.size)
        }

        fun reset() {
            buf = ByteArray(0)
        }
    }
}
