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
}
