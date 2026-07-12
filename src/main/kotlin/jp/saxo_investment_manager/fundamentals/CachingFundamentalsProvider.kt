package jp.saxo_investment_manager.fundamentals

import jp.saxo_investment_manager.api.Fundamentals
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches [Fundamentals] in memory so a repeated view of the same instrument does not re-fire the
 * nine FMP calls behind every fetch. Wraps [FmpFundamentalsProvider] and is marked [Primary] so
 * callers (via the [FundamentalsProvider] seam) transparently get caching.
 *
 * Entries live for [FmpProperties.cacheTtlMs] (24h by default). Keying is by `uic`, which uniquely
 * identifies an instrument, so the stable `assetType`/`symbol`/`name` arguments do not affect the
 * key. Only successful results are cached: when the delegate throws (unknown ticker → 404, bad key
 * → 502) nothing is stored, so the error is not memoised and the next call retries.
 *
 * [sector] is not cached here — it is static reference data that callers already snapshot into the
 * database once per holding (see `PortfolioService.add`), so it just delegates.
 */
@Primary
@Component
class CachingFundamentalsProvider(
    private val delegate: FmpFundamentalsProvider,
    private val properties: FmpProperties,
    private val clock: Clock,
) : FundamentalsProvider {

    private class Entry(val value: Fundamentals, val storedAtMs: Long)

    private val cache = ConcurrentHashMap<Long, Entry>()

    override suspend fun fundamentals(uic: Long, assetType: String, symbol: String, name: String): Fundamentals {
        val now = clock.millis()
        cache[uic]?.let { if (now - it.storedAtMs < properties.cacheTtlMs) return it.value }
        // A miss (or a concurrent miss for the same uic) fetches fresh: at worst a couple of
        // redundant fetches race on first access, which is harmless and far cheaper than a lock.
        val fresh = delegate.fundamentals(uic, assetType, symbol, name)
        cache[uic] = Entry(fresh, now)
        return fresh
    }

    override suspend fun sector(assetType: String, symbol: String): String? = delegate.sector(assetType, symbol)
}
