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

class ReferenceDataClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ReferenceDataClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val webClient = WebClient.builder().baseUrl(server.url("/").toString().removeSuffix("/")).build()
        client = ReferenceDataClient(webClient)
    }

    @AfterEach
    fun tearDown() = server.shutdown()

    @Test
    fun `parses instrument summaries and forwards search parameters`() = runBlocking {
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"Data":[
                      {"Identifier":211,"Symbol":"AAPL:xnas","Description":"Apple Inc.","AssetType":"Stock","ExchangeId":"NASDAQ","CurrencyCode":"USD"}
                    ]}
                    """.trimIndent(),
                ),
        )

        val result = client.searchInstruments(keywords = "Apple", assetTypes = "Stock", exchangeId = null)

        assertEquals(1, result.size)
        assertEquals(InstrumentSummary(211, "AAPL:xnas", "Apple Inc.", "Stock", "NASDAQ", "USD"), result.first())

        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/ref/v1/instruments"))
        assertTrue(request.path!!.contains("Keywords=Apple"))
        assertTrue(request.path!!.contains("AssetTypes=Stock"))
    }
}
