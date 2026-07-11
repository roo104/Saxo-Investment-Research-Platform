package jp.saxo_investment_manager.api

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Directional bias of a single signal or of the overall verdict.")
enum class SignalDirection { BULLISH, BEARISH, NEUTRAL }

@Schema(description = "One computed technical signal, e.g. an RSI reading or a moving-average cross.")
data class Signal(
    @get:Schema(description = "The indicator that produced it", example = "RSI (14)")
    val indicator: String,
    @get:Schema(description = "Short verdict", example = "Overbought")
    val label: String,
    val direction: SignalDirection,
    @get:Schema(
        description = "Raw metric value(s), if any — the client localises and formats them. " +
                "One number for most indicators, two for the SMA cross (fast / slow), empty when none.",
        example = "[72.421]",
    )
    val value: List<Double>,
    @get:Schema(description = "One-line explanation")
    val detail: String,
)

@Schema(description = "A named indicator series aligned 1:1 with the signals' price points (null = warm-up).")
data class IndicatorSeries(val name: String, val points: List<Double?>)

@Schema(
    description = "Computed trade signals for a watchlist instrument, derived from Saxo OHLC candles. " +
            "Saxo's OpenAPI has no trade-signals endpoint, so these are calculated in-house."
)
data class Signals(
    val symbol: String,
    @get:Schema(description = "Candle size in minutes used for the computation", example = "1440")
    val horizonMinutes: Int,
    @get:Schema(description = "False when there aren't enough candles to compute anything.")
    val available: Boolean,
    @get:Schema(description = "Timestamp of the latest candle", example = "2026-07-10T00:00:00Z")
    val asOf: String?,
    @get:Schema(description = "Overall bias: majority of the directional signals.")
    val netBias: SignalDirection,
    val signals: List<Signal>,
    @get:Schema(description = "OHLC points the indicators were computed over (same shape as /history).")
    val points: List<PricePoint>,
    @get:Schema(description = "Price-scale overlays (SMA/Bollinger), aligned index-for-index to points.")
    val overlays: List<IndicatorSeries>,
    @get:Schema(description = "Separate-scale oscillators (RSI/MACD), aligned index-for-index to points.")
    val oscillators: List<IndicatorSeries>,
)
