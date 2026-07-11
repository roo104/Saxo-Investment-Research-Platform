package jp.saxo_investment_manager.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jp.saxo_investment_manager.service.PortfolioService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/portfolio")
@Tag(
    name = "Fundamentals",
    description = "Company key stats & financials (live data from Financial Modeling Prep; Saxo has no fundamentals API)"
)
class FundamentalsController(private val portfolioService: PortfolioService) {

    @GetMapping("/{id}/fundamentals")
    @Operation(summary = "Get company fundamentals for a portfolio item")
    suspend fun fundamentals(@PathVariable id: Long): Fundamentals = portfolioService.fundamentals(id)
}
