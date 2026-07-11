package jp.saxo_investment_manager.signals

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndicatorsTest {

    @Test
    fun `sma has a null warm-up then the trailing average`() {
        val out = Indicators.sma(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 3)
        assertEquals(listOf(null, null, 2.0, 3.0, 4.0), out)
    }

    @Test
    fun `sma is all null when there aren't enough values`() {
        assertEquals(listOf<Double?>(null, null), Indicators.sma(listOf(1.0, 2.0), 3))
    }

    @Test
    fun `ema seeds with the simple average then smooths`() {
        // period 2, k = 2/3: seed = avg(2,4)=3; 6·⅔+3·⅓=5; 8·⅔+5·⅓=7
        val out = Indicators.ema(listOf(2.0, 4.0, 6.0, 8.0), 2)
        assertNull(out[0])
        assertEquals(3.0, out[1]!!, 1e-9)
        assertEquals(5.0, out[2]!!, 1e-9)
        assertEquals(7.0, out[3]!!, 1e-9)
    }

    @Test
    fun `rsi matches a hand-computed Wilder series`() {
        // closes 10,11,10,11,12 with period 2 → 50, 75, 87.5 after the warm-up.
        val out = Indicators.rsi(listOf(10.0, 11.0, 10.0, 11.0, 12.0), 2)
        assertNull(out[0])
        assertNull(out[1])
        assertEquals(50.0, out[2]!!, 1e-9)
        assertEquals(75.0, out[3]!!, 1e-9)
        assertEquals(87.5, out[4]!!, 1e-9)
    }

    @Test
    fun `rsi is 100 for a purely rising series and 0 for a purely falling one`() {
        val rising = Indicators.rsi((1..20).map { it.toDouble() }, 14)
        assertEquals(100.0, rising.last()!!, 1e-9)
        val falling = Indicators.rsi((20 downTo 1).map { it.toDouble() }, 14)
        assertEquals(0.0, falling.last()!!, 1e-9)
    }

    @Test
    fun `macd histogram equals macd minus signal where both are defined`() {
        val closes = (1..60).map { it.toDouble() }
        val macd = Indicators.macd(closes, 12, 26, 9)
        assertEquals(closes.size, macd.macd.size)
        // MACD line is defined from the slow EMA onwards (index 25).
        assertNull(macd.macd[24])
        assertTrue(macd.macd[25] != null)
        val i = closes.lastIndex
        assertEquals(macd.macd[i]!! - macd.signal[i]!!, macd.histogram[i]!!, 1e-9)
    }

    @Test
    fun `bollinger bands collapse onto the middle for a flat series`() {
        val out = Indicators.bollinger(listOf(5.0, 5.0, 5.0, 5.0), 2, 2.0)
        assertNull(out.upper[0])
        assertEquals(5.0, out.middle[3]!!, 1e-9)
        assertEquals(5.0, out.upper[3]!!, 1e-9)
        assertEquals(5.0, out.lower[3]!!, 1e-9)
    }
}
