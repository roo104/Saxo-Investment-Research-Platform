package jp.saxo_investment_manager.fundamentals

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FmpFundamentalsProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: FmpFundamentalsProvider

    private fun json(body: String) = MockResponse().addHeader("Content-Type", "application/json").setBody(body)

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                if ("/AAPL" !in path) return json("[]") // FMP only knows AAPL in this test
                return when {
                    "/quote/" in path -> json("""[{"name":"Apple Inc.","price":190.0,"pe":30.5,"eps":6.2}]""")
                    "/ratios-ttm/" in path -> json("""[{"priceToBookRatioTTM":45.0,"dividendYielTTM":0.005,"payoutRatioTTM":0.15}]""")
                    "/key-metrics-ttm/" in path -> json("""[{"enterpriseValueOverEBITDATTM":22.5}]""")
                    "/income-statement/" in path -> json(
                        """[{"date":"2024-09-28","calendarYear":"2024","period":"FY","reportedCurrency":"USD","revenue":391035000000,"ebitda":134661000000,"netIncome":93736000000,"eps":6.11}]""",
                    )

                    "/balance-sheet-statement/" in path -> json("""[{"date":"2024-09-28","totalAssets":364980000000,"totalDebt":106629000000}]""")
                    "/ratios/" in path -> json("""[{"date":"2024-09-28","priceToSalesRatio":9.5,"returnOnEquity":1.57}]""")
                    else -> json("[]")
                }
            }
        }
        server.start()
        val props = FmpProperties(apiKey = "test-key", baseUrl = server.url("/").toString().removeSuffix("/"))
        provider = FmpFundamentalsProvider(props)
    }

    @AfterEach
    fun tearDown() = server.shutdown()

    @Test
    fun `maps FMP responses into fundamentals`() = runBlocking {
        val f = provider.fundamentals(211, "Stock", "AAPL:xnas", "Apple")

        assertTrue(f.available)
        assertEquals("Apple Inc.", f.name)
        assertEquals("USD", f.currency)

        val pe = f.keyStats.first { it.label.startsWith("Price / Earnings") }
        assertEquals("30,50", pe.value)
        assertTrue(f.keyStats.any { it.label.startsWith("Enterprise Value") && it.value == "22,50" })

        assertEquals(listOf("2024"), f.perYear.periods)
        val revenue = f.perYear.sections.first().rows.first { it.label == "Revenue" }
        assertEquals("391bn USD", revenue.values.single())
    }

    @Test
    fun `maps a Saxo share-class symbol to FMP's hyphenated ticker with an exchange suffix`() = runBlocking {
        // NOVOb:xcse (Novo Nordisk B on Copenhagen) → FMP ticker NOVO-B.CO. The share class is a
        // trailing lowercase letter in Saxo but hyphenated + uppercased at FMP, and xcse → .CO.
        val requested = mutableSetOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                if ("/income-statement/" in path) requested += path.substringAfter("/income-statement/")
                    .substringBefore("?")
                if ("NOVO-B.CO" !in path) return json("[]")
                return when {
                    "/income-statement/" in path -> json(
                        """[{"date":"2023-12-31","calendarYear":"2023","period":"FY","reportedCurrency":"DKK","revenue":232261000000,"ebitda":110000000000,"netIncome":83683000000,"eps":18.6}]""",
                    )

                    else -> json("[]")
                }
            }
        }
        val f = provider.fundamentals(112, "Stock", "NOVOb:xcse", "Novo Nordisk B")

        assertTrue(f.available)
        assertEquals("DKK", f.currency)
        assertTrue(requested.contains("NOVO-B.CO"), "expected FMP to be queried for NOVO-B.CO, got $requested")
    }

    @Test
    fun `fails fast at construction when no API key is configured (no mock fallback)`() {
        assertFailsWith<IllegalArgumentException> {
            FmpFundamentalsProvider(FmpProperties(apiKey = ""))
        }
    }

    @Test
    fun `fails with 404 when FMP does not recognise the ticker`() = runBlocking {
        // The dispatcher returns "[]" for the income statement of any other ticker.
        val ex = assertFailsWith<ResponseStatusException> {
            provider.fundamentals(999, "Stock", "NOPE:xnas", "Nope")
        }
        assertEquals(404, ex.statusCode.value())
    }

    @Test
    fun `marks non-equity instruments as unavailable`() = runBlocking {
        val f = provider.fundamentals(21, "FxSpot", "EURUSD", "Euro/US Dollar")
        assertFalse(f.available)
        assertTrue(f.keyStats.isEmpty())
    }

    @Test
    fun `fails with 404 when the income endpoint returns an HTTP 404`() = runBlocking {
        // A hard HTTP error (not an empty array) on every endpoint — as FMP returns for an unknown
        // ticker. get() must swallow it to null rather than throw, so the caller emits a clean 404
        // and the sibling requests are never cancelled mid-flight.
        respondWith(MockResponse().setResponseCode(404))
        val ex = assertFailsWith<ResponseStatusException> {
            provider.fundamentals(999, "Stock", "AAPL:xnas", "Apple")
        }
        assertEquals(404, ex.statusCode.value())
    }

    @Test
    fun `stays available when a non-critical endpoint is throttled`() = runBlocking {
        // Income succeeds; the TTM-ratios endpoint is rate-limited (429). The section is simply
        // omitted — the instrument is still available and the throttled stat is absent.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    "/ratios-ttm/" in path -> MockResponse().setResponseCode(429)
                    "/income-statement/" in path -> json(
                        """[{"date":"2024-09-28","calendarYear":"2024","reportedCurrency":"USD","revenue":391035000000,"ebitda":134661000000,"netIncome":93736000000,"eps":6.11}]""",
                    )

                    else -> json("[]")
                }
            }
        }
        val f = provider.fundamentals(211, "Stock", "AAPL:xnas", "Apple")

        assertTrue(f.available)
        assertFalse(f.keyStats.any { it.label.startsWith("Price / Book") })
    }

    @Test
    fun `surfaces a loud 502 when FMP rejects the API key`() = runBlocking {
        // A wrong/revoked key is a misconfiguration, not missing data: it must not masquerade as a 404.
        respondWith(MockResponse().setResponseCode(401))
        val ex = assertFailsWith<ResponseStatusException> {
            provider.fundamentals(211, "Stock", "AAPL:xnas", "Apple")
        }
        assertEquals(502, ex.statusCode.value())
        assertTrue(ex.reason?.contains("FMP_API_KEY") == true)
    }

    @Test
    fun `surfaces a loud 502 when FMP forbids the request`() = runBlocking {
        respondWith(MockResponse().setResponseCode(403))
        val ex = assertFailsWith<ResponseStatusException> {
            provider.fundamentals(211, "Stock", "AAPL:xnas", "Apple")
        }
        assertEquals(502, ex.statusCode.value())
    }

    /** Replaces the dispatcher so every endpoint returns the same response. */
    private fun respondWith(response: MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = response
        }
    }
}
