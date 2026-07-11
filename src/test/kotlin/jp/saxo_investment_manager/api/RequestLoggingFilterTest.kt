package jp.saxo_investment_manager.api

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ch.qos.logback.classic.Logger as LogbackLogger

class RequestLoggingFilterTest {

    @RestController
    class EchoController {
        @PostMapping("/api/echo")
        fun echo(@RequestBody body: Map<String, Any>): Map<String, Any> = body + ("echoed" to true)

        @GetMapping("/api/ping")
        fun ping(): Map<String, Any> = mapOf("pong" to true)
    }

    private val logger = LoggerFactory.getLogger("jp.saxo_investment_manager.api.RequestLog") as LogbackLogger
    private val appender = ListAppender<ILoggingEvent>()
    private val client = WebTestClient.bindToController(EchoController())
        .webFilter<WebTestClient.ControllerSpec>(RequestLoggingFilter())
        .build()

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

    @Test
    fun `logs one request line and one response line, each with its json body inline`() {
        client.post().uri("/api/echo")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("uic" to 211, "assetType" to "Stock"))
            .exchange()
            .expectStatus().isOk
            // Response must be byte-perfect — proves peeking reset the read position for the real consumer.
            .expectBody()
            .jsonPath("$.uic").isEqualTo(211)
            .jsonPath("$.assetType").isEqualTo("Stock")
            .jsonPath("$.echoed").isEqualTo(true)

        val messages = messages()
        assertEquals(2, messages.size, "exactly one request + one response line: $messages")
        val (req, resp) = messages
        assertEquals("→ POST /api/echo body: {\"uic\":211,\"assetType\":\"Stock\"}", req)
        assertTrue(resp.startsWith("← POST /api/echo → 200 ("), "response line: $resp")
        assertTrue(
            resp.endsWith(" body: {\"uic\":211,\"assetType\":\"Stock\",\"echoed\":true}"),
            "response body inline: $resp"
        )
    }

    @Test
    fun `logs a bodyless GET as one request line with no body and one response line`() {
        client.get().uri("/api/ping").exchange().expectStatus().isOk

        val messages = messages()
        assertEquals(2, messages.size, "exactly one request + one response line: $messages")
        assertEquals("→ GET /api/ping", messages[0])
        assertTrue(messages[1].startsWith("← GET /api/ping → 200 ("), "response line: ${messages[1]}")
        assertTrue(messages[1].endsWith(" body: {\"pong\":true}"), "response body inline: ${messages[1]}")
    }
}
