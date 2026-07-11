package jp.saxo_investment_manager.signals

import jp.saxo_investment_manager.api.IndicatorSeries
import jp.saxo_investment_manager.api.PricePoint
import jp.saxo_investment_manager.api.Signal
import jp.saxo_investment_manager.api.SignalDirection
import jp.saxo_investment_manager.api.SignalDirection.BEARISH
import jp.saxo_investment_manager.api.SignalDirection.BULLISH
import jp.saxo_investment_manager.api.SignalDirection.NEUTRAL
import java.util.Locale

/** The signals, net bias and chart series produced from one candle series. */
data class SignalResult(
    val netBias: SignalDirection,
    val signals: List<Signal>,
    val overlays: List<IndicatorSeries>,
    val oscillators: List<IndicatorSeries>,
)

/**
 * Turns a chronological candle series into discrete trade signals plus the indicator series used to
 * draw chart overlays and oscillators. This is our stand-in for Saxo/Autochartist "Trade Signals",
 * which are not exposed by the Saxo OpenAPI — everything here is computed from the OHLC data we
 * already fetch. Signals that lack enough candles degrade to [NEUTRAL] "Insufficient data".
 *
 * [points] must be sorted oldest-first and have a non-null close (the caller filters/sorts).
 */
object SignalEngine {

    fun evaluate(points: List<PricePoint>): SignalResult {
        val closes = points.map { it.close!! }
        val last = closes.lastIndex
        val lastClose = closes[last]

        val sma50 = Indicators.sma(closes, 50)
        val sma200 = Indicators.sma(closes, 200)
        val rsi = Indicators.rsi(closes, 14)
        val macd = Indicators.macd(closes, 12, 26, 9)
        val bollinger = Indicators.bollinger(closes, 20, 2.0)

        val signals = listOf(
            smaCross(sma50, sma200),
            priceVsSma(lastClose, sma50[last]),
            rsiSignal(rsi[last]),
            macdSignal(macd.macd, macd.signal),
            bollingerSignal(lastClose, bollinger.upper[last], bollinger.lower[last]),
        )

        val overlays = listOf(
            IndicatorSeries("SMA 50", sma50),
            IndicatorSeries("SMA 200", sma200),
            IndicatorSeries("Bollinger upper", bollinger.upper),
            IndicatorSeries("Bollinger lower", bollinger.lower),
        )
        val oscillators = listOf(
            IndicatorSeries("RSI", rsi),
            IndicatorSeries("MACD", macd.macd),
            IndicatorSeries("Signal", macd.signal),
            IndicatorSeries("Histogram", macd.histogram),
        )
        return SignalResult(netBias(signals), signals, overlays, oscillators)
    }

    private fun smaCross(fast: List<Double?>, slow: List<Double?>): Signal {
        val name = "SMA 50/200"
        val f = fast.last()
        val s = slow.last()
        if (f == null || s == null)
            return Signal(name, "Insufficient data", NEUTRAL, null, "Needs 200 candles for the long average.")
        val value = "${fmt(f)} / ${fmt(s)}"
        return when {
            crossover(fast, slow) > 0 -> Signal(
                name,
                "Golden cross",
                BULLISH,
                value,
                "SMA 50 just crossed above SMA 200."
            )

            crossover(fast, slow) < 0 -> Signal(
                name,
                "Death cross",
                BEARISH,
                value,
                "SMA 50 just crossed below SMA 200."
            )

            f > s -> Signal(name, "Uptrend", BULLISH, value, "SMA 50 is above SMA 200.")
            else -> Signal(name, "Downtrend", BEARISH, value, "SMA 50 is below SMA 200.")
        }
    }

    private fun priceVsSma(close: Double, sma: Double?): Signal {
        val name = "Price vs SMA 50"
        if (sma == null) return Signal(name, "Insufficient data", NEUTRAL, null, "Needs 50 candles.")
        return if (close >= sma)
            Signal(name, "Above average", BULLISH, fmt(close), "Price is above its 50-period average.")
        else
            Signal(name, "Below average", BEARISH, fmt(close), "Price is below its 50-period average.")
    }

    private fun rsiSignal(rsi: Double?): Signal {
        val name = "RSI (14)"
        if (rsi == null) return Signal(name, "Insufficient data", NEUTRAL, null, "Needs 15 candles.")
        val value = fmt(rsi)
        return when {
            rsi >= 70 -> Signal(name, "Overbought", BEARISH, value, "RSI above 70 — stretched to the upside.")
            rsi <= 30 -> Signal(name, "Oversold", BULLISH, value, "RSI below 30 — stretched to the downside.")
            else -> Signal(name, "Neutral", NEUTRAL, value, "RSI is between 30 and 70.")
        }
    }

    private fun macdSignal(macd: List<Double?>, signal: List<Double?>): Signal {
        val name = "MACD (12,26,9)"
        val m = macd.last()
        val s = signal.last()
        if (m == null || s == null) return Signal(name, "Insufficient data", NEUTRAL, null, "Needs 35 candles.")
        val value = fmt(m)
        return when {
            crossover(macd, signal) > 0 -> Signal(
                name,
                "Bullish crossover",
                BULLISH,
                value,
                "MACD just crossed above its signal line."
            )

            crossover(macd, signal) < 0 -> Signal(
                name,
                "Bearish crossover",
                BEARISH,
                value,
                "MACD just crossed below its signal line."
            )

            m > s -> Signal(name, "Above signal", BULLISH, value, "MACD is above its signal line.")
            else -> Signal(name, "Below signal", BEARISH, value, "MACD is below its signal line.")
        }
    }

    private fun bollingerSignal(close: Double, upper: Double?, lower: Double?): Signal {
        val name = "Bollinger (20,2)"
        if (upper == null || lower == null) return Signal(name, "Insufficient data", NEUTRAL, null, "Needs 20 candles.")
        return when {
            close > upper -> Signal(name, "Breakout", BULLISH, fmt(close), "Price closed above the upper band.")
            close < lower -> Signal(name, "Breakdown", BEARISH, fmt(close), "Price closed below the lower band.")
            else -> Signal(name, "Within bands", NEUTRAL, fmt(close), "Price is inside the Bollinger bands.")
        }
    }

    /** +1 if [a] crossed above [b] on the last bar, −1 if it crossed below, 0 otherwise. */
    private fun crossover(a: List<Double?>, b: List<Double?>): Int {
        val i = a.lastIndex
        if (i < 1) return 0
        val a1 = a[i] ?: return 0
        val b1 = b[i] ?: return 0
        val a0 = a[i - 1] ?: return 0
        val b0 = b[i - 1] ?: return 0
        return when {
            a0 <= b0 && a1 > b1 -> 1
            a0 >= b0 && a1 < b1 -> -1
            else -> 0
        }
    }

    private fun netBias(signals: List<Signal>): SignalDirection {
        val bull = signals.count { it.direction == BULLISH }
        val bear = signals.count { it.direction == BEARISH }
        return when {
            bull > bear -> BULLISH
            bear > bull -> BEARISH
            else -> NEUTRAL
        }
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.4f", v)
}
