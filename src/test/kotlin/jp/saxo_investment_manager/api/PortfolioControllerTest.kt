package jp.saxo_investment_manager.api

import io.mockk.coEvery
import io.mockk.mockk
import jp.saxo_investment_manager.service.PortfolioItemNotFoundException
import jp.saxo_investment_manager.service.PortfolioService
import org.junit.jupiter.api.Test
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@WebFluxTest(PortfolioController::class)
@Import(PortfolioControllerTest.Mocks::class, ApiExceptionHandler::class)
class PortfolioControllerTest(
    @org.springframework.beans.factory.annotation.Autowired val webClient: WebTestClient,
    @org.springframework.beans.factory.annotation.Autowired val service: PortfolioService,
) {
    @TestConfiguration
    class Mocks {
        @Bean
        fun portfolioService(): PortfolioService = mockk()
    }

    @Test
    fun `returns portfolio entries`() {
        coEvery { service.list() } returns listOf(
            PortfolioEntryDto(
                id = 1, uic = 211, symbol = "AAPL:xnas", description = "Apple Inc.", assetType = "Stock",
                quantity = 10.0, openingPrice = 180.0, bid = 1.0, ask = 2.0, mid = 1.5, currency = "USD",
                marketState = "Open", delayedByMinutes = 15, priceAvailable = true,
            ),
        )

        webClient.get().uri("/api/portfolio").exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].symbol").isEqualTo("AAPL:xnas")
            .jsonPath("$[0].mid").isEqualTo(1.5)
    }

    @Test
    fun `add validates the request body`() {
        webClient.post().uri("/api/portfolio")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"assetType":""}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
    }

    @Test
    fun `remove returns 404 problem detail when item is missing`() {
        coEvery { service.remove(99) } throws PortfolioItemNotFoundException(99)

        webClient.delete().uri("/api/portfolio/99").exchange()
            .expectStatus().isNotFound
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("Portfolio item not found")
    }

    @Test
    fun `surfaces Saxo errors as problem detail preserving the upstream status`() {
        coEvery { service.list() } throws WebClientResponseException.create(
            HttpStatus.UNAUTHORIZED.value(),
            "Unauthorized",
            org.springframework.http.HttpHeaders.EMPTY,
            ByteArray(0),
            null,
        )

        webClient.get().uri("/api/portfolio").exchange()
            .expectStatus().isUnauthorized
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("Saxo API error")
    }
}
