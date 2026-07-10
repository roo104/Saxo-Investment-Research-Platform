package jp.saxo_investment_manager.saxo

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

/**
 * Chart (historical OHLC) endpoints of the Saxo OpenAPI (`/chart/v3`).
 */
@Component
class ChartClient(private val saxoWebClient: WebClient) {

    /**
     * Fetches the most recent [count] candles for an instrument via `GET /chart/v3/charts`.
     *
     * @param horizonMinutes candle size in minutes (Saxo `Horizon`), e.g. 60 (hourly) or 1440 (daily).
     * @param count number of candles to return, newest last.
     */
    suspend fun getChart(uic: Long, assetType: String, horizonMinutes: Int, count: Int): List<ChartSample> =
        saxoWebClient.get()
            .uri { builder ->
                builder.path("/chart/v3/charts")
                    .queryParam("Uic", uic)
                    .queryParam("AssetType", assetType)
                    .queryParam("Horizon", horizonMinutes)
                    .queryParam("Count", count)
                    .queryParam("FieldGroups", "Data")
                    .build()
            }
            .retrieve()
            .awaitBody<SaxoCollection<ChartSample>>()
            .data
}
