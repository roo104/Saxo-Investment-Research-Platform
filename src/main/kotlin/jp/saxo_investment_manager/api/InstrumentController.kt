package jp.saxo_investment_manager.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jp.saxo_investment_manager.service.InstrumentService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/instruments")
@Tag(name = "Instruments", description = "Search the Saxo instrument universe")
class InstrumentController(private val instrumentService: InstrumentService) {

    @GetMapping
    @Operation(
        summary = "Search instruments",
        description = "Free-text search against Saxo reference data, optionally filtered by asset type and exchange.",
    )
    suspend fun search(
        @Parameter(description = "Free-text query, e.g. 'Apple'")
        @RequestParam(required = false) keywords: String?,
        @Parameter(description = "Comma-separated Saxo asset types, e.g. 'Stock,ETF'")
        @RequestParam(required = false) assetTypes: String?,
        @Parameter(description = "Restrict to a single exchange, e.g. 'NASDAQ'")
        @RequestParam(required = false) exchangeId: String?,
    ): List<InstrumentDto> = instrumentService.search(keywords, assetTypes, exchangeId)
}
