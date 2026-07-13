package jp.saxo_investment_manager.service

import jp.saxo_investment_manager.api.PricePoint
import jp.saxo_investment_manager.saxo.ChartSample

/** Direct value if present (securities), otherwise the bid/ask mid (FX and other quote instruments). */
private fun mid(direct: Double?, bid: Double?, ask: Double?): Double? = when {
    direct != null -> direct
    bid != null && ask != null -> (bid + ask) / 2
    else -> bid ?: ask
}

/**
 * Maps a Saxo OHLC candle to a public [PricePoint], collapsing the FX bid/ask variants to a single
 * mid-price so the frontend renders one line regardless of asset type. Shared by the portfolio
 * history/signals paths and the market-index overview.
 */
fun ChartSample.toPoint() = PricePoint(
    time = time,
    open = mid(open, openBid, openAsk),
    high = mid(high, highBid, highAsk),
    low = mid(low, lowBid, lowAsk),
    close = mid(close, closeBid, closeAsk),
)
