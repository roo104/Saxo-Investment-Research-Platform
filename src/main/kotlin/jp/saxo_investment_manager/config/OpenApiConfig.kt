package jp.saxo_investment_manager.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun saxoOpenApi(): OpenAPI = OpenAPI().info(
        Info()
            .title("Saxo Investment Research Platform API")
            .description(
                "REST API for searching Saxo instruments and maintaining a portfolio with live quotes. " +
                        "All Saxo communication happens server-side; the active environment (simulation or live) " +
                        "is reported by GET /api/environment.",
            )
            .version("0.0.1"),
    )
}
