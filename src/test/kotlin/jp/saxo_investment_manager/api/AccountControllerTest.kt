package jp.saxo_investment_manager.api

import io.mockk.coEvery
import io.mockk.mockk
import jp.saxo_investment_manager.service.AccountService
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
                nonMarginPositionsValue = 17500.0, unrealizedPositionsValue = 2500.0,
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
                profitLoss = 2500.0, profitLossPct = 0.1667, dayChangePct = 0.012,
            ),
        )

        webClient.get().uri("/api/account/positions").exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].symbol").isEqualTo("AAPL:xnas")
            .jsonPath("$[0].profitLoss").isEqualTo(2500.0)
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
