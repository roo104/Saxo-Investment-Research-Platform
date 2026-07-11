package jp.saxo_investment_manager.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jp.saxo_investment_manager.service.PortfolioService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/portfolio")
@Tag(
    name = "Signals",
    description = "Technical trade signals computed in-house from Saxo OHLC candles (Saxo's API has no signals endpoint)"
)
class SignalsController(private val portfolioService: PortfolioService) {

    @GetMapping("/{id}/signals")
    @Operation(
        summary = "Get computed technical signals for a portfolio item",
        description = "Derives SMA/EMA/RSI/MACD/Bollinger signals from OHLC candles. Horizon is the " +
                "candle size in minutes (default 1440 daily); count is clamped to 1..1200 and " +
                "defaults to 250 so the 200-period average has enough warm-up.",
    )
    suspend fun signals(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "1440") horizon: Int,
        @RequestParam(defaultValue = "250") count: Int,
    ): Signals = portfolioService.signals(id, horizon, count.coerceIn(1, 1200))
}
