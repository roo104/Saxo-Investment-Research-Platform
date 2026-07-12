package jp.saxo_investment_manager.fundamentals

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import jp.saxo_investment_manager.api.FinancialStatements
import jp.saxo_investment_manager.api.Fundamentals

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CachingFundamentalsProviderTest {

    private val ttlMs = 24 * 60 * 60 * 1000L

    /** A [Clock] whose [instant] the test advances, so TTL expiry can be exercised without sleeping. */
    private class MutableClock(var now: Instant) : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }

    private fun fundamentals(symbol: String) =
        Fundamentals(symbol, symbol, "USD", available = true, emptyList(), empty(), empty())

    private fun empty() = FinancialStatements(emptyList(), emptyList())

    private fun provider(delegate: FmpFundamentalsProvider, clock: Clock) =
        CachingFundamentalsProvider(delegate, FmpProperties(cacheTtlMs = ttlMs), clock)

    @Test
    fun `serves a repeat lookup from cache without re-hitting the delegate`() = runBlocking {
        val delegate = mockk<FmpFundamentalsProvider>()
        val expected = fundamentals("AAPL")
        coEvery { delegate.fundamentals(211, "Stock", "AAPL", "Apple") } returns expected
        val caching = provider(delegate, MutableClock(Instant.EPOCH))

        val first = caching.fundamentals(211, "Stock", "AAPL", "Apple")
        val second = caching.fundamentals(211, "Stock", "AAPL", "Apple")

        assertSame(expected, first)
        assertSame(expected, second)
        coVerify(exactly = 1) { delegate.fundamentals(211, "Stock", "AAPL", "Apple") }
    }

    @Test
    fun `re-fetches once the entry is older than the TTL`() = runBlocking {
        val delegate = mockk<FmpFundamentalsProvider>()
        coEvery { delegate.fundamentals(211, "Stock", "AAPL", "Apple") } returns fundamentals("AAPL")
        val clock = MutableClock(Instant.EPOCH)
        val caching = provider(delegate, clock)

        caching.fundamentals(211, "Stock", "AAPL", "Apple")
        clock.now = Instant.EPOCH.plusMillis(ttlMs - 1)
        caching.fundamentals(211, "Stock", "AAPL", "Apple") // still fresh
        clock.now = Instant.EPOCH.plusMillis(ttlMs)
        caching.fundamentals(211, "Stock", "AAPL", "Apple") // expired

        coVerify(exactly = 2) { delegate.fundamentals(211, "Stock", "AAPL", "Apple") }
    }

    @Test
    fun `caches per instrument`() = runBlocking {
        val delegate = mockk<FmpFundamentalsProvider>()
        coEvery { delegate.fundamentals(211, "Stock", "AAPL", "Apple") } returns fundamentals("AAPL")
        coEvery { delegate.fundamentals(311, "Stock", "MSFT", "Microsoft") } returns fundamentals("MSFT")
        val caching = provider(delegate, MutableClock(Instant.EPOCH))

        caching.fundamentals(211, "Stock", "AAPL", "Apple")
        caching.fundamentals(311, "Stock", "MSFT", "Microsoft")

        coVerify(exactly = 1) { delegate.fundamentals(211, "Stock", "AAPL", "Apple") }
        coVerify(exactly = 1) { delegate.fundamentals(311, "Stock", "MSFT", "Microsoft") }
    }

    @Test
    fun `does not cache failures`() = runBlocking {
        val delegate = mockk<FmpFundamentalsProvider>()
        coEvery { delegate.fundamentals(211, "Stock", "AAPL", "Apple") }
            .throws(IllegalStateException("boom")) andThen fundamentals("AAPL")
        val caching = provider(delegate, MutableClock(Instant.EPOCH))

        assertFailsWith<IllegalStateException> { caching.fundamentals(211, "Stock", "AAPL", "Apple") }
        val recovered = caching.fundamentals(211, "Stock", "AAPL", "Apple")

        assertEquals("AAPL", recovered.symbol)
        coVerify(exactly = 2) { delegate.fundamentals(211, "Stock", "AAPL", "Apple") }
    }
}
