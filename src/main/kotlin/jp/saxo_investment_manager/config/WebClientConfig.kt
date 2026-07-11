package jp.saxo_investment_manager.config

import io.netty.channel.ChannelOption
import jp.saxo_investment_manager.fundamentals.FmpProperties
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

/**
 * Builds the single [WebClient] every Saxo client shares. It is pinned to the active
 * environment's base URL and automatically attaches the bearer token to every request.
 */
@Configuration
@EnableConfigurationProperties(SaxoProperties::class, FmpProperties::class)
class WebClientConfig(
    private val properties: SaxoProperties,
    private val tokenProvider: SaxoTokenProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun saxoWebClient(): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.connectTimeoutMs.toInt())
            .responseTimeout(Duration.ofMillis(properties.responseTimeoutMs))

        log.info("Saxo WebClient targeting {} at {}", properties.environment, properties.environment.restBaseUrl)

        return WebClient.builder()
            .baseUrl(properties.environment.restBaseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .filter(bearerTokenFilter())
            .filter(loggingFilter())
            .build()
    }

    /** Attaches `Authorization: Bearer <token>` to every outgoing request. */
    private fun bearerTokenFilter() = ExchangeFilterFunction.ofRequestProcessor { request ->
        mono {
            val token = tokenProvider.accessToken()
            ClientRequest.from(request)
                .headers { it.setBearerAuth(token) }
                .build()
        }
    }

    private fun loggingFilter() = ExchangeFilterFunction.ofRequestProcessor { request ->
        mono {
            log.debug("→ Saxo {} {}", request.method(), request.url())
            request
        }
    }
}
