package jp.saxo_investment_manager.api

import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpRequestDecorator
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.http.server.reactive.ServerHttpResponseDecorator
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebExchangeDecorator
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Logs exactly two lines per API call: one for the request (method + path + query + JSON body — the
 * controller's input) and one for the response (status + elapsed time + JSON body — the outcome).
 *
 * Runs at the WebFilter layer so it works uniformly across the coroutine controllers and the SSE
 * streaming endpoints. Bodies are captured by *peeking* each buffer (read a copy, reset the read
 * position) so nothing downstream is disturbed, capped at [MAX_BODY_CHARS] with whitespace collapsed.
 * The request line is emitted once its body has been read (so the body can be on the same line); a
 * bodyless request logs immediately, and a `doFinally` fallback guarantees the line is emitted once.
 *
 * The SSE endpoints are left alone for free: they emit via `writeAndFlushWith` (not the `writeWith`
 * tapped here) and aren't `application/json`, so the open-ended stream is never collected. Only the
 * `/api/` surface is logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // outermost, so timing spans the whole exchange
class RequestLoggingFilter : WebFilter {
    private val log = LoggerFactory.getLogger("jp.saxo_investment_manager.api.RequestLog")

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val request = exchange.request
        if (!request.path.value().startsWith("/api/")) return chain.filter(exchange)

        val method = request.method.name()
        val uri = request.uri.rawPath + (request.uri.rawQuery?.let { "?$it" } ?: "")
        val startNanos = System.nanoTime()
        val requestBody = ByteArrayOutputStream()
        val responseBody = ByteArrayOutputStream()

        val requestLogged = AtomicBoolean(false)
        fun logRequest() {
            if (requestLogged.compareAndSet(false, true)) {
                log.info("→ {} {}{}", method, uri, bodySuffix(requestBody))
            }
        }

        val hasJsonBody = isJson(request.headers.contentType)
        if (!hasJsonBody) logRequest() // no body coming — log the request line now
        val loggingRequest =
            if (hasJsonBody) LoggingRequest(request, requestBody, ::logRequest) else request
        val loggingResponse = LoggingResponse(exchange.response, responseBody)
        val decorated = object : ServerWebExchangeDecorator(exchange) {
            override fun getRequest(): ServerHttpRequest = loggingRequest
            override fun getResponse(): ServerHttpResponse = loggingResponse
        }

        return chain.filter(decorated).doFinally {
            logRequest() // fallback: emit the request line even if the body was never read
            val millis = (System.nanoTime() - startNanos) / 1_000_000
            // Spring records only explicitly-set codes; a null here is an un-set implicit 200. Error
            // and @ResponseStatus paths (404/502/201/204) do set it, so null uniquely means 200 OK.
            val status = exchange.response.statusCode?.value() ?: 200
            log.info("← {} {} → {} ({} ms){}", method, uri, status, millis, bodySuffix(responseBody))
        }
    }

    /** Peeks the request body into [sink] as the controller reads it, then emits the request line. */
    private class LoggingRequest(
        delegate: ServerHttpRequest,
        private val sink: ByteArrayOutputStream,
        private val onBodyComplete: () -> Unit,
    ) : ServerHttpRequestDecorator(delegate) {
        override fun getBody(): Flux<DataBuffer> =
            super.getBody().doOnNext { peek(it, sink) }.doOnComplete { onBodyComplete() }
    }

    /** Peeks JSON `writeWith` bodies into [sink]; leaves SSE's `writeAndFlushWith` untouched. */
    private class LoggingResponse(
        delegate: ServerHttpResponse,
        private val sink: ByteArrayOutputStream,
    ) : ServerHttpResponseDecorator(delegate) {
        override fun writeWith(body: Publisher<out DataBuffer>): Mono<Void> {
            if (!isJson(headers.contentType)) return super.writeWith(body)
            return super.writeWith(Flux.from(body).doOnNext { peek(it, sink) })
        }
    }
}

private const val MAX_BODY_CHARS = 4096

private fun isJson(contentType: MediaType?): Boolean =
    contentType != null && (contentType.subtype.equals("json", true) || contentType.subtypeSuffix.equals("json", true))

/** Copies up to the remaining cap of bytes out of [buffer] without advancing it for downstream readers. */
private fun peek(buffer: DataBuffer, sink: ByteArrayOutputStream) {
    val count = minOf(buffer.readableByteCount(), MAX_BODY_CHARS - sink.size())
    if (count <= 0) return
    val position = buffer.readPosition()
    val bytes = ByteArray(count)
    buffer.read(bytes)
    buffer.readPosition(position)
    sink.write(bytes)
}

/** `" body: {…}"` for a captured body, or `""` when there was none. */
private fun bodySuffix(sink: ByteArrayOutputStream): String {
    if (sink.size() == 0) return ""
    val text = sink.toString(Charsets.UTF_8).replace(Regex("\\s+"), " ").trim()
    val capped = if (sink.size() >= MAX_BODY_CHARS) "$text… (truncated)" else text
    return " body: $capped"
}
