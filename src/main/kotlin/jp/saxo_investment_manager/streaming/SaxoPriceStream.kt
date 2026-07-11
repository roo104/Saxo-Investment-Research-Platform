package jp.saxo_investment_manager.streaming

import jp.saxo_investment_manager.api.PriceTick
import jp.saxo_investment_manager.config.SaxoProperties
import jp.saxo_investment_manager.config.SaxoTokenProvider
import jp.saxo_investment_manager.market.MarketCalendar
import jp.saxo_investment_manager.saxo.InfoPrice
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Maintains a single Saxo streaming WebSocket connection and turns it into a hot [ticks] flow of
 * live [PriceTick]s.
 *
 * Design: one connection (one `contextId`) for the whole app; one price subscription per distinct
 * instrument, reference-counted by `uic|assetType`. The REST call that creates a subscription
 * returns the initial snapshot; subsequent partial updates arrive over the WebSocket keyed by the
 * subscription's reference id and are deep-merged into the retained snapshot before a tick is
 * emitted. Consumers (the SSE endpoint) filter [ticks] to the instruments they care about.
 */
@Service
class SaxoPriceStream(
    private val saxoWebClient: WebClient,
    private val tokenProvider: SaxoTokenProvider,
    private val properties: SaxoProperties,
    private val objectMapper: ObjectMapper,
    private val marketCalendar: MarketCalendar,
) : DisposableBean {
    private val log = LoggerFactory.getLogger(javaClass)
    private val contextId = "sim" + UUID.randomUUID().toString().replace("-", "").take(40)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wsClient = ReactorNettyWebSocketClient()

    private val _ticks =
        MutableSharedFlow<PriceTick>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val ticks: SharedFlow<PriceTick> = _ticks

    private class Sub(val referenceId: String, val uic: Long, val assetType: String, var state: ObjectNode)

    private val subsByKey = ConcurrentHashMap<String, Sub>()
    private val subsByRef = ConcurrentHashMap<String, Sub>()
    private val desired = ConcurrentHashMap<String, Pair<Long, String>>()

    private val connectMutex = Mutex()
    @Volatile
    private var connectionStarted = false

    private fun key(uic: Long, assetType: String) = "$uic|$assetType"

    /** Cancels the connection loop so a Spring/devtools restart doesn't leak a zombie stream. */
    override fun destroy() {
        scope.cancel()
        log.info("Saxo price stream scope cancelled")
    }

    /** Ensures the connection is up and an active subscription exists for the instrument. */
    suspend fun subscribe(uic: Long, assetType: String) {
        desired[key(uic, assetType)] = uic to assetType
        ensureConnection()
        createSubscription(uic, assetType)
    }

    /** The most recent tick for an instrument (from the retained snapshot), if subscribed. */
    fun currentTick(uic: Long, assetType: String): PriceTick? =
        subsByKey[key(uic, assetType)]?.let { buildTick(it) }

    private suspend fun ensureConnection() {
        if (connectionStarted) return
        connectMutex.withLock {
            if (connectionStarted) return
            connectionStarted = true
            scope.launch { connectionLoop() }
        }
    }

    private suspend fun createSubscription(uic: Long, assetType: String) {
        if (subsByKey.containsKey(key(uic, assetType))) return
        val referenceId = "px" + UUID.randomUUID().toString().replace("-", "").take(20)
        val body = mapOf(
            "ContextId" to contextId,
            "ReferenceId" to referenceId,
            "Arguments" to mapOf(
                "Uic" to uic,
                "AssetType" to assetType,
                "FieldGroups" to listOf("Quote", "DisplayAndFormat")
            ),
            "RefreshRate" to 1000,
        )
        val response =
            saxoWebClient.post().uri("/trade/v1/prices/subscriptions").bodyValue(body).retrieve().awaitBody<JsonNode>()
        val snapshot = response.get("Snapshot") as? ObjectNode ?: objectMapper.createObjectNode()
        val sub = Sub(referenceId, uic, assetType, snapshot)
        subsByKey[key(uic, assetType)] = sub
        subsByRef[referenceId] = sub
        _ticks.tryEmit(buildTick(sub))
        log.debug("Subscribed price stream for {} {} as {}", uic, assetType, referenceId)
    }

    private suspend fun connectionLoop() {
        val acc = Accumulator()
        while (scope.isActive) {
            subsByKey.clear()
            subsByRef.clear()
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
                // Re-create desired subscriptions shortly after the socket opens.
                val resubscribe = scope.launch {
                    delay(500)
                    desired.values.forEach { (uic, at) -> runCatching { createSubscription(uic, at) } }
                }
                log.info("Connecting Saxo price stream ({})", properties.environment)
                try {
                    wsClient.execute(uri, headers, handler).awaitFirstOrNull()
                } finally {
                    resubscribe.cancel()
                }
                log.warn("Saxo price stream closed; reconnecting")
            } catch (e: Exception) {
                log.warn("Saxo price stream error: {}", e.message)
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
            "_disconnect" -> log.warn("Saxo sent _disconnect; socket will reconnect")
            "_resetsubscriptions" -> scope.launch {
                subsByKey.clear(); subsByRef.clear()
                desired.values.forEach { (uic, at) -> runCatching { createSubscription(uic, at) } }
            }

            else -> {
                val sub = subsByRef[msg.referenceId] ?: return
                if (msg.payloadFormat != 0) return // only JSON handled
                sub.state = objectMapper.readerForUpdating(sub.state).readValue(msg.payload)
                _ticks.tryEmit(buildTick(sub))
            }
        }
    }

    private fun buildTick(sub: Sub): PriceTick {
        val info = objectMapper.treeToValue(sub.state, InfoPrice::class.java)
        val q = info.quote
        val symbol = info.displayAndFormat?.symbol ?: ""
        return PriceTick(
            uic = sub.uic,
            assetType = sub.assetType,
            bid = q?.bid,
            ask = q?.ask,
            mid = q?.mid,
            currency = info.displayAndFormat?.currency,
            marketState = q?.marketState,
            lastUpdated = info.lastUpdated,
            priceAvailable = q?.let { it.mid != null || it.bid != null || it.ask != null } ?: false,
            exchange = marketCalendar.exchangeName(symbol),
            marketOpen = marketCalendar.isOpen(symbol, q?.marketState),
        )
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
