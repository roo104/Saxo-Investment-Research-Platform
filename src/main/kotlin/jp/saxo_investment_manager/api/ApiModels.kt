package jp.saxo_investment_manager.api

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * The camelCase JSON contract exposed to the frontend. Kept deliberately separate from the
 * PascalCase Saxo DTOs so the public API can evolve independently of the upstream format.
 */

@Schema(description = "An instrument returned by a search.")
data class InstrumentDto(
    @get:Schema(description = "Saxo universal instrument code", example = "211")
    val uic: Long,
    @get:Schema(example = "AAPL:xnas")
    val symbol: String,
    @get:Schema(example = "Apple Inc.")
    val description: String,
    @get:Schema(example = "Stock")
    val assetType: String,
    @get:Schema(example = "NASDAQ")
    val exchangeId: String?,
    @get:Schema(example = "USD")
    val currencyCode: String?,
)

@Schema(description = "A watchlist entry enriched with the latest available info-price.")
data class WatchlistEntryDto(
    val id: Long,
    val uic: Long,
    val symbol: String,
    val description: String,
    val assetType: String,
    @get:Schema(description = "Best bid, if a quote is available")
    val bid: Double?,
    @get:Schema(description = "Best ask, if a quote is available")
    val ask: Double?,
    @get:Schema(description = "Mid price, if a quote is available")
    val mid: Double?,
    val currency: String?,
    @get:Schema(description = "Market state, e.g. Open or Closed", example = "Open")
    val marketState: String?,
    @get:Schema(description = "How many minutes the quote is delayed, if applicable")
    val delayedByMinutes: Int?,
    @get:Schema(description = "False when no live quote could be retrieved for this instrument")
    val priceAvailable: Boolean,
)

@Schema(description = "Request to add an instrument to the watchlist.")
data class AddWatchlistRequest(
    @field:NotNull(message = "uic is required")
    @get:Schema(description = "Saxo universal instrument code", example = "211")
    val uic: Long?,
    @field:NotBlank(message = "assetType is required")
    @get:Schema(example = "Stock")
    val assetType: String?,
)

@Schema(
    description = "A single OHLC point in a price-history series. Values are direct prices for " +
            "securities and bid/ask mid-prices for FX."
)
data class PricePoint(
    @get:Schema(description = "ISO-8601 timestamp of the candle", example = "2026-07-10T00:00:00Z")
    val time: String,
    val open: Double?,
    val high: Double?,
    val low: Double?,
    val close: Double?,
)

@Schema(description = "Historical price series for a watchlist instrument.")
data class PriceHistoryDto(
    val uic: Long,
    val symbol: String,
    val assetType: String,
    @get:Schema(description = "Candle size in minutes", example = "1440")
    val horizonMinutes: Int,
    val currency: String?,
    val points: List<PricePoint>,
)

@Schema(description = "A streamed live-price update for one instrument, pushed over SSE.")
data class PriceTick(
    val uic: Long,
    val assetType: String,
    val bid: Double?,
    val ask: Double?,
    val mid: Double?,
    val currency: String?,
    val marketState: String?,
    val lastUpdated: String?,
    val priceAvailable: Boolean,
)

@Schema(description = "A streamed chart update: a full candle set (snapshot=true) or the candles " +
    "that just changed (snapshot=false). Clients merge updates into the snapshot by point time.")
data class ChartUpdate(
    val key: String,
    val snapshot: Boolean,
    val points: List<PricePoint>,
)

@Schema(description = "The Saxo environment the backend is currently connected to.")
data class EnvironmentDto(
    @get:Schema(example = "SIMULATION")
    val environment: String,
    @get:Schema(example = "https://gateway.saxobank.com/sim/openapi")
    val restBaseUrl: String,
)
