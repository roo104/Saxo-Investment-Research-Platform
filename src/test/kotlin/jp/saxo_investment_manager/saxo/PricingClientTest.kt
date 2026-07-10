package jp.saxo_investment_manager.saxo

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PricingClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: PricingClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val webClient = WebClient.builder().baseUrl(server.url("/").toString().removeSuffix("/")).build()
        client = PricingClient(webClient)
    }

    @AfterEach
    fun tearDown() = server.shutdown()

    @Test
    fun `parses info prices and batches uics`() = runBlocking {
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"Data":[
                      {"Uic":211,"AssetType":"Stock",
                       "Quote":{"Bid":150.1,"Ask":150.2,"Mid":150.15,"MarketState":"Open","DelayedByMinutes":15},
                       "DisplayAndFormat":{"Currency":"USD","Description":"Apple Inc.","Symbol":"AAPL:xnas"}}
                    ]}
                    """.trimIndent(),
                ),
        )

        val result = client.getInfoPrices(uics = listOf(211, 212), assetType = "Stock")

        assertEquals(1, result.size)
        assertEquals(150.15, result.first().quote?.mid)
        assertEquals("Open", result.first().quote?.marketState)

        val request = server.takeRequest()
        assertTrue(request.path!!.contains("Uics=211,212") || request.path!!.contains("Uics=211%2C212"))
        assertTrue(request.path!!.contains("AssetType=Stock"))
    }

    @Test
    fun `returns empty without calling Saxo for empty uic list`() = runBlocking {
        val result = client.getInfoPrices(uics = emptyList(), assetType = "Stock")
        assertTrue(result.isEmpty())
        assertEquals(0, server.requestCount)
    }
}
