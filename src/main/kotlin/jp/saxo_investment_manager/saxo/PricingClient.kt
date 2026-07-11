package jp.saxo_investment_manager.saxo

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

/**
 * Pricing endpoints of the Saxo OpenAPI (`/trade/v1`).
 *
 * Uses info-prices, which are indicative quotes intended for portfolios/research and do not
 * require the ability to trade the instrument.
 */
@Component
class PricingClient(private val saxoWebClient: WebClient) {

    /**
     * Fetches info-prices for a batch of instruments of a single asset type via
     * `GET /trade/v1/infoprices/list`.
     *
     * Saxo requires all UICs in one call to share the same [assetType], so callers should group
     * their instruments by asset type before calling. Returns an empty list for empty input.
     */
    suspend fun getInfoPrices(uics: List<Long>, assetType: String): List<InfoPrice> {
        if (uics.isEmpty()) return emptyList()
        return saxoWebClient.get()
            .uri { builder ->
                builder.path("/trade/v1/infoprices/list")
                    .queryParam("Uics", uics.joinToString(","))
                    .queryParam("AssetType", assetType)
                    .queryParam("FieldGroups", "Quote,DisplayAndFormat")
                    .build()
            }
            .retrieve()
            .awaitBody<SaxoCollection<InfoPrice>>()
            .data
    }
}
