package jp.saxo_investment_manager.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jp.saxo_investment_manager.service.AccountService
import jp.saxo_investment_manager.streaming.SaxoPortfolioStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/account")
@Tag(name = "Account", description = "Read-only Saxo account state: accounts, balance and open positions")
class AccountController(
    private val accountService: AccountService,
    private val portfolioStream: SaxoPortfolioStream,
) {

    @GetMapping
    @Operation(summary = "Get the account overview (accounts + aggregate balance)")
    suspend fun overview(): AccountOverviewDto = accountService.overview()

    @GetMapping("/positions")
    @Operation(summary = "List open net positions, valued with current prices")
    suspend fun positions(): List<PositionDto> = accountService.positions()

    /**
     * Streams live account P&L as Server-Sent Events: a `balance` event carrying the aggregate
     * balance and a `positions` event carrying the full valued net-position list. Emits the latest
     * known snapshot of each immediately, then pushes updates as they arrive from Saxo.
     */
    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(summary = "Stream live account balance and positions (SSE)")
    suspend fun stream(): Flow<ServerSentEvent<Any>> {
        portfolioStream.start()

        val balances = flow {
            portfolioStream.currentBalance()?.let { emit(it) }
            emitAll(portfolioStream.balances)
        }.map { ServerSentEvent.builder<Any>(it).event("balance").build() }

        val positions = flow {
            emit(portfolioStream.currentPositions())
            emitAll(portfolioStream.positions)
        }.map { ServerSentEvent.builder<Any>(it).event("positions").build() }

        return merge(balances, positions)
    }
}
