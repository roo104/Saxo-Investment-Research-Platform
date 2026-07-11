package jp.saxo_investment_manager.signals

import jp.saxo_investment_manager.api.PricePoint
import jp.saxo_investment_manager.api.SignalDirection.BEARISH
import jp.saxo_investment_manager.api.SignalDirection.BULLISH
import jp.saxo_investment_manager.api.SignalDirection.NEUTRAL
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SignalEngineTest {

    private fun points(closes: List<Double>): List<PricePoint> =
        closes.mapIndexed { i, c ->
            PricePoint(
                time = "2026-01-01T00:%02dZ".format(i % 60),
                open = c,
                high = c,
                low = c,
                close = c
            )
        }

    // Accelerating trends, so the MACD line stays clearly on one side of its signal line — a linear
    // ramp flattens the MACD, and a mere reversal decelerates it, either of which would tie the vote.
    private val uptrend = (1..220).map { Math.pow(1.01, it.toDouble()) }
    private val downtrend = (1..220).map { 100.0 - Math.pow(1.01, it.toDouble()) }

    private fun signal(result: SignalResult, indicator: String) =
        result.signals.single { it.indicator == indicator }

    @Test
    fun `a sustained uptrend reads bullish with an overbought RSI`() {
        val result = SignalEngine.evaluate(points(uptrend))

        assertEquals(BULLISH, result.netBias)
        assertEquals(BULLISH, signal(result, "SMA 50/200").direction)
        assertEquals(BULLISH, signal(result, "Price vs SMA 50").direction)
        assertEquals(BULLISH, signal(result, "MACD (12,26,9)").direction)
        // A relentless rise pins RSI at overbought, which we flag as a bearish caution.
        assertEquals(BEARISH, signal(result, "RSI (14)").direction)
    }

    @Test
    fun `a sustained downtrend reads bearish with an oversold RSI`() {
        val result = SignalEngine.evaluate(points(downtrend))

        assertEquals(BEARISH, result.netBias)
        assertEquals(BEARISH, signal(result, "SMA 50/200").direction)
        assertEquals(BULLISH, signal(result, "RSI (14)").direction)
    }

    @Test
    fun `too few candles degrade every signal to neutral`() {
        val result = SignalEngine.evaluate(points(listOf(1.0, 2.0, 3.0)))

        assertEquals(NEUTRAL, result.netBias)
        assertEquals(5, result.signals.size)
        result.signals.forEach { assertEquals(NEUTRAL, it.direction, "${it.indicator} should be neutral") }
    }

    @Test
    fun `overlays and oscillators are aligned to the input length`() {
        val closes = (1..220).map { it.toDouble() }
        val result = SignalEngine.evaluate(points(closes))

        result.overlays.forEach { assertEquals(closes.size, it.points.size, it.name) }
        result.oscillators.forEach { assertEquals(closes.size, it.points.size, it.name) }
    }
}
