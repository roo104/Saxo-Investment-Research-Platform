package jp.saxo_investment_manager.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jp.saxo_investment_manager.service.WatchlistService
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
@RequestMapping("/api/watchlist")
@Tag(name = "Watchlist", description = "Manage a persisted watchlist of instruments with live quotes")
class WatchlistController(private val watchlistService: WatchlistService) {

    @GetMapping
    @Operation(summary = "List watchlist entries with their latest quotes")
    suspend fun list(): List<WatchlistEntryDto> = watchlistService.list()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an instrument to the watchlist")
    suspend fun add(@Valid @RequestBody request: AddWatchlistRequest): WatchlistEntryDto =
        watchlistService.add(request.uic!!, request.assetType!!)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an instrument from the watchlist")
    suspend fun remove(@PathVariable id: Long) = watchlistService.remove(id)

    @GetMapping("/{id}/history")
    @Operation(
        summary = "Get historical OHLC price candles for a watchlist item",
        description = "Horizon is the candle size in minutes (e.g. 60 hourly, 1440 daily). " +
                "Count is clamped to 1..1200.",
    )
    suspend fun history(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "1440") horizon: Int,
        @RequestParam(defaultValue = "90") count: Int,
    ): PriceHistoryDto = watchlistService.history(id, horizon, count.coerceIn(1, 1200))
}
