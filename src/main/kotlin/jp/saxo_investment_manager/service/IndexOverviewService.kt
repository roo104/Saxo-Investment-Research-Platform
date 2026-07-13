package jp.saxo_investment_manager.service

import jp.saxo_investment_manager.api.IndexSeriesDto
import jp.saxo_investment_manager.market.IndexCatalog
import jp.saxo_investment_manager.market.MarketCalendar
import jp.saxo_investment_manager.market.MarketIndex
import jp.saxo_investment_manager.saxo.ChartClient
import jp.saxo_investment_manager.saxo.ReferenceDataClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Builds the 24-hour, region-grouped market-index overview.
 *
 * Saxo has no index catalogue, so each [MarketIndex] is resolved to a `(uic, assetType)` once via
 * instrument search and cached — resolution is the one uncertain step, since not every index is
 * available (or chartable) in every Saxo environment, so unresolved entries are logged and skipped
 * rather than failing the whole response. Intraday candles come from `/chart/v3/charts`, which
 * returns data even for instruments whose live quotes are `NoAccess` in simulation (the same reason
 * the portfolio view falls back to chart closes), so this works without live streaming entitlements.
 */
@Service
class IndexOverviewService(
    private val referenceDataClient: ReferenceDataClient,
    private val chartClient: ChartClient,
    private val marketCalendar: MarketCalendar,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** A catalog entry that has been matched to a concrete Saxo instrument. */
    private data class ResolvedIndex(
        val index: MarketIndex,
        val uic: Long,
        val assetType: String,
        val currency: String?,
    )

    private val resolveMutex = Mutex()

    @Volatile
    private var resolved: List<ResolvedIndex>? = null

    /**
     * Each resolvable index with its most recent ~24h of intraday closes and current open/closed
     * state, fetched concurrently. Indices that can't be resolved or return no candles are omitted.
     */
    suspend fun overview(): List<IndexSeriesDto> {
        val catalog = resolvedCatalog()
        return coroutineScope {
            catalog.map { entry ->
                async {
                    val points = runCatching {
                        chartClient.getChart(entry.uic, entry.assetType, CANDLE_MINUTES, CANDLE_COUNT)
                    }.getOrElse {
                        log.warn(
                            "Chart fetch failed for index {} (uic={}): {}",
                            entry.index.name,
                            entry.uic,
                            it.message
                        )
                        emptyList()
                    }.map { it.toPoint() }

                    IndexSeriesDto(
                        key = entry.index.key,
                        name = entry.index.name,
                        region = entry.index.region.label,
                        currency = entry.currency,
                        // No live MarketState for indices in sim, so this derives from session hours.
                        marketOpen = marketCalendar.isOpen(":${entry.index.sessionMic}", null) ?: false,
                        points = points,
                    )
                }
            }.awaitAll()
        }.filter { it.points.isNotEmpty() }
    }

    /** Resolves the whole catalog once; only caches when at least one index resolved, so a transient
     *  failure to reach Saxo is retried on the next request rather than caching an empty result. */
    private suspend fun resolvedCatalog(): List<ResolvedIndex> {
        resolved?.let { return it }
        return resolveMutex.withLock {
            resolved ?: resolveAll().also { if (it.isNotEmpty()) resolved = it }
        }
    }

    private suspend fun resolveAll(): List<ResolvedIndex> =
        IndexCatalog.indices.mapNotNull { resolveOne(it) }

    private suspend fun resolveOne(index: MarketIndex): ResolvedIndex? {
        // Index CFDs can be flagged non-tradable in simulation (e.g. Japan 225) yet still carry
        // reference data and chart history, so include them; match on the exact Saxo symbol.
        val matches = runCatching {
            referenceDataClient.searchInstruments(index.symbol, "CfdOnIndex", null, top = 10, includeNonTradable = true)
        }.getOrElse {
            log.warn("Instrument search failed for index {} ({}): {}", index.name, index.symbol, it.message)
            return null
        }
        val match = matches.firstOrNull { it.symbol.equals(index.symbol, ignoreCase = true) } ?: matches.firstOrNull()
        if (match == null) {
            log.warn("No Saxo instrument matched index {} (symbol='{}')", index.name, index.symbol)
            return null
        }
        log.info(
            "Resolved index {} -> uic={} assetType={} symbol={}",
            index.name,
            match.uic,
            match.assetType,
            match.symbol
        )
        return ResolvedIndex(index, match.uic, match.assetType, match.currencyCode)
    }
}

/** 15-minute candles × 96 ≈ the last 24 hours of trading. */
private const val CANDLE_MINUTES = 15
private const val CANDLE_COUNT = 96
