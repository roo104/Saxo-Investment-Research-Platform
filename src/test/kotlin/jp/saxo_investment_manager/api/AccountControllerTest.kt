package jp.saxo_investment_manager.api

import io.mockk.coEvery
import io.mockk.mockk
import jp.saxo_investment_manager.service.AccountService
import jp.saxo_investment_manager.streaming.SaxoPortfolioStream
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@WebFluxTest(AccountController::class)
@Import(AccountControllerTest.Mocks::class, ApiExceptionHandler::class)
class AccountControllerTest(
    @Autowired val webClient: WebTestClient,
    @Autowired val service: AccountService,
) {
    @TestConfiguration
    class Mocks {
        @Bean
        fun accountService(): AccountService = mockk()

        // relaxed: Spring invokes DisposableBean.destroy() on shutdown, which the tests don't stub.
        @Bean
        fun portfolioStream(): SaxoPortfolioStream = mockk(relaxed = true)
    }

    @Test
    fun `returns the account overview`() {
        coEvery { service.overview() } returns AccountOverviewDto(
            accounts = listOf(
                AccountDto(
                    accountId = "9226248",
                    currency = "USD",
                    accountType = "Normal",
                    active = true
                )
            ),
            balance = AccountBalanceDto(
                currency = "USD", cashBalance = 100000.0, totalValue = 117500.0,
                nonMarginPositionsValue = 17500.0,
                marginAvailable = 95000.0, marginUsed = 5000.0, openPositionsCount = 2,
            ),
        )

        webClient.get().uri("/api/account").exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.accounts[0].accountId").isEqualTo("9226248")
            .jsonPath("$.balance.totalValue").isEqualTo(117500.0)
    }

    @Test
    fun `returns net positions`() {
        coEvery { service.positions() } returns listOf(
            PositionDto(
                netPositionId = "211__Stock", uic = 211, symbol = "AAPL:xnas", description = "Apple Inc.",
                assetType = "Stock", currency = "USD", amount = 100.0, openingDirection = "Buy",
                averageOpenPrice = 150.0, currentPrice = 175.0, marketValue = 17500.0,
                profitLoss = 2500.0, profitLossBase = 2500.0, profitLossPct = 0.1667, dayChangePct = 0.012,
                tradeCosts = -11.35, tradeCostsBase = -10.02,
            ),
        )

        webClient.get().uri("/api/account/positions").exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].symbol").isEqualTo("AAPL:xnas")
            .jsonPath("$[0].profitLoss").isEqualTo(2500.0)
            .jsonPath("$[0].tradeCosts").isEqualTo(-11.35)
    }

    @Test
    fun `returns closed positions`() {
        coEvery { service.closedPositions() } returns listOf(
            ClosedPositionDto(
                closedPositionId = "1-2", uic = 21, symbol = "EURUSD", description = "Euro/US Dollar",
                assetType = "FxSpot", currency = "USD", amount = 100000.0, buyOrSell = "Buy",
                openPrice = 1.1, closingPrice = 1.12, openedAt = "2026-07-01T10:00:00Z",
                closedAt = "2026-07-03T14:30:00Z", openingCost = -5.0, closingCost = -4.0,
                profitLoss = 200.0, profitLossBase = 180.0, currencyConversionPl = -8.0,
            ),
        )

        webClient.get().uri("/api/account/closed-positions").exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].symbol").isEqualTo("EURUSD")
            .jsonPath("$[0].openingCost").isEqualTo(-5.0)
            .jsonPath("$[0].currencyConversionPl").isEqualTo(-8.0)
    }

    @Test
    fun `surfaces Saxo errors as problem detail preserving the upstream status`() {
        coEvery { service.overview() } throws WebClientResponseException.create(
            HttpStatus.UNAUTHORIZED.value(),
            "Unauthorized",
            org.springframework.http.HttpHeaders.EMPTY,
            ByteArray(0),
            null,
        )

        webClient.get().uri("/api/account").exchange()
            .expectStatus().isUnauthorized
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("Saxo API error")
    }
}
