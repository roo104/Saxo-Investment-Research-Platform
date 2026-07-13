package jp.saxo_investment_manager.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jp.saxo_investment_manager.service.IndexOverviewService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/indices")
@Tag(name = "Indices", description = "24-hour intraday overview of major market indices by region")
class IndexController(private val indexOverviewService: IndexOverviewService) {

    @GetMapping
    @Operation(
        summary = "Recent 24h intraday series for the headline indices",
        description = "One series per resolvable index (Americas · Europe · Asia), each carrying raw " +
                "intraday closes and its current open/closed state. Indices unavailable in the active " +
                "Saxo environment are omitted.",
    )
    suspend fun overview(): List<IndexSeriesDto> = indexOverviewService.overview()
}
