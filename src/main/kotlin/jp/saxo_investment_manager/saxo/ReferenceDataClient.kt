package jp.saxo_investment_manager.saxo

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

/**
 * Reference-data endpoints of the Saxo OpenAPI (`/ref/v1`).
 *
 * Coroutine-based: it suspends on the shared [WebClient] rather than blocking a thread.
 */
@Component
class ReferenceDataClient(private val saxoWebClient: WebClient) {

    /**
     * Searches for instruments matching the given criteria via `GET /ref/v1/instruments`.
     *
     * @param keywords free-text query, e.g. "Apple" or "Novo".
     * @param assetTypes comma-separated Saxo asset types to restrict to, e.g. "Stock,Etf".
     * @param exchangeId restrict to a single exchange, e.g. "NASDAQ".
     * @param top maximum number of results to return.
     */
    suspend fun searchInstruments(
        keywords: String?,
        assetTypes: String?,
        exchangeId: String?,
        top: Int = 20,
    ): List<InstrumentSummary> =
        saxoWebClient.get()
            .uri { builder ->
                builder.path("/ref/v1/instruments").queryParam("\$top", top)
                if (!keywords.isNullOrBlank()) builder.queryParam("Keywords", keywords)
                if (!assetTypes.isNullOrBlank()) builder.queryParam("AssetTypes", assetTypes)
                if (!exchangeId.isNullOrBlank()) builder.queryParam("ExchangeId", exchangeId)
                builder.build()
            }
            .retrieve()
            .awaitBody<SaxoCollection<InstrumentSummary>>()
            .data
}
