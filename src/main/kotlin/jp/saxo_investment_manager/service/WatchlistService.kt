package jp.saxo_investment_manager.service

import jp.saxo_investment_manager.api.Fundamentals
import jp.saxo_investment_manager.api.PriceHistoryDto
import jp.saxo_investment_manager.api.PricePoint
import jp.saxo_investment_manager.api.WatchlistEntryDto
import jp.saxo_investment_manager.fundamentals.FundamentalsProvider
import jp.saxo_investment_manager.saxo.ChartClient
import jp.saxo_investment_manager.saxo.ChartSample
import jp.saxo_investment_manager.saxo.InfoPrice
import jp.saxo_investment_manager.saxo.PricingClient
import jp.saxo_investment_manager.watchlist.WatchlistItem
import jp.saxo_investment_manager.watchlist.WatchlistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

/**
 * Manages the persisted watchlist and enriches each entry with the latest info-price from Saxo.
 *
 * JPA is blocking, so every repository call is dispatched to [Dispatchers.IO] to keep the
 * reactive request threads free; Saxo calls suspend on the shared WebClient.
 */
@Service
class WatchlistService(
    private val repository: WatchlistRepository,
    private val pricingClient: PricingClient,
    private val chartClient: ChartClient,
    private val fundamentalsProvider: FundamentalsProvider,
) {

    /** Company fundamentals for a watchlist item (live from FMP; Saxo has no fundamentals API). */
    suspend fun fundamentals(id: Long): Fundamentals {
        val item = withContext(Dispatchers.IO) { repository.findById(id) }
            .orElseThrow { WatchlistItemNotFoundException(id) }
        return fundamentalsProvider.fundamentals(item.uic, item.assetType, item.symbol, item.description)
    }

    /** The (uic, assetType) of every watchlist item — used to set up price-stream subscriptions. */
    suspend fun instruments(): List<Pair<Long, String>> =
        withContext(Dispatchers.IO) { repository.findAll().map { it.uic to it.assetType } }

    /** The (uic, assetType) of a single watchlist item, or throws if it is gone. */
    suspend fun instrument(id: Long): Pair<Long, String> =
        withContext(Dispatchers.IO) { repository.findById(id) }
            .orElseThrow { WatchlistItemNotFoundException(id) }
            .let { it.uic to it.assetType }

    /** Returns all watchlist items, each merged with its current quote (if one is available). */
    suspend fun list(): List<WatchlistEntryDto> {
        val items = withContext(Dispatchers.IO) { repository.findAll() }
        if (items.isEmpty()) return emptyList()

        // Saxo requires one info-price call per asset type; fetch each group then index by UIC.
        val pricesByUic: Map<Long, InfoPrice> = items
            .groupBy { it.assetType }
            .flatMap { (assetType, group) -> pricingClient.getInfoPrices(group.map { it.uic }, assetType) }
            .associateBy { it.uic }

        return items.map { it.toDto(pricesByUic[it.uic]) }
    }

    /**
     * Adds an instrument to the watchlist, or returns the existing entry if already present.
     * The symbol/description snapshot is taken from the live quote's display metadata.
     */
    suspend fun add(uic: Long, assetType: String): WatchlistEntryDto {
        withContext(Dispatchers.IO) { repository.findByUicAndAssetType(uic, assetType) }
            ?.let { return it.toDto(priceFor(it)) }

        val price = pricingClient.getInfoPrices(listOf(uic), assetType).firstOrNull()
        val symbol = price?.displayAndFormat?.symbol ?: uic.toString()
        val description = price?.displayAndFormat?.description ?: symbol

        val saved = withContext(Dispatchers.IO) {
            repository.save(WatchlistItem(uic = uic, assetType = assetType, symbol = symbol, description = description))
        }
        return saved.toDto(price)
    }

    /** Removes an item, or throws [WatchlistItemNotFoundException] if it does not exist. */
    suspend fun remove(id: Long) {
        withContext(Dispatchers.IO) {
            if (!repository.existsById(id)) throw WatchlistItemNotFoundException(id)
            repository.deleteById(id)
        }
    }

    /**
     * Fetches historical OHLC candles for a watchlist item, mapping FX bid/ask candles to a single
     * mid-price series so the frontend can render one line regardless of asset type.
     */
    suspend fun history(id: Long, horizonMinutes: Int, count: Int): PriceHistoryDto {
        val item = withContext(Dispatchers.IO) { repository.findById(id) }.orElseThrow {
            WatchlistItemNotFoundException(id)
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

    private suspend fun priceFor(item: WatchlistItem): InfoPrice? =
        pricingClient.getInfoPrices(listOf(item.uic), item.assetType).firstOrNull()
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

class WatchlistItemNotFoundException(id: Long) : RuntimeException("Watchlist item $id was not found")

private fun WatchlistItem.toDto(price: InfoPrice?) = WatchlistEntryDto(
    id = requireNotNull(id) { "Persisted watchlist item must have an id" },
    uic = uic,
    symbol = symbol,
    description = description,
    assetType = assetType,
    bid = price?.quote?.bid,
    ask = price?.quote?.ask,
    mid = price?.quote?.mid,
    currency = price?.displayAndFormat?.currency,
    marketState = price?.quote?.marketState,
    delayedByMinutes = price?.quote?.delayedByMinutes,
    // A Quote object can come back present-but-empty when the account is not entitled to an
    // instrument's market data (Saxo sets PriceType* to "NoAccess"); treat that as no price.
    priceAvailable = price?.quote?.let { it.mid != null || it.bid != null || it.ask != null } ?: false,
)
