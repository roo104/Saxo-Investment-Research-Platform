package jp.saxo_investment_manager.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jp.saxo_investment_manager.service.WatchlistService
import jp.saxo_investment_manager.streaming.SaxoChartStream
import jp.saxo_investment_manager.streaming.SaxoPriceStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/watchlist")
@Tag(name = "Streaming", description = "Server-Sent Events of live prices and chart candles")
class StreamController(
    private val watchlistService: WatchlistService,
    private val priceStream: SaxoPriceStream,
    private val chartStream: SaxoChartStream,
) {

    /**
     * Streams live prices for the current watchlist as Server-Sent Events. Emits the latest known
     * price for each instrument immediately, then pushes updates as they arrive from Saxo.
     *
     * Instruments added after the stream opens are not included until the client reconnects.
     */
    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(summary = "Stream live watchlist prices (SSE)")
    suspend fun stream(): Flow<ServerSentEvent<PriceTick>> {
        val instruments = watchlistService.instruments()
        val uics = instruments.map { it.first }.toSet()
        instruments.forEach { (uic, assetType) -> priceStream.subscribe(uic, assetType) }

        return flow {
            // Initial render: the current snapshot for each instrument.
            instruments.forEach { (uic, assetType) ->
                priceStream.currentTick(uic, assetType)?.let { emit(it) }
            }
            // Then live updates for this client's instruments.
            emitAll(priceStream.ticks.filter { it.uic in uics })
        }.map { ServerSentEvent.builder(it).event("price").build() }
    }

    /**
     * Streams live OHLC candles for one watchlist instrument as SSE. Emits a `snapshot` event with
     * the full candle set, then `update` events as candles change (the current candle moves and new
     * candles roll in). `horizon` is the candle size in minutes; `count` is clamped to 2..1200.
     */
    @GetMapping("/{id}/chart/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(summary = "Stream live chart candles for a watchlist item (SSE)")
    suspend fun chartStream(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "60") horizon: Int,
        @RequestParam(defaultValue = "120") count: Int,
    ): Flow<ServerSentEvent<ChartUpdate>> {
        val (uic, assetType) = watchlistService.instrument(id)
        val key = chartStream.subscribe(uic, assetType, horizon, count.coerceIn(2, 1200))

        return flow {
            chartStream.currentSnapshot(key)?.let { emit(it) }
            emitAll(chartStream.updates.filter { it.key == key })
        }.map { ServerSentEvent.builder(it).event(if (it.snapshot) "snapshot" else "update").build() }
    }
}
