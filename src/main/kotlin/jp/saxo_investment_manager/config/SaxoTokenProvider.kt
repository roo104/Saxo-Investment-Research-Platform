package jp.saxo_investment_manager.config

import org.springframework.stereotype.Component

/**
 * Supplies the bearer token used to authenticate against Saxo.
 *
 * This is the seam for authentication strategy. Today the only implementation is
 * [StaticTokenProvider], which serves a token pasted into configuration (the developer-portal
 * 24-hour simulation token). A future OAuth 2.0 Authorization Code (PKCE) implementation with
 * automatic refresh can replace it without touching any calling code — hence the `suspend`,
 * which lets an implementation refresh over the network transparently.
 */
interface SaxoTokenProvider {
    suspend fun accessToken(): String
}

/**
 * Serves the statically-configured token for the active environment.
 *
 * Fails fast with an actionable message when no token is configured, so a missing
 * `SAXO_SIM_TOKEN` surfaces immediately rather than as an opaque 401 from Saxo.
 */
@Component
class StaticTokenProvider(private val properties: SaxoProperties) : SaxoTokenProvider {
    override suspend fun accessToken(): String {
        val token = properties.activeToken
        check(token.isNotBlank()) {
            "No Saxo API token configured for the ${properties.environment} environment. " +
                    "Set the SAXO_SIM_TOKEN (simulation) or SAXO_LIVE_TOKEN (live) environment variable."
        }
        return token
    }
}
