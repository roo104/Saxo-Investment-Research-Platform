package jp.saxo_investment_manager.api

import jp.saxo_investment_manager.service.PortfolioItemNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.net.URI

/**
 * Translates backend exceptions into RFC-7807 `application/problem+json` responses.
 *
 * The guiding principle is no silent failures: an error talking to Saxo is surfaced with the
 * upstream status and body rather than being swallowed into an empty result. Validation errors
 * and other Spring exceptions are handled by Spring's built-in ProblemDetail support
 * (`spring.webflux.problemdetails.enabled=true`).
 */
@RestControllerAdvice
class ApiExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    /** An error response received from Saxo (e.g. 401 for an expired token). */
    @ExceptionHandler(WebClientResponseException::class)
    fun handleSaxoResponse(ex: WebClientResponseException): ProblemDetail {
        log.warn("Saxo returned {} for {}: {}", ex.statusCode, ex.request?.uri, ex.responseBodyAsString)
        return ProblemDetail.forStatusAndDetail(ex.statusCode, saxoDetail(ex)).apply {
            type = URI.create("https://saxo-investment-manager/errors/saxo-api")
            title = "Saxo API error"
            setProperty("saxoStatus", ex.statusCode.value())
        }
    }

    /** Saxo was unreachable (DNS, connection refused, timeout). */
    @ExceptionHandler(WebClientRequestException::class)
    fun handleSaxoRequest(ex: WebClientRequestException): ProblemDetail {
        log.error("Could not reach Saxo", ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Could not reach Saxo: ${ex.message}").apply {
            type = URI.create("https://saxo-investment-manager/errors/saxo-unreachable")
            title = "Saxo unreachable"
        }
    }

    @ExceptionHandler(PortfolioItemNotFoundException::class)
    fun handleNotFound(ex: PortfolioItemNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Not found").apply {
            title = "Portfolio item not found"
        }

    private fun saxoDetail(ex: WebClientResponseException): String {
        val body = ex.responseBodyAsString.take(500)
        return if (body.isBlank()) "Saxo returned ${ex.statusCode}" else "Saxo returned ${ex.statusCode}: $body"
    }
}
