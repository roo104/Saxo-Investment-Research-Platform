package jp.saxo_investment_manager.fundamentals

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration for the Financial Modeling Prep fundamentals source.
 *
 * [apiKey] is mandatory: when it is blank the application fails to start (see
 * [FmpFundamentalsProvider]) — the app never serves mock data. Get a free key at
 * https://financialmodelingprep.com and set `FMP_API_KEY`.
 */
@ConfigurationProperties(prefix = "fundamentals.fmp")
data class FmpProperties(
    val apiKey: String = "",
    val baseUrl: String = "https://financialmodelingprep.com",
    /**
     * How long a fetched [jp.saxo_investment_manager.api.Fundamentals] is served from the in-memory
     * cache before it is re-fetched, in milliseconds. Fundamentals change at most quarterly, so a
     * 24-hour default keeps a single view (which fires nine FMP calls) from re-hitting the API on
     * every render while staying comfortably within the free tier's rate limit.
     */
    val cacheTtlMs: Long = 24 * 60 * 60 * 1000,
)
