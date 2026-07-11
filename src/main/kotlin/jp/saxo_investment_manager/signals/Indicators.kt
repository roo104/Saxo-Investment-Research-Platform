package jp.saxo_investment_manager.signals

import kotlin.math.sqrt

/**
 * Pure technical-indicator math over a close-price series.
 *
 * Every function returns a series the same length as its input, with leading `null`s for the
 * warm-up window where the indicator is not yet defined. No Spring, no I/O — this is the unit-test
 * target and the raw material the [SignalEngine] turns into signals and chart overlays.
 */
object Indicators {

    /** Simple moving average over [period] closes. */
    fun sma(values: List<Double>, period: Int): List<Double?> {
        require(period > 0) { "period must be positive" }
        val out = arrayOfNulls<Double>(values.size)
        var sum = 0.0
        for (i in values.indices) {
            sum += values[i]
            if (i >= period) sum -= values[i - period]
            if (i >= period - 1) out[i] = sum / period
        }
        return out.toList()
    }

    /** Exponential moving average, seeded with the simple average of the first [period] values. */
    fun ema(values: List<Double>, period: Int): List<Double?> {
        require(period > 0) { "period must be positive" }
        val out = arrayOfNulls<Double>(values.size)
        if (values.size < period) return out.toList()
        val k = 2.0 / (period + 1)
        var prev = values.take(period).average()
        out[period - 1] = prev
        for (i in period until values.size) {
            prev = values[i] * k + prev * (1 - k)
            out[i] = prev
        }
        return out.toList()
    }

    /** Wilder's Relative Strength Index over [period] closes, on a 0..100 scale. */
    fun rsi(closes: List<Double>, period: Int = 14): List<Double?> {
        require(period > 0) { "period must be positive" }
        val out = arrayOfNulls<Double>(closes.size)
        if (closes.size <= period) return out.toList()
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val change = closes[i] - closes[i - 1]
            if (change >= 0) gain += change else loss -= change
        }
        var avgGain = gain / period
        var avgLoss = loss / period
        out[period] = rsiFrom(avgGain, avgLoss)
        for (i in period + 1 until closes.size) {
            val change = closes[i] - closes[i - 1]
            avgGain = (avgGain * (period - 1) + change.coerceAtLeast(0.0)) / period
            avgLoss = (avgLoss * (period - 1) + (-change).coerceAtLeast(0.0)) / period
            out[i] = rsiFrom(avgGain, avgLoss)
        }
        return out.toList()
    }

    private fun rsiFrom(avgGain: Double, avgLoss: Double): Double =
        if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1 + avgGain / avgLoss)

    /** MACD line (EMA[fast] − EMA[slow]), its signal line (EMA[signal] of the MACD line) and histogram. */
    data class Macd(val macd: List<Double?>, val signal: List<Double?>, val histogram: List<Double?>)

    fun macd(closes: List<Double>, fast: Int = 12, slow: Int = 26, signal: Int = 9): Macd {
        val emaFast = ema(closes, fast)
        val emaSlow = ema(closes, slow)
        val macdLine = closes.indices.map { i ->
            val f = emaFast[i]
            val s = emaSlow[i]
            if (f != null && s != null) f - s else null
        }
        // The signal line is an EMA of the MACD line's defined tail, re-aligned to the full series.
        val firstDefined = macdLine.indexOfFirst { it != null }
        val signalLine = arrayOfNulls<Double>(closes.size)
        if (firstDefined >= 0) {
            val emaOfMacd = ema(macdLine.filterNotNull(), signal)
            for ((j, v) in emaOfMacd.withIndex()) if (v != null) signalLine[firstDefined + j] = v
        }
        val histogram = closes.indices.map { i ->
            val m = macdLine[i]
            val s = signalLine[i]
            if (m != null && s != null) m - s else null
        }
        return Macd(macdLine, signalLine.toList(), histogram)
    }

    /** Bollinger bands: SMA[period] as the middle band, ± [k]·σ (population std-dev) as the outer bands. */
    data class Bollinger(val middle: List<Double?>, val upper: List<Double?>, val lower: List<Double?>)

    fun bollinger(closes: List<Double>, period: Int = 20, k: Double = 2.0): Bollinger {
        val middle = sma(closes, period)
        val upper = arrayOfNulls<Double>(closes.size)
        val lower = arrayOfNulls<Double>(closes.size)
        for (i in closes.indices) {
            val m = middle[i] ?: continue
            var variance = 0.0
            for (j in i - period + 1..i) {
                val d = closes[j] - m
                variance += d * d
            }
            val sd = sqrt(variance / period)
            upper[i] = m + k * sd
            lower[i] = m - k * sd
        }
        return Bollinger(middle, upper.toList(), lower.toList())
    }
}
