package jp.saxo_investment_manager.streaming

import jp.saxo_investment_manager.api.ChartUpdate
import jp.saxo_investment_manager.api.PricePoint
import jp.saxo_investment_manager.config.SaxoProperties
import jp.saxo_investment_manager.config.SaxoTokenProvider
import jp.saxo_investment_manager.saxo.ChartSample
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
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Streams live OHLC candles from Saxo (`chart/v3/charts/subscriptions`) over a single WebSocket
 * connection, turning them into a hot [updates] flow of [ChartUpdate]s.
 *
 * Unlike price updates (which are field-level deltas), chart deltas carry whole candles: the most
 * recent candle is re-sent as its high/low/close move, and a new candle appears when the horizon
 * rolls over. Each subscription keeps its candles in a time-ordered map so a delta upserts by
 * `Time`; a snapshot emits the full set, a delta emits just the candles that changed.
 */
@Service
class SaxoChartStream(
    private val saxoWebClient: WebClient,
    private val tokenProvider: SaxoTokenProvider,
    private val properties: SaxoProperties,
    private val objectMapper: ObjectMapper,
) : DisposableBean {
    private val log = LoggerFactory.getLogger(javaClass)
    private val contextId = "chart" + UUID.randomUUID().toString().replace("-", "").take(40)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wsClient = ReactorNettyWebSocketClient()

    private val _updates =
        MutableSharedFlow<ChartUpdate>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val updates: SharedFlow<ChartUpdate> = _updates

    private data class Spec(val uic: Long, val assetType: String, val horizon: Int, val count: Int)
    private class Sub(val referenceId: String, val key: String, val candles: LinkedHashMap<String, ChartSample>)

    private val subsByKey = ConcurrentHashMap<String, Sub>()
    private val subsByRef = ConcurrentHashMap<String, Sub>()
    private val desired = ConcurrentHashMap<String, Spec>()

    private val connectMutex = Mutex()
    @Volatile private var connectionStarted = false

    private fun key(uic: Long, assetType: String, horizon: Int) = "$uic|$assetType|$horizon"

    override fun destroy() {
        scope.cancel()
        log.info("Saxo chart stream scope cancelled")
    }

    /** Ensures a candle subscription exists for the instrument/horizon and returns its stream key. */
    suspend fun subscribe(uic: Long, assetType: String, horizon: Int, count: Int): String {
        val key = key(uic, assetType, horizon)
        desired[key] = Spec(uic, assetType, horizon, count)
        ensureConnection()
        createSubscription(key)
        return key
    }

    /** The current full candle set for a subscription, as a snapshot update. */
    fun currentSnapshot(key: String): ChartUpdate? =
        subsByKey[key]?.let { ChartUpdate(key, snapshot = true, points = it.candles.values.map { c -> c.toPoint() }) }

    private suspend fun ensureConnection() {
        if (connectionStarted) return
        connectMutex.withLock {
            if (connectionStarted) return
            connectionStarted = true
            scope.launch { connectionLoop() }
        }
    }

    private suspend fun createSubscription(key: String) {
        if (subsByKey.containsKey(key)) return
        val spec = desired[key] ?: return
        val referenceId = "ch" + UUID.randomUUID().toString().replace("-", "").take(20)
        val body = mapOf(
            "ContextId" to contextId,
            "ReferenceId" to referenceId,
            "Arguments" to mapOf(
                "Uic" to spec.uic,
                "AssetType" to spec.assetType,
                "Horizon" to spec.horizon,
                "Count" to spec.count,
                "FieldGroups" to listOf("Data"),
            ),
        )
        val response =
            saxoWebClient.post().uri("/chart/v3/charts/subscriptions").bodyValue(body).retrieve().awaitBody<JsonNode>()
        val candles = LinkedHashMap<String, ChartSample>()
        response.get("Snapshot")?.get("Data")?.forEach { node ->
            val c = objectMapper.treeToValue(node, ChartSample::class.java)
            candles[c.time] = c
        }
        val sub = Sub(referenceId, key, candles)
        subsByKey[key] = sub
        subsByRef[referenceId] = sub
        _updates.tryEmit(ChartUpdate(key, snapshot = true, points = candles.values.map { it.toPoint() }))
        log.debug("Subscribed chart stream for {} as {} ({} candles)", key, referenceId, candles.size)
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
                val resubscribe = scope.launch {
                    delay(500)
                    desired.keys.forEach { runCatching { createSubscription(it) } }
                }
                log.info("Connecting Saxo chart stream ({})", properties.environment)
                try {
                    wsClient.execute(uri, headers, handler).awaitFirstOrNull()
                } finally {
                    resubscribe.cancel()
                }
                log.warn("Saxo chart stream closed; reconnecting")
            } catch (e: Exception) {
                log.warn("Saxo chart stream error: {}", e.message)
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
            "_disconnect" -> log.warn("Saxo sent _disconnect; chart socket will reconnect")
            "_resetsubscriptions" -> scope.launch {
                subsByKey.clear(); subsByRef.clear()
                desired.keys.forEach { runCatching { createSubscription(it) } }
            }
            else -> {
                val sub = subsByRef[msg.referenceId] ?: return
                if (msg.payloadFormat != 0) return
                val data = objectMapper.readTree(msg.payload).get("Data") ?: return
                val changed = mutableListOf<ChartSample>()
                data.forEach { node ->
                    val c = objectMapper.treeToValue(node, ChartSample::class.java)
                    sub.candles[c.time] = c
                    changed.add(c)
                }
                if (changed.isNotEmpty()) {
                    _updates.tryEmit(ChartUpdate(sub.key, snapshot = false, points = changed.map { it.toPoint() }))
                }
            }
        }
    }

    private class Accumulator {
        private var buf = ByteArray(0)
        fun append(bytes: ByteArray) { buf += bytes }
        fun bytes(): ByteArray = buf
        fun consume(n: Int) { if (n > 0) buf = buf.copyOfRange(n, buf.size) }
        fun reset() { buf = ByteArray(0) }
    }
}

/** Direct value if present (securities), otherwise the bid/ask mid (FX and other quote instruments). */
private fun mid(direct: Double?, bid: Double?, ask: Double?): Double? = when {
    direct != null -> direct
    bid != null && ask != null -> (bid + ask) / 2
    else -> bid ?: ask
}

private fun ChartSample.toPoint() = PricePoint(
    time = time,
    open = mid(open, openBid, openAsk),
    high = mid(high, highBid, highAsk),
    low = mid(low, lowBid, lowAsk),
    close = mid(close, closeBid, closeAsk),
)
