package jp.saxo_investment_manager.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jp.saxo_investment_manager.service.PortfolioService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/portfolio")
@Tag(name = "Portfolio", description = "Manage a persisted portfolio of instruments with live quotes")
class PortfolioController(private val portfolioService: PortfolioService) {

    @GetMapping
    @Operation(summary = "List portfolio entries with their latest quotes")
    suspend fun list(): List<PortfolioEntryDto> = portfolioService.list()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an instrument to the portfolio")
    suspend fun add(@Valid @RequestBody request: AddPortfolioRequest): PortfolioEntryDto =
        portfolioService.add(request.uic!!, request.assetType!!, request.quantity!!, request.openingPrice!!)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an instrument from the portfolio")
    suspend fun remove(@PathVariable id: Long) = portfolioService.remove(id)

    @GetMapping("/{id}/history")
    @Operation(
        summary = "Get historical OHLC price candles for a portfolio item",
        description = "Horizon is the candle size in minutes (e.g. 60 hourly, 1440 daily). " +
                "Count is clamped to 1..1200.",
    )
    suspend fun history(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "1440") horizon: Int,
        @RequestParam(defaultValue = "90") count: Int,
    ): PriceHistoryDto = portfolioService.history(id, horizon, count.coerceIn(1, 1200))
}
