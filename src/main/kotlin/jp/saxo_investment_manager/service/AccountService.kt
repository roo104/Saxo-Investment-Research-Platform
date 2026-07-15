package jp.saxo_investment_manager.service

import jp.saxo_investment_manager.api.AccountDto
import jp.saxo_investment_manager.api.AccountOverviewDto
import jp.saxo_investment_manager.api.PositionDto
import jp.saxo_investment_manager.saxo.AccountClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service

/**
 * Exposes read-only Saxo account state — accounts, balance and open net positions — mapped from the
 * PascalCase [AccountClient] responses onto the frontend's camelCase contract.
 *
 * Everything here is served straight from Saxo's Portfolio service group; there is no local
 * persistence (unlike the watchlist [PortfolioService]).
 */
@Service
class AccountService(private val accountClient: AccountClient) {

    /** Accounts plus their aggregate balance, fetched concurrently. */
    suspend fun overview(): AccountOverviewDto = coroutineScope {
        val accounts = async { accountClient.accounts() }
        val balance = async { accountClient.balance() }
        AccountOverviewDto(
            accounts = accounts.await().map {
                AccountDto(
                    accountId = it.accountId,
                    currency = it.currency,
                    accountType = it.accountType,
                    active = it.active,
                )
            },
            balance = balance.await().toDto(),
        )
    }

    /** Open net positions, valued and mapped for display. */
    suspend fun positions(): List<PositionDto> =
        accountClient.netPositions().map { it.toDto() }
}
