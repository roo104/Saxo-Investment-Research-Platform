package jp.saxo_investment_manager.saxo

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

/**
 * Portfolio endpoints of the Saxo OpenAPI (`/port/v1`).
 *
 * Read-only account state: the accounts on the logged-in client, the aggregate cash/value balance,
 * and open net positions. All three use the `/me` variants, which resolve the account/client
 * context from the bearer token — so no keys need to be threaded through by the caller.
 */
@Component
class AccountClient(private val saxoWebClient: WebClient) {

    /**
     * The authenticated client via `GET /port/v1/clients/me`, carrying the `ClientKey` the
     * streaming subscription endpoints need in their arguments.
     */
    suspend fun me(): ClientInfo =
        saxoWebClient.get()
            .uri("/port/v1/clients/me")
            .retrieve()
            .awaitBody<ClientInfo>()

    /** Accounts belonging to the authenticated user via `GET /port/v1/accounts/me`. */
    suspend fun accounts(): List<SaxoAccount> =
        saxoWebClient.get()
            .uri("/port/v1/accounts/me")
            .retrieve()
            .awaitBody<SaxoCollection<SaxoAccount>>()
            .data

    /**
     * Aggregate balance for the authenticated user via `GET /port/v1/balances/me`.
     *
     * Unlike the collection endpoints this returns a single object, not a [SaxoCollection].
     */
    suspend fun balance(): AccountBalance =
        saxoWebClient.get()
            .uri("/port/v1/balances/me")
            .retrieve()
            .awaitBody<AccountBalance>()

    /**
     * Open net positions via `GET /port/v1/netpositions/me`, aggregated per instrument (multiple
     * fills in the same instrument collapse to one line). The field groups pull in the base
     * position, the priced view (market value, P/L) and the display metadata in one call.
     */
    suspend fun netPositions(): List<NetPosition> =
        saxoWebClient.get()
            .uri { builder ->
                builder.path("/port/v1/netpositions/me")
                    .queryParam("FieldGroups", "NetPositionBase,NetPositionView,DisplayAndFormat")
                    .build()
            }
            .retrieve()
            .awaitBody<SaxoCollection<NetPosition>>()
            .data
}
