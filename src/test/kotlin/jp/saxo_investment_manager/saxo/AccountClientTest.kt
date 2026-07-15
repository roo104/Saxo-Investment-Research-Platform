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

class AccountClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: AccountClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val webClient = WebClient.builder().baseUrl(server.url("/").toString().removeSuffix("/")).build()
        client = AccountClient(webClient)
    }

    @AfterEach
    fun tearDown() = server.shutdown()

    @Test
    fun `parses the client info`() = runBlocking {
        server.enqueue(
            MockResponse().addHeader("Content-Type", "application/json").setBody(
                """{"ClientKey":"ck==","DefaultAccountKey":"ak=="}""",
            ),
        )

        val result = client.me()

        assertEquals("ck==", result.clientKey)
        assertEquals("ak==", result.defaultAccountKey)
        assertEquals("/port/v1/clients/me", server.takeRequest().path)
    }

    @Test
    fun `parses accounts collection`() = runBlocking {
        server.enqueue(
            MockResponse().addHeader("Content-Type", "application/json").setBody(
                """
                {"Data":[
                  {"AccountId":"9226248","AccountKey":"abc==","Currency":"USD","AccountType":"Normal","Active":true}
                ]}
                """.trimIndent(),
            ),
        )

        val result = client.accounts()

        assertEquals(1, result.size)
        assertEquals("9226248", result.first().accountId)
        assertEquals("USD", result.first().currency)
        assertEquals("/port/v1/accounts/me", server.takeRequest().path)
    }

    @Test
    fun `parses the balance object`() = runBlocking {
        server.enqueue(
            MockResponse().addHeader("Content-Type", "application/json").setBody(
                """
                {"Currency":"USD","CashBalance":100000.0,"TotalValue":117500.0,
                 "NonMarginPositionsValue":17500.0,
                 "MarginAvailableForTrading":95000.0,"MarginUsedByCurrentPositions":5000.0,
                 "OpenPositionsCount":2}
                """.trimIndent(),
            ),
        )

        val result = client.balance()

        assertEquals("USD", result.currency)
        assertEquals(100000.0, result.cashBalance)
        assertEquals(117500.0, result.totalValue)
        assertEquals(2, result.openPositionsCount)
        assertEquals("/port/v1/balances/me", server.takeRequest().path)
    }

    @Test
    fun `parses net positions with field groups`() = runBlocking {
        server.enqueue(
            MockResponse().addHeader("Content-Type", "application/json").setBody(
                """
                {"Data":[
                  {"NetPositionId":"211__Stock",
                   "NetPositionBase":{"Uic":211,"AssetType":"Stock","Amount":100,"OpeningDirection":"Buy"},
                   "NetPositionView":{"AverageOpenPrice":150.0,"CurrentPrice":175.0,"MarketValue":17500.0,
                     "ProfitLossOnTrade":2500.0,"ProfitLossOnTradeInBaseCurrency":2500.0,
                     "Exposure":17500.0,"ExposureCurrency":"USD","InstrumentPriceDayPercentChange":1.2},
                   "DisplayAndFormat":{"Currency":"USD","Description":"Apple Inc.","Symbol":"AAPL:xnas"}}
                ]}
                """.trimIndent(),
            ),
        )

        val result = client.netPositions()

        assertEquals(1, result.size)
        val pos = result.first()
        assertEquals(211, pos.base?.uic)
        assertEquals(100.0, pos.base?.amount)
        assertEquals(2500.0, pos.view?.profitLossOnTrade)
        assertEquals(2500.0, pos.view?.profitLossOnTradeInBaseCurrency)
        assertEquals("AAPL:xnas", pos.displayAndFormat?.symbol)

        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/port/v1/netpositions/me"))
        assertTrue(
            request.path!!.contains("FieldGroups=NetPositionBase,NetPositionView,DisplayAndFormat")
                    || request.path!!.contains("FieldGroups=NetPositionBase%2CNetPositionView%2CDisplayAndFormat")
        )
    }
}
