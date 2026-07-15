package jp.saxo_investment_manager.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jp.saxo_investment_manager.service.AccountService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/account")
@Tag(name = "Account", description = "Read-only Saxo account state: accounts, balance and open positions")
class AccountController(private val accountService: AccountService) {

    @GetMapping
    @Operation(summary = "Get the account overview (accounts + aggregate balance)")
    suspend fun overview(): AccountOverviewDto = accountService.overview()

    @GetMapping("/positions")
    @Operation(summary = "List open net positions, valued with current prices")
    suspend fun positions(): List<PositionDto> = accountService.positions()
}
