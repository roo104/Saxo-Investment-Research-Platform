package jp.saxo_investment_manager.service

import jp.saxo_investment_manager.api.Fundamentals
import jp.saxo_investment_manager.api.IndicatorSeries
import jp.saxo_investment_manager.api.PriceHistoryDto
import jp.saxo_investment_manager.api.PricePoint
import jp.saxo_investment_manager.api.SignalDirection
import jp.saxo_investment_manager.api.Signals
import jp.saxo_investment_manager.api.PortfolioEntryDto
import jp.saxo_investment_manager.fundamentals.FundamentalsProvider
import jp.saxo_investment_manager.market.MarketCalendar
import jp.saxo_investment_manager.signals.SignalEngine
import jp.saxo_investment_manager.saxo.ChartClient
import jp.saxo_investment_manager.saxo.ChartSample
import jp.saxo_investment_manager.saxo.InfoPrice
import jp.saxo_investment_manager.saxo.PricingClient
import jp.saxo_investment_manager.portfolio.PortfolioItem
import jp.saxo_investment_manager.portfolio.PortfolioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

/**
 * Manages the persisted portfolio and enriches each entry with the latest info-price from Saxo.
 *
 * JPA is blocking, so every repository call is dispatched to [Dispatchers.IO] to keep the
 * reactive request threads free; Saxo calls suspend on the shared WebClient.
 */
@Service
class PortfolioService(
    private val repository: PortfolioRepository,
    private val pricingClient: PricingClient,
    private val chartClient: ChartClient,
    private val fundamentalsProvider: FundamentalsProvider,
    private val marketCalendar: MarketCalendar,
) {

    /** Company fundamentals for a portfolio item (live from FMP; Saxo has no fundamentals API). */
    suspend fun fundamentals(id: Long): Fundamentals {
        val item = withContext(Dispatchers.IO) { repository.findById(id) }
            .orElseThrow { PortfolioItemNotFoundException(id) }
        return fundamentalsProvider.fundamentals(item.uic, item.assetType, item.symbol, item.description)
    }

    /** The (uic, assetType) of every portfolio item — used to set up price-stream subscriptions. */
    suspend fun instruments(): List<Pair<Long, String>> =
        withContext(Dispatchers.IO) { repository.findAll().map { it.uic to it.assetType } }

    /** The (uic, assetType) of a single portfolio item, or throws if it is gone. */
    suspend fun instrument(id: Long): Pair<Long, String> =
        withContext(Dispatchers.IO) { repository.findById(id) }
            .orElseThrow { PortfolioItemNotFoundException(id) }
            .let { it.uic to it.assetType }

    /** Returns all portfolio items, each merged with its current quote (if one is available). */
    suspend fun list(): List<PortfolioEntryDto> {
        val items = withContext(Dispatchers.IO) { repository.findAll() }
        if (items.isEmpty()) return emptyList()

        // Saxo requires one info-price call per asset type; fetch each group then index by UIC.
        val pricesByUic: Map<Long, InfoPrice> = items
            .groupBy { it.assetType }
            .flatMap { (assetType, group) -> pricingClient.getInfoPrices(group.map { it.uic }, assetType) }
            .associateBy { it.uic }

        // Instruments the account cannot get a live quote for (e.g. equities in sim) still have
        // chart history, so fall back to the most recent daily close. Fetched concurrently.
        return coroutineScope {
            items.map { item ->
                async {
                    val dto = item.toDto(pricesByUic[item.uic], marketCalendar)
                    if (dto.priceAvailable) dto else dto.copy(lastClose = lastCloseFor(item))
                }
            }.awaitAll()
        }
    }

    /**
     * Adds an instrument to the portfolio, or returns the existing entry if already present.
     * The symbol/description snapshot is taken from the live quote's display metadata.
     */
    suspend fun add(uic: Long, assetType: String, quantity: Double, openingPrice: Double): PortfolioEntryDto {
        withContext(Dispatchers.IO) { repository.findByUicAndAssetType(uic, assetType) }
            ?.let { return it.toDto(priceFor(it), marketCalendar) }

        val price = pricingClient.getInfoPrices(listOf(uic), assetType).firstOrNull()
        val symbol = price?.displayAndFormat?.symbol ?: uic.toString()
        val description = price?.displayAndFormat?.description ?: symbol
        // Sector is static reference data; snapshot it once here so the allocation view never
        // spends a fundamentals call per render. Null (non-equity / unknown / non-US) is fine.
        val sector = fundamentalsProvider.sector(assetType, symbol)

        val saved = withContext(Dispatchers.IO) {
            repository.save(
                PortfolioItem(
                    uic = uic,
                    assetType = assetType,
                    symbol = symbol,
                    description = description,
                    quantity = quantity,
                    openingPrice = openingPrice,
                    sector = sector,
                ),
            )
        }
        return saved.toDto(price, marketCalendar)
    }

    /** Removes an item, or throws [PortfolioItemNotFoundException] if it does not exist. */
    suspend fun remove(id: Long) {
        withContext(Dispatchers.IO) {
            if (!repository.existsById(id)) throw PortfolioItemNotFoundException(id)
            repository.deleteById(id)
        }
    }

    /**
     * Fetches historical OHLC candles for a portfolio item, mapping FX bid/ask candles to a single
     * mid-price series so the frontend can render one line regardless of asset type.
     */
    suspend fun history(id: Long, horizonMinutes: Int, count: Int): PriceHistoryDto {
        val item = withContext(Dispatchers.IO) { repository.findById(id) }.orElseThrow {
            PortfolioItemNotFoundException(id)
        }
        val samples = chartClient.getChart(item.uic, item.assetType, horizonMinutes, count)
        val price = priceFor(item)
        return PriceHistoryDto(
            uic = item.uic,
            symbol = item.symbol,
            assetType = item.assetType,
            horizonMinutes = horizonMinutes,
            currency = price?.displayAndFormat?.currency,
            points = samples.map { it.toPoint() },
        )
    }

    /**
     * Computes technical trade signals for a portfolio item from its OHLC candles (Saxo exposes no
     * signals endpoint). Candles are mapped to a mid-price series, sorted oldest-first, and fed to
     * the [SignalEngine]. Fewer than two priced candles yields an unavailable result.
     *
     * We fetch [SIGNAL_WARMUP] extra candles *before* the requested window so long-lookback
     * indicators (SMA 200 in particular) are fully formed at the very first displayed candle, then
     * trim that warm-up prefix off the returned series — otherwise the SMA 200 line would only
     * appear over the final slice of the chart.
     */
    suspend fun signals(id: Long, horizonMinutes: Int, count: Int): Signals {
        val item = withContext(Dispatchers.IO) { repository.findById(id) }.orElseThrow {
            PortfolioItemNotFoundException(id)
        }
        val fetchCount = (count + SIGNAL_WARMUP).coerceAtMost(SAXO_MAX_CANDLES)
        val all = chartClient.getChart(item.uic, item.assetType, horizonMinutes, fetchCount)
            .map { it.toPoint() }
            .filter { it.close != null }
            .sortedBy { it.time }

        if (all.size < 2) {
            return Signals(
                symbol = item.symbol,
                horizonMinutes = horizonMinutes,
                available = false,
                asOf = all.lastOrNull()?.time,
                netBias = SignalDirection.NEUTRAL,
                signals = emptyList(),
                points = all,
                overlays = emptyList(),
                oscillators = emptyList(),
            )
        }

        // Evaluate over the full (warm-up + window) series, then keep only the requested window.
        // Dropping the same prefix from points and every indicator series preserves their alignment.
        val result = SignalEngine.evaluate(all)
        val drop = (all.size - count).coerceAtLeast(0)
        val points = all.drop(drop)
        fun trim(series: List<IndicatorSeries>) = series.map { IndicatorSeries(it.name, it.points.drop(drop)) }

        return Signals(
            symbol = item.symbol,
            horizonMinutes = horizonMinutes,
            available = true,
            asOf = points.last().time,
            netBias = result.netBias,
            signals = result.signals,
            points = points,
            overlays = trim(result.overlays),
            oscillators = trim(result.oscillators),
        )
    }

    private suspend fun priceFor(item: PortfolioItem): InfoPrice? =
        pricingClient.getInfoPrices(listOf(item.uic), item.assetType).firstOrNull()

    /** Most recent daily close for an instrument, or null if chart history is unavailable. */
    private suspend fun lastCloseFor(item: PortfolioItem): Double? =
        runCatching { chartClient.getChart(item.uic, item.assetType, 1440, 1) }
            .getOrDefault(emptyList())
            .lastOrNull()
            ?.toPoint()?.close
}

/** Extra candles fetched before the display window to warm up the longest indicator (SMA 200). */
private const val SIGNAL_WARMUP = 200

/** Saxo's `/chart/v3/charts` caps `Count` at 1200 candles per request. */
private const val SAXO_MAX_CANDLES = 1200

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

class PortfolioItemNotFoundException(id: Long) : RuntimeException("Portfolio item $id was not found")

private fun PortfolioItem.toDto(price: InfoPrice?, calendar: MarketCalendar) = PortfolioEntryDto(
    id = requireNotNull(id) { "Persisted portfolio item must have an id" },
    uic = uic,
    symbol = symbol,
    description = description,
    assetType = assetType,
    quantity = quantity,
    openingPrice = openingPrice,
    sector = sector,
    bid = price?.quote?.bid,
    ask = price?.quote?.ask,
    mid = price?.quote?.mid,
    currency = price?.displayAndFormat?.currency,
    marketState = price?.quote?.marketState,
    delayedByMinutes = price?.quote?.delayedByMinutes,
    // A Quote object can come back present-but-empty when the account is not entitled to an
    // instrument's market data (Saxo sets PriceType* to "NoAccess"); treat that as no price.
    priceAvailable = price?.quote?.let { it.mid != null || it.bid != null || it.ask != null } ?: false,
    exchange = calendar.exchangeName(symbol),
    country = calendar.country(symbol),
    marketOpen = calendar.isOpen(symbol, price?.quote?.marketState),
)
