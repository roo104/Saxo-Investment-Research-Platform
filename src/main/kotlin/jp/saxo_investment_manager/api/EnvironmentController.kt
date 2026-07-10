package jp.saxo_investment_manager.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jp.saxo_investment_manager.config.SaxoProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/environment")
@Tag(name = "Environment", description = "Which Saxo environment the backend is connected to")
class EnvironmentController(private val properties: SaxoProperties) {

    @GetMapping
    @Operation(summary = "Get the active Saxo environment (SIMULATION or LIVE)")
    fun current(): EnvironmentDto =
        EnvironmentDto(properties.environment.name, properties.environment.restBaseUrl)
}
