package jp.saxo_investment_manager.config

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ch.qos.logback.classic.Logger as LogbackLogger

class OutboundLoggingFilterTest {

    private val logger = LoggerFactory.getLogger("test.outbound") as LogbackLogger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun setUp() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(appender)
    }

    private fun messages() = appender.list.map { it.formattedMessage }

    /** A WebClient whose only filter is the one under test and which never touches the network. */
    private fun client(status: HttpStatus) = WebClient.builder()
        .filter(outboundLoggingFilter("FMP", logger))
        .exchangeFunction { Mono.just(ClientResponse.create(status).build()) }
        .build()

    @Test
    fun `logs a request line and a response line with status and timing`() {
        client(HttpStatus.OK).get().uri("https://example.com/stable/quote?symbol=AAPL")
            .retrieve().toBodilessEntity().block()

        val messages = messages()
        assertEquals(2, messages.size, "exactly one request + one response line: $messages")
        assertEquals("→ FMP GET /stable/quote?symbol=AAPL", messages[0])
        assertTrue(
            messages[1].startsWith("← FMP GET /stable/quote?symbol=AAPL → 200 ("),
            "response line: ${messages[1]}"
        )
        assertTrue(messages[1].endsWith(" ms)"), "response line ends with timing: ${messages[1]}")
    }

    @Test
    fun `redacts the apikey query parameter so the secret never reaches the log`() {
        client(HttpStatus.OK).get().uri("https://example.com/stable/quote?symbol=AAPL&apikey=SECRET123")
            .retrieve().toBodilessEntity().block()

        val messages = messages()
        assertTrue(messages.none { it.contains("SECRET123") }, "apikey must be redacted: $messages")
        assertEquals("→ FMP GET /stable/quote?symbol=AAPL&apikey=***", messages[0])
    }

    @Test
    fun `logs the response line even on a non-2xx status`() {
        client(HttpStatus.NOT_FOUND).get().uri("https://example.com/stable/quote?symbol=BOGUS")
            .retrieve().onStatus({ true }, { Mono.empty() }).toBodilessEntity().block()

        val messages = messages()
        assertEquals(2, messages.size, "request + response line even for 404: $messages")
        assertTrue(
            messages[1].startsWith("← FMP GET /stable/quote?symbol=BOGUS → 404 ("),
            "response line: ${messages[1]}"
        )
    }
}
