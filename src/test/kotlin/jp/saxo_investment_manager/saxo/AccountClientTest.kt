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

    @Test
    fun `parses net position trade costs`() = runBlocking {
        server.enqueue(
            MockResponse().addHeader("Content-Type", "application/json").setBody(
                """
                {"Data":[
                  {"NetPositionId":"211__Stock",
                   "NetPositionBase":{"Uic":211,"AssetType":"Stock","Amount":100},
                   "NetPositionView":{"ProfitLossOnTrade":2500.0,"TradeCostsTotal":-11.35,
                     "TradeCostsTotalInBaseCurrency":-10.02}}
                ]}
                """.trimIndent(),
            ),
        )

        val pos = client.netPositions().first()

        assertEquals(-11.35, pos.view?.tradeCostsTotal)
        assertEquals(-10.02, pos.view?.tradeCostsTotalInBaseCurrency)
    }

    @Test
    fun `parses closed positions with field groups`() = runBlocking {
        // The closedpositions/me endpoint returns a bare array, not the {"Data":[...]} envelope.
        server.enqueue(
            MockResponse().addHeader("Content-Type", "application/json").setBody(
                """
                [
                  {"ClosedPosition":{
                     "AccountId":"9226397","Amount":100000,"AssetType":"FxSpot","BuyOrSell":"Buy",
                     "ClosedProfitLoss":29,"ClosedProfitLossInBaseCurrency":25.6447,
                     "ClosingPrice":1.13025,"OpenPrice":1.13054,
                     "CostOpening":-5.65,"CostOpeningInBaseCurrency":-5.0,
                     "CostClosing":-5.65,"CostClosingInBaseCurrency":-5.0,
                     "ProfitLossCurrencyConversion":-1.2,
                     "ExecutionTimeOpen":"2019-03-05T22:39:43Z","ExecutionTimeClose":"2019-03-05T22:57:51Z","Uic":21},
                   "ClosedPositionUniqueId":"212702696-212702772","NetPositionId":"EURUSD__FxSpot",
                   "DisplayAndFormat":{"Currency":"USD","Description":"Euro/US Dollar","Symbol":"EURUSD"}}
                ]
                """.trimIndent(),
            ),
        )

        val result = client.closedPositions()

        assertEquals(1, result.size)
        val cp = result.first()
        assertEquals("212702696-212702772", cp.closedPositionUniqueId)
        assertEquals("EURUSD", cp.displayAndFormat?.symbol)
        assertEquals(-5.0, cp.closed?.costOpeningInBaseCurrency)
        assertEquals(-1.2, cp.closed?.profitLossCurrencyConversion)
        assertEquals(25.6447, cp.closed?.closedProfitLossInBaseCurrency)

        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/port/v1/closedpositions/me"))
        assertTrue(
            request.path!!.contains("FieldGroups=ClosedPosition,DisplayAndFormat")
                    || request.path!!.contains("FieldGroups=ClosedPosition%2CDisplayAndFormat")
        )
    }

    @Test
    fun `parses account performance and encodes the client key in the path`() = runBlocking {
        server.enqueue(
            MockResponse().addHeader("Content-Type", "application/json").setBody(
                """
                {"BalancePerformance":{"AccountValueTimeSeries":[
                   {"Date":"2026-01-01","Value":100000.0},
                   {"Date":"2026-07-15","Value":117500.0}
                 ]},
                 "TimeWeightedPerformance":{"AccumulatedTimeWeightedTimeSeries":[
                   {"Date":"2026-01-01","Value":0.0},
                   {"Date":"2026-07-15","Value":0.175}
                 ]}}
                """.trimIndent(),
            ),
        )

        val result = client.performance("Cl-ent=Key==", "Year")

        assertEquals(2, result.balance?.accountValue?.size)
        assertEquals(100000.0, result.balance?.accountValue?.first()?.value)
        assertEquals(0.175, result.timeWeighted?.accumulated?.last()?.value)

        val request = server.takeRequest()
        // The ClientKey is a path segment, so its reserved characters must be percent-encoded.
        assertTrue(request.path!!.startsWith("/hist/v3/perf/Cl-ent%3DKey%3D%3D"))
        assertTrue(request.path!!.contains("StandardPeriod=Year"))
        assertTrue(
            request.path!!.contains("FieldGroups=BalancePerformance,TimeWeightedPerformance")
                    || request.path!!.contains("FieldGroups=BalancePerformance%2CTimeWeightedPerformance")
        )
    }
}
