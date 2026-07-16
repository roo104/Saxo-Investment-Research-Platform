package jp.saxo_investment_manager.api

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

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

@Schema(description = "A portfolio entry enriched with the latest available info-price.")
data class PortfolioEntryDto(
    val id: Long,
    val uic: Long,
    val symbol: String,
    val description: String,
    val assetType: String,
    @get:Schema(description = "Units held (shares, or notional for FX)", example = "10")
    val quantity: Double?,
    @get:Schema(description = "Price paid per unit when the position was opened", example = "180.5")
    val openingPrice: Double?,
    @get:Schema(description = "Sector classification, if known (equities only)", example = "Technology")
    val sector: String?,
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
    @get:Schema(description = "Most recent daily close, used as a fallback when no live quote is available")
    val lastClose: Double? = null,
    @get:Schema(description = "Exchange the instrument trades on, if known", example = "NASDAQ")
    val exchange: String? = null,
    @get:Schema(description = "Country of the listing exchange, if known", example = "United States")
    val country: String? = null,
    @get:Schema(description = "Whether that market is currently open; null when it can't be determined")
    val marketOpen: Boolean? = null,
)

@Schema(description = "Request to add an instrument to the portfolio.")
data class AddPortfolioRequest(
    @field:NotNull(message = "uic is required")
    @get:Schema(description = "Saxo universal instrument code", example = "211")
    val uic: Long?,
    @field:NotBlank(message = "assetType is required")
    @get:Schema(example = "Stock")
    val assetType: String?,
    @field:NotNull(message = "quantity is required")
    @field:Positive(message = "quantity must be positive")
    @get:Schema(description = "Units held (shares, or notional for FX)", example = "10")
    val quantity: Double?,
    @field:NotNull(message = "openingPrice is required")
    @field:Positive(message = "openingPrice must be positive")
    @get:Schema(description = "Price paid per unit when the position was opened", example = "180.5")
    val openingPrice: Double?,
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

@Schema(description = "Historical price series for a portfolio instrument.")
data class PriceHistoryDto(
    val uic: Long,
    val symbol: String,
    val assetType: String,
    @get:Schema(description = "Candle size in minutes", example = "1440")
    val horizonMinutes: Int,
    val currency: String?,
    val points: List<PricePoint>,
)

@Schema(
    description = "One market index's recent intraday price series for the 24h markets overview. " +
            "Closes are raw index levels; the frontend rebases each series to a % change so indices " +
            "with very different levels share one scale."
)
data class IndexSeriesDto(
    @get:Schema(description = "Stable client key", example = "spx")
    val key: String,
    @get:Schema(example = "S&P 500")
    val name: String,
    @get:Schema(description = "Region grouping", example = "Americas")
    val region: String,
    val currency: String?,
    @get:Schema(description = "Whether the index's market is currently open (regular session hours)")
    val marketOpen: Boolean,
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
    @get:Schema(description = "Exchange the instrument trades on, if known", example = "NASDAQ")
    val exchange: String? = null,
    @get:Schema(description = "Whether that market is currently open; null when it can't be determined")
    val marketOpen: Boolean? = null,
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

@Schema(description = "A trading account belonging to the authenticated Saxo user.")
data class AccountDto(
    @get:Schema(example = "9226248")
    val accountId: String,
    @get:Schema(example = "USD")
    val currency: String?,
    @get:Schema(description = "Saxo account classification", example = "Normal")
    val accountType: String?,
    val active: Boolean,
)

@Schema(
    description = "Aggregate cash and value balance for the authenticated user's account context. " +
            "All amounts are in the account's base currency."
)
data class AccountBalanceDto(
    @get:Schema(example = "USD")
    val currency: String?,
    @get:Schema(description = "Settled cash on the account", example = "100000.0")
    val cashBalance: Double?,
    @get:Schema(description = "Cash plus the value of all open positions", example = "117500.0")
    val totalValue: Double?,
    @get:Schema(description = "Value of positions held outright (non-margin)", example = "17500.0")
    val nonMarginPositionsValue: Double?,
    @get:Schema(description = "Margin still available to open new positions")
    val marginAvailable: Double?,
    @get:Schema(description = "Margin currently tied up by open positions")
    val marginUsed: Double?,
    @get:Schema(description = "Number of open positions the balance reflects", example = "3")
    val openPositionsCount: Int?,
)

@Schema(description = "Account overview: the accounts on the client plus their aggregate balance.")
data class AccountOverviewDto(
    val accounts: List<AccountDto>,
    val balance: AccountBalanceDto,
)

@Schema(
    description = "A single open net position (all fills in one instrument aggregated). Prices and " +
            "values are in the position's instrument currency; percentages are raw ratios the " +
            "frontend localises."
)
data class PositionDto(
    @get:Schema(description = "Saxo net-position identifier", example = "211__Stock")
    val netPositionId: String,
    val uic: Long,
    @get:Schema(example = "AAPL:xnas")
    val symbol: String,
    @get:Schema(example = "Apple Inc.")
    val description: String,
    @get:Schema(example = "Stock")
    val assetType: String,
    val currency: String?,
    @get:Schema(description = "Units held; negative when the net position is short", example = "100")
    val amount: Double?,
    @get:Schema(description = "Long or Short", example = "Buy")
    val openingDirection: String?,
    @get:Schema(description = "Volume-weighted average price the position was opened at", example = "150.0")
    val averageOpenPrice: Double?,
    @get:Schema(description = "Latest price used to value the position", example = "175.0")
    val currentPrice: Double?,
    @get:Schema(description = "Current market value of the position", example = "17500.0")
    val marketValue: Double?,
    @get:Schema(description = "Unrealised profit/loss on the position, in the instrument currency", example = "2500.0")
    val profitLoss: Double?,
    @get:Schema(
        description = "Unrealised profit/loss on the position converted to the account's base currency; " +
                "summed across positions to give the account-level unrealised P/L",
        example = "2500.0",
    )
    val profitLossBase: Double?,
    @get:Schema(description = "Unrealised P/L as a raw ratio of cost basis, e.g. 0.1667 = +16.67%", example = "0.1667")
    val profitLossPct: Double?,
    @get:Schema(description = "Instrument price change on the day, as a raw ratio", example = "0.012")
    val dayChangePct: Double?,
    @get:Schema(
        description = "Trade costs incurred on the position so far, in the instrument currency",
        example = "-11.35"
    )
    val tradeCosts: Double?,
    @get:Schema(description = "Trade costs incurred so far, in the account base currency", example = "-10.02")
    val tradeCostsBase: Double?,
)

@Schema(
    description = "A realised (closed) position: one opening fill paired with the close that " +
            "realised it. Amounts marked 'base' are in the account currency; the rest are in the " +
            "instrument currency."
)
data class ClosedPositionDto(
    @get:Schema(description = "Stable id pairing the opening and closing fills", example = "212702698-212702774")
    val closedPositionId: String,
    val uic: Long?,
    @get:Schema(example = "AAPL:xnas")
    val symbol: String,
    @get:Schema(example = "Apple Inc.")
    val description: String,
    @get:Schema(example = "Stock")
    val assetType: String?,
    @get:Schema(description = "Instrument currency", example = "USD")
    val currency: String?,
    @get:Schema(description = "Units closed; negative when the closed position was short", example = "10")
    val amount: Double?,
    @get:Schema(description = "Buy or Sell (the opening direction)", example = "Buy")
    val buyOrSell: String?,
    @get:Schema(description = "Price the position was opened at", example = "150.0")
    val openPrice: Double?,
    @get:Schema(description = "Price the position was closed at", example = "175.0")
    val closingPrice: Double?,
    @get:Schema(description = "When the position was opened (ISO-8601)")
    val openedAt: String?,
    @get:Schema(description = "When the position was closed (ISO-8601)")
    val closedAt: String?,
    @get:Schema(description = "Opening cost in the account base currency (negative = paid)", example = "-4.63")
    val openingCost: Double?,
    @get:Schema(description = "Closing cost in the account base currency (negative = paid)", example = "-4.62")
    val closingCost: Double?,
    @get:Schema(description = "Realised P/L in the instrument currency", example = "2500.0")
    val profitLoss: Double?,
    @get:Schema(description = "Realised P/L in the account base currency", example = "2185.0")
    val profitLossBase: Double?,
    @get:Schema(
        description = "Realised profit/loss attributable to currency conversion between open and " +
                "close, in the base currency. Null when Saxo does not report it for the position.",
        example = "-12.4",
    )
    val currencyConversionPl: Double?,
)

@Schema(description = "Standard reporting period for account performance (maps to Saxo's StandardPeriod).")
enum class PerformancePeriod { Month, Quarter, Year, AllTime }

@Schema(description = "One point on the account-value curve: total account value in base currency on a date.")
data class PerformancePoint(
    @get:Schema(description = "ISO-8601 date", example = "2026-07-10")
    val date: String,
    @get:Schema(description = "Total account value in the account base currency", example = "117500.0")
    val value: Double,
)

@Schema(
    description = "Historic account performance over a period: the account-value curve plus returns " +
            "derived from it. Amounts are in the account base currency; percentages are raw ratios " +
            "the frontend localises. In simulation the series is often empty (see entitlement note)."
)
data class PerformanceDto(
    @get:Schema(description = "The reporting period this covers", example = "Year")
    val period: PerformancePeriod,
    @get:Schema(description = "False when there is no meaningful data to chart (no curve, or flat at zero)")
    val available: Boolean,
    @get:Schema(description = "Account value at the start of the period", example = "100000.0")
    val startValue: Double?,
    @get:Schema(description = "Account value at the end of the period (latest known)", example = "117500.0")
    val endValue: Double?,
    @get:Schema(description = "End minus start, in base currency", example = "17500.0")
    val absoluteReturn: Double?,
    @get:Schema(
        description = "Return over the period as a raw ratio. Saxo's accumulated time-weighted return " +
                "when available, else the simple change across the value curve.",
        example = "0.175",
    )
    val returnPct: Double?,
    @get:Schema(description = "The account-value curve, oldest point first")
    val points: List<PerformancePoint>,
)
