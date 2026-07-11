package jp.saxo_investment_manager.fundamentals

import jp.saxo_investment_manager.api.Fundamentals

/**
 * Supplies company fundamentals (key stats + financial statements) for an instrument.
 *
 * This is the seam for a fundamentals data source. Saxo's OpenAPI does **not** expose fundamentals,
 * so the implementation is [FmpFundamentalsProvider], backed by Financial Modeling Prep. Data is
 * always live or an error — the app never serves mock/sample figures. Another real feed (e.g.
 * Morningstar) can replace it without touching any calling code.
 */
interface FundamentalsProvider {
    suspend fun fundamentals(uic: Long, assetType: String, symbol: String, name: String): Fundamentals

    /**
     * The company's sector classification (e.g. "Technology"), or null when it is unavailable —
     * a non-equity, an instrument the feed doesn't recognise, or a lookup failure. This is static
     * reference data, so callers are expected to fetch it once and cache it rather than per request.
     */
    suspend fun sector(assetType: String, symbol: String): String?
}
