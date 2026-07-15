package jp.saxo_investment_manager.saxo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Data-transfer objects mapping the subset of Saxo OpenAPI responses this platform consumes.
 *
 * Saxo uses PascalCase field names, so each property is mapped explicitly with [JsonProperty].
 * Unknown properties are ignored: Saxo returns many more fields than we model, and it must be
 * safe for them to evolve without breaking deserialization.
 */

/** Generic OData-style envelope Saxo wraps collection responses in. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SaxoCollection<T>(
    @param:JsonProperty("Data") val data: List<T> = emptyList(),
)

/** A single instrument summary from `GET /ref/v1/instruments`. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class InstrumentSummary(
    @param:JsonProperty("Identifier") val uic: Long,
    @param:JsonProperty("Symbol") val symbol: String,
    @param:JsonProperty("Description") val description: String,
    @param:JsonProperty("AssetType") val assetType: String,
    @param:JsonProperty("ExchangeId") val exchangeId: String? = null,
    @param:JsonProperty("CurrencyCode") val currencyCode: String? = null,
)

/** A single info-price entry from `GET /trade/v1/infoprices/list`. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class InfoPrice(
    @param:JsonProperty("Uic") val uic: Long,
    @param:JsonProperty("AssetType") val assetType: String,
    @param:JsonProperty("Quote") val quote: Quote? = null,
    @param:JsonProperty("DisplayAndFormat") val displayAndFormat: DisplayAndFormat? = null,
    @param:JsonProperty("LastUpdated") val lastUpdated: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Quote(
    @param:JsonProperty("Bid") val bid: Double? = null,
    @param:JsonProperty("Ask") val ask: Double? = null,
    @param:JsonProperty("Mid") val mid: Double? = null,
    @param:JsonProperty("DelayedByMinutes") val delayedByMinutes: Int? = null,
    @param:JsonProperty("MarketState") val marketState: String? = null,
)

/**
 * A single OHLC candle from `GET /chart/v3/charts`.
 *
 * Instruments traded as securities (stocks, ETFs) populate the plain `Open/High/Low/Close` fields;
 * FX and other quote-driven instruments instead populate the `*Bid`/`*Ask` variants. [SaxoDtos]
 * consumers should fall back from the direct value to the bid/ask mid.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ChartSample(
    @param:JsonProperty("Time") val time: String,
    @param:JsonProperty("Open") val open: Double? = null,
    @param:JsonProperty("High") val high: Double? = null,
    @param:JsonProperty("Low") val low: Double? = null,
    @param:JsonProperty("Close") val close: Double? = null,
    @param:JsonProperty("Volume") val volume: Double? = null,
    @param:JsonProperty("OpenBid") val openBid: Double? = null,
    @param:JsonProperty("OpenAsk") val openAsk: Double? = null,
    @param:JsonProperty("HighBid") val highBid: Double? = null,
    @param:JsonProperty("HighAsk") val highAsk: Double? = null,
    @param:JsonProperty("LowBid") val lowBid: Double? = null,
    @param:JsonProperty("LowAsk") val lowAsk: Double? = null,
    @param:JsonProperty("CloseBid") val closeBid: Double? = null,
    @param:JsonProperty("CloseAsk") val closeAsk: Double? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DisplayAndFormat(
    @param:JsonProperty("Currency") val currency: String? = null,
    @param:JsonProperty("Decimals") val decimals: Int? = null,
    @param:JsonProperty("Description") val description: String? = null,
    @param:JsonProperty("Symbol") val symbol: String? = null,
)

/**
 * The authenticated client from `GET /port/v1/clients/me`.
 *
 * The streaming subscription endpoints (unlike the `/me` REST convenience) require an explicit
 * [clientKey] in their `Arguments`, so this is resolved once and threaded into each subscription.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ClientInfo(
    @param:JsonProperty("ClientKey") val clientKey: String,
    @param:JsonProperty("DefaultAccountKey") val defaultAccountKey: String? = null,
)

/** A single account from `GET /port/v1/accounts/me`. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SaxoAccount(
    @param:JsonProperty("AccountId") val accountId: String,
    @param:JsonProperty("AccountKey") val accountKey: String,
    @param:JsonProperty("Currency") val currency: String? = null,
    @param:JsonProperty("AccountType") val accountType: String? = null,
    @param:JsonProperty("Active") val active: Boolean = true,
)

/**
 * Account balance from `GET /port/v1/balances/me`.
 *
 * Note this is a bare object, not a [SaxoCollection]: the balance endpoint returns a single
 * aggregate for the logged-in user's default account context.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountBalance(
    @param:JsonProperty("Currency") val currency: String? = null,
    @param:JsonProperty("CashBalance") val cashBalance: Double? = null,
    @param:JsonProperty("TotalValue") val totalValue: Double? = null,
    @param:JsonProperty("NonMarginPositionsValue") val nonMarginPositionsValue: Double? = null,
    @param:JsonProperty("MarginAvailableForTrading") val marginAvailableForTrading: Double? = null,
    @param:JsonProperty("MarginUsedByCurrentPositions") val marginUsedByCurrentPositions: Double? = null,
    @param:JsonProperty("CostToClosePositions") val costToClosePositions: Double? = null,
    @param:JsonProperty("OpenPositionsCount") val openPositionsCount: Int? = null,
)

/** A single aggregated position from `GET /port/v1/netpositions/me`. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NetPosition(
    @param:JsonProperty("NetPositionId") val netPositionId: String,
    @param:JsonProperty("NetPositionBase") val base: NetPositionBase? = null,
    @param:JsonProperty("NetPositionView") val view: NetPositionView? = null,
    @param:JsonProperty("DisplayAndFormat") val displayAndFormat: DisplayAndFormat? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NetPositionBase(
    @param:JsonProperty("Uic") val uic: Long,
    @param:JsonProperty("AssetType") val assetType: String,
    @param:JsonProperty("Amount") val amount: Double? = null,
    @param:JsonProperty("OpeningDirection") val openingDirection: String? = null,
    @param:JsonProperty("ValueDate") val valueDate: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NetPositionView(
    @param:JsonProperty("AverageOpenPrice") val averageOpenPrice: Double? = null,
    @param:JsonProperty("CurrentPrice") val currentPrice: Double? = null,
    @param:JsonProperty("MarketValue") val marketValue: Double? = null,
    @param:JsonProperty("ProfitLossOnTrade") val profitLossOnTrade: Double? = null,
    @param:JsonProperty("ProfitLossOnTradeInBaseCurrency") val profitLossOnTradeInBaseCurrency: Double? = null,
    @param:JsonProperty("TradeCostsTotal") val tradeCostsTotal: Double? = null,
    @param:JsonProperty("TradeCostsTotalInBaseCurrency") val tradeCostsTotalInBaseCurrency: Double? = null,
    @param:JsonProperty("Exposure") val exposure: Double? = null,
    @param:JsonProperty("ExposureCurrency") val exposureCurrency: String? = null,
    @param:JsonProperty("InstrumentPriceDayPercentChange") val instrumentPriceDayPercentChange: Double? = null,
)

/**
 * A closed-position entry from `GET /port/v1/closedpositions/me`. Each entry pairs one opening fill
 * with the closing fill that realised it, so the cost and P/L figures are final (not marked-to-market).
 * The realised numbers live in the nested [ClosedPositionData]; [displayAndFormat] carries the label.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ClosedPositionEntry(
    @param:JsonProperty("ClosedPositionUniqueId") val closedPositionUniqueId: String,
    @param:JsonProperty("NetPositionId") val netPositionId: String? = null,
    @param:JsonProperty("ClosedPosition") val closed: ClosedPositionData? = null,
    @param:JsonProperty("DisplayAndFormat") val displayAndFormat: DisplayAndFormat? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClosedPositionData(
    @param:JsonProperty("Uic") val uic: Long? = null,
    @param:JsonProperty("AssetType") val assetType: String? = null,
    @param:JsonProperty("Amount") val amount: Double? = null,
    @param:JsonProperty("BuyOrSell") val buyOrSell: String? = null,
    @param:JsonProperty("OpenPrice") val openPrice: Double? = null,
    @param:JsonProperty("ClosingPrice") val closingPrice: Double? = null,
    @param:JsonProperty("ExecutionTimeOpen") val executionTimeOpen: String? = null,
    @param:JsonProperty("ExecutionTimeClose") val executionTimeClose: String? = null,
    // Saxo reports costs as negative amounts (money paid). Instrument currency and base-currency variants.
    @param:JsonProperty("CostOpening") val costOpening: Double? = null,
    @param:JsonProperty("CostOpeningInBaseCurrency") val costOpeningInBaseCurrency: Double? = null,
    @param:JsonProperty("CostClosing") val costClosing: Double? = null,
    @param:JsonProperty("CostClosingInBaseCurrency") val costClosingInBaseCurrency: Double? = null,
    @param:JsonProperty("ClosedProfitLoss") val closedProfitLoss: Double? = null,
    @param:JsonProperty("ClosedProfitLossInBaseCurrency") val closedProfitLossInBaseCurrency: Double? = null,
    // The isolated FX gain/loss between open and close; present on newer API versions, may be absent.
    @param:JsonProperty("ProfitLossCurrencyConversion") val profitLossCurrencyConversion: Double? = null,
)
