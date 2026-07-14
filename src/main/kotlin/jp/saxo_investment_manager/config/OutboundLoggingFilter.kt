package jp.saxo_investment_manager.config

import org.slf4j.Logger
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import java.net.URI

/**
 * Logs every outbound HTTP request to an upstream ([target] is "Saxo" or "FMP") as two lines — the
 * request and its outcome (status + elapsed time) — mirroring the inbound
 * [jp.saxo_investment_manager.api.RequestLoggingFilter]'s `→`/`←` format.
 *
 * Secrets carried in the query string (FMP's `apikey`) are redacted before logging; the Saxo bearer
 * token lives in an `Authorization` header, which is never logged.
 */
fun outboundLoggingFilter(target: String, log: Logger): ExchangeFilterFunction =
    ExchangeFilterFunction { request, next ->
        val path = redactedPathAndQuery(request.url())
        val method = request.method()
        log.info("→ {} {} {}", target, method, path)
        val startNanos = System.nanoTime()
        next.exchange(request)
            .doOnNext { response ->
                val millis = (System.nanoTime() - startNanos) / 1_000_000
                log.info("← {} {} {} → {} ({} ms)", target, method, path, response.statusCode().value(), millis)
            }
            .doOnError { error ->
                val millis = (System.nanoTime() - startNanos) / 1_000_000
                log.info("← {} {} {} → failed: {} ({} ms)", target, method, path, error.toString(), millis)
            }
    }

private val SECRET_QUERY_PARAMS = setOf("apikey", "token")

/** The URL's path plus query, with any secret query-parameter values replaced by `***`. */
private fun redactedPathAndQuery(url: URI): String {
    val query = url.rawQuery ?: return url.rawPath
    val safe = query.split('&').joinToString("&") { param ->
        val name = param.substringBefore('=')
        if (name.lowercase() in SECRET_QUERY_PARAMS) "$name=***" else param
    }
    return "${url.rawPath}?$safe"
}
