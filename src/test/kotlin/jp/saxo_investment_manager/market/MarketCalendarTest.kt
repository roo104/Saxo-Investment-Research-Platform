package jp.saxo_investment_manager.market

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarketCalendarTest {

    private fun at(instant: String) =
        MarketCalendar(Clock.fixed(Instant.parse(instant), ZoneId.of("UTC")))

    @Test
    fun `open during regular hours on a weekday`() {
        // Monday 2026-07-13 14:00Z = 10:00 in New York.
        assertTrue(at("2026-07-13T14:00:00Z").isOpen("AAPL:xnas", null)!!)
    }

    @Test
    fun `closed before the opening bell`() {
        // Monday 2026-07-13 13:00Z = 09:00 in New York, before the 09:30 open.
        assertFalse(at("2026-07-13T13:00:00Z").isOpen("AAPL:xnas", null)!!)
    }

    @Test
    fun `closed after the closing bell`() {
        // Monday 2026-07-13 20:30Z = 16:30 in New York, after the 16:00 close.
        assertFalse(at("2026-07-13T20:30:00Z").isOpen("AAPL:xnas", null)!!)
    }

    @Test
    fun `closed on the weekend`() {
        // Saturday 2026-07-11 14:00Z.
        assertFalse(at("2026-07-11T14:00:00Z").isOpen("AAPL:xnas", null)!!)
    }

    @Test
    fun `resolves each exchange in its own timezone`() {
        // Monday 2026-07-13 15:30Z: Copenhagen 17:30 (closed, past 16:55) but New York 11:30 (open).
        val cal = at("2026-07-13T15:30:00Z")
        assertFalse(cal.isOpen("NOVOb:xcse", null)!!)
        assertTrue(cal.isOpen("AAPL:xnas", null)!!)
    }

    @Test
    fun `prefers live market state over computed hours`() {
        // Saturday (computed = closed) but Saxo says Open — the live state wins.
        assertTrue(at("2026-07-11T14:00:00Z").isOpen("AAPL:xnas", "Open")!!)
        // Weekday during hours (computed = open) but Saxo says Closed — the live state wins.
        assertFalse(at("2026-07-13T14:00:00Z").isOpen("AAPL:xnas", "Closed")!!)
    }

    @Test
    fun `unknown exchange with no market state is undetermined`() {
        assertNull(at("2026-07-13T14:00:00Z").isOpen("USDDKK", null))
        assertNull(at("2026-07-13T14:00:00Z").isOpen("FOO:xzzz", null))
    }

    @Test
    fun `exchange name resolves from the symbol suffix`() {
        val cal = at("2026-07-13T14:00:00Z")
        assertEquals("NASDAQ", cal.exchangeName("AAPL:xnas"))
        assertEquals("Nasdaq Copenhagen", cal.exchangeName("NOVOb:xcse"))
        assertNull(cal.exchangeName("USDDKK"))
    }
}
