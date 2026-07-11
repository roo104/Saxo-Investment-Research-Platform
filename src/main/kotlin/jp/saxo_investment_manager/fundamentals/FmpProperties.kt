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
)
