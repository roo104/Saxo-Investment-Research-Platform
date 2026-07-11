package jp.saxo_investment_manager.service

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jp.saxo_investment_manager.saxo.ChartClient
import jp.saxo_investment_manager.saxo.ChartSample
import jp.saxo_investment_manager.saxo.DisplayAndFormat
import jp.saxo_investment_manager.saxo.InfoPrice
import jp.saxo_investment_manager.saxo.PricingClient
import jp.saxo_investment_manager.saxo.Quote
import jp.saxo_investment_manager.api.SignalDirection
import jp.saxo_investment_manager.fundamentals.FundamentalsProvider
import jp.saxo_investment_manager.market.MarketCalendar
import jp.saxo_investment_manager.watchlist.WatchlistItem
import jp.saxo_investment_manager.watchlist.WatchlistRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchlistServiceTest {
    private val repository = mockk<WatchlistRepository>()
    private val pricing = mockk<PricingClient>()
    private val chart = mockk<ChartClient>()
    private val fundamentals = mockk<FundamentalsProvider>()

    // Fixed at Monday 2026-07-13 14:00Z: 10:00 in New York, 16:00 in Copenhagen — both open.
    private val calendar = MarketCalendar(Clock.fixed(Instant.parse("2026-07-13T14:00:00Z"), ZoneId.of("UTC")))
    private val service = WatchlistService(repository, pricing, chart, fundamentals, calendar)

    private fun item(uic: Long, id: Long, assetType: String = "Stock") =
        WatchlistItem(uic = uic, assetType = assetType, symbol = "SYM$uic", description = "Desc $uic", id = id)

    @Test
    fun `list enriches items with quotes grouped by asset type`() = runBlocking {
        every { repository.findAll() } returns listOf(item(211, 1), item(212, 2))
        coEvery { pricing.getInfoPrices(listOf(211, 212), "Stock") } returns listOf(
            InfoPrice(
                211,
                "Stock",
                Quote(bid = 1.0, ask = 2.0, mid = 1.5, marketState = "Open"),
                DisplayAndFormat(currency = "USD")
            ),
        )

        val result = service.list()

        assertEquals(2, result.size)
        val apple = result.first { it.uic == 211L }
        assertEquals(1.5, apple.mid)
        assertEquals("USD", apple.currency)
        assertTrue(apple.priceAvailable)

        // 212 had no quote returned → still present, but marked unavailable
        val other = result.first { it.uic == 212L }
        assertFalse(other.priceAvailable)
        assertNull(other.mid)
    }

    @Test
    fun `list derives market-open from exchange hours when no quote is available`() = runBlocking {
        // No live quote (the sim NoAccess case for equities) — open/closed must come from the exchange.
        every { repository.findAll() } returns listOf(
            WatchlistItem(uic = 211, assetType = "Stock", symbol = "AAPL:xnas", description = "Apple Inc.", id = 1),
        )
        coEvery { pricing.getInfoPrices(listOf(211), "Stock") } returns emptyList()
        coEvery { chart.getChart(211, "Stock", 1440, 1) } returns emptyList()

        val entry = service.list().single()

        assertFalse(entry.priceAvailable)
        assertEquals("NASDAQ", entry.exchange)
        assertTrue(entry.marketOpen!!) // 10:00 ET on a Monday is within 09:30–16:00
    }

    @Test
    fun `treats a present-but-empty quote (NoAccess) as no price`() = runBlocking {
        every { repository.findAll() } returns listOf(item(211, 1))
        // Saxo returns a Quote object but with no prices when the account lacks market-data access.
        coEvery { pricing.getInfoPrices(listOf(211), "Stock") } returns listOf(
            InfoPrice(211, "Stock", Quote(marketState = "Open"), DisplayAndFormat(currency = "USD")),
        )

        val result = service.list()

        assertFalse(result.single().priceAvailable)
        assertNull(result.single().mid)
    }

    @Test
    fun `add is idempotent for an already-present instrument`() = runBlocking {
        every { repository.findByUicAndAssetType(211, "Stock") } returns item(211, 1)
        coEvery { pricing.getInfoPrices(listOf(211), "Stock") } returns emptyList()

        val result = service.add(211, "Stock")

        assertEquals(1L, result.id)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `add persists a new item using display metadata from the quote`() = runBlocking {
        every { repository.findByUicAndAssetType(211, "Stock") } returns null
        coEvery { pricing.getInfoPrices(listOf(211), "Stock") } returns listOf(
            InfoPrice(
                211,
                "Stock",
                Quote(mid = 9.9),
                DisplayAndFormat(symbol = "AAPL:xnas", description = "Apple Inc.", currency = "USD")
            ),
        )
        val saved = slot<WatchlistItem>()
        every { repository.save(capture(saved)) } answers { saved.captured.apply { id = 5 } }

        val result = service.add(211, "Stock")

        assertEquals("AAPL:xnas", saved.captured.symbol)
        assertEquals("Apple Inc.", saved.captured.description)
        assertEquals(5L, result.id)
        assertEquals(9.9, result.mid)
    }

    @Test
    fun `history maps FX bid-ask candles to a mid-price series`() = runBlocking {
        every { repository.findById(1) } returns Optional.of(item(21, 1, "FxSpot"))
        coEvery { pricing.getInfoPrices(listOf(21), "FxSpot") } returns emptyList()
        coEvery { chart.getChart(21, "FxSpot", 1440, 90) } returns listOf(
            ChartSample(time = "2026-07-10T00:00:00Z", closeBid = 1.0, closeAsk = 2.0, openBid = 3.0, openAsk = 5.0),
        )

        val result = service.history(1, horizonMinutes = 1440, count = 90)

        assertEquals(1, result.points.size)
        assertEquals(1.5, result.points.single().close)
        assertEquals(4.0, result.points.single().open)
        assertEquals("FxSpot", result.assetType)
    }

    @Test
    fun `history throws when the item does not exist`() {
        every { repository.findById(99) } returns Optional.empty()
        assertFailsWith<WatchlistItemNotFoundException> { runBlocking { service.history(99, 1440, 90) } }
    }

    @Test
    fun `signals computes indicators from candles for a rising series`() = runBlocking {
        every { repository.findById(1) } returns Optional.of(item(211, 1))
        val samples = (1..220).map {
            ChartSample(time = "2026-01-%03dT00:00:00Z".format(it), close = Math.pow(1.01, it.toDouble()))
        }
        coEvery { chart.getChart(211, "Stock", 1440, 250) } returns samples

        val result = service.signals(1, horizonMinutes = 1440, count = 250)

        assertTrue(result.available)
        assertEquals(SignalDirection.BULLISH, result.netBias)
        assertEquals(220, result.points.size)
        assertTrue(result.signals.isNotEmpty())
        assertEquals(samples.last().time, result.asOf)
    }

    @Test
    fun `signals is unavailable when too few candles come back`() = runBlocking {
        every { repository.findById(1) } returns Optional.of(item(211, 1))
        coEvery { chart.getChart(211, "Stock", 1440, 250) } returns listOf(
            ChartSample(time = "2026-01-01T00:00:00Z", close = 1.0),
        )

        val result = service.signals(1, horizonMinutes = 1440, count = 250)

        assertFalse(result.available)
        assertEquals(SignalDirection.NEUTRAL, result.netBias)
        assertTrue(result.signals.isEmpty())
    }

    @Test
    fun `signals throws when the item does not exist`() {
        every { repository.findById(99) } returns Optional.empty()
        assertFailsWith<WatchlistItemNotFoundException> { runBlocking { service.signals(99, 1440, 250) } }
    }

    @Test
    fun `remove throws when the item does not exist`() {
        every { repository.existsById(99) } returns false
        assertFailsWith<WatchlistItemNotFoundException> { runBlocking { service.remove(99) } }
        verify(exactly = 0) { repository.deleteById(any()) }
    }
}
