package jp.saxo_investment_manager.api

import io.mockk.coEvery
import io.mockk.mockk
import jp.saxo_investment_manager.service.WatchlistItemNotFoundException
import jp.saxo_investment_manager.service.WatchlistService
import org.junit.jupiter.api.Test
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@WebFluxTest(WatchlistController::class)
@Import(WatchlistControllerTest.Mocks::class, ApiExceptionHandler::class)
class WatchlistControllerTest(
    @org.springframework.beans.factory.annotation.Autowired val webClient: WebTestClient,
    @org.springframework.beans.factory.annotation.Autowired val service: WatchlistService,
) {
    @TestConfiguration
    class Mocks {
        @Bean
        fun watchlistService(): WatchlistService = mockk()
    }

    @Test
    fun `returns watchlist entries`() {
        coEvery { service.list() } returns listOf(
            WatchlistEntryDto(1, 211, "AAPL:xnas", "Apple Inc.", "Stock", 1.0, 2.0, 1.5, "USD", "Open", 15, true),
        )

        webClient.get().uri("/api/watchlist").exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].symbol").isEqualTo("AAPL:xnas")
            .jsonPath("$[0].mid").isEqualTo(1.5)
    }

    @Test
    fun `add validates the request body`() {
        webClient.post().uri("/api/watchlist")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"assetType":""}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
    }

    @Test
    fun `remove returns 404 problem detail when item is missing`() {
        coEvery { service.remove(99) } throws WatchlistItemNotFoundException(99)

        webClient.delete().uri("/api/watchlist/99").exchange()
            .expectStatus().isNotFound
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("Watchlist item not found")
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

        webClient.get().uri("/api/watchlist").exchange()
            .expectStatus().isUnauthorized
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.title").isEqualTo("Saxo API error")
    }
}
