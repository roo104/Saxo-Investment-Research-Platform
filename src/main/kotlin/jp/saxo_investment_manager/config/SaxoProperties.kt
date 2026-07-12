package jp.saxo_investment_manager.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Type-safe configuration for talking to Saxo, bound from the `saxo.*` properties.
 *
 * Each environment carries its own token so that a simulation token can never be sent to
 * Live and vice-versa: only [activeToken] for the selected [environment] is ever used.
 */
@ConfigurationProperties(prefix = "saxo")
data class SaxoProperties(
    /** Which Saxo environment every request is routed to. */
    val environment: SaxoEnvironment = SaxoEnvironment.SIMULATION,
    val simulation: EnvironmentCredentials = EnvironmentCredentials(),
    val live: EnvironmentCredentials = EnvironmentCredentials(),
    /** TCP connect timeout for calls to Saxo, in milliseconds. */
    val connectTimeoutMs: Long = 5_000,
    /** Overall response timeout for calls to Saxo, in milliseconds. */
    val responseTimeoutMs: Long = 15_000,
    /**
     * Evict a pooled connection once it has been idle this long. Must be shorter than the
     * idle timeout of Saxo's gateway so we drop connections before the server does — otherwise
     * the next request reuses a half-closed socket and fails with "Connection reset".
     */
    val poolMaxIdleTimeMs: Long = 30_000,
    /** Hard cap on a pooled connection's total lifetime, regardless of activity, in milliseconds. */
    val poolMaxLifeTimeMs: Long = 300_000,
    /** How often the pool sweeps for and evicts stale connections in the background, in milliseconds. */
    val poolEvictInBackgroundMs: Long = 30_000,
) {
    data class EnvironmentCredentials(
        /** Bearer token used for the environment (e.g. a developer-portal 24-hour simulation token). */
        val token: String = "",
    )

    /** The bearer token configured for the currently active [environment]. */
    val activeToken: String
        get() = when (environment) {
            SaxoEnvironment.SIMULATION -> simulation.token
            SaxoEnvironment.LIVE -> live.token
        }
}
