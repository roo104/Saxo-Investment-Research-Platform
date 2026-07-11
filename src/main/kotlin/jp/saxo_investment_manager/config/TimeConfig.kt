package jp.saxo_investment_manager.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Exposes a [Clock] bean so time-dependent components can be tested against a fixed clock. */
@Configuration
class TimeConfig {
    @Bean
    fun clock(): Clock = Clock.systemDefaultZone()
}
