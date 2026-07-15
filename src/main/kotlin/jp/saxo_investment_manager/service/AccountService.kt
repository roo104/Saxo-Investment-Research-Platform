package jp.saxo_investment_manager.service

import jp.saxo_investment_manager.api.AccountBalanceDto
import jp.saxo_investment_manager.api.AccountDto
import jp.saxo_investment_manager.api.AccountOverviewDto
import jp.saxo_investment_manager.api.PositionDto
import jp.saxo_investment_manager.saxo.AccountBalance
import jp.saxo_investment_manager.saxo.AccountClient
import jp.saxo_investment_manager.saxo.NetPosition
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service
import kotlin.math.abs

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

private fun AccountBalance.toDto() = AccountBalanceDto(
    currency = currency,
    cashBalance = cashBalance,
    totalValue = totalValue,
    nonMarginPositionsValue = nonMarginPositionsValue,
    unrealizedPositionsValue = unrealizedPositionsValue,
    marginAvailable = marginAvailableForTrading,
    marginUsed = marginUsedByCurrentPositions,
    openPositionsCount = openPositionsCount,
)

private fun NetPosition.toDto(): PositionDto {
    val avgOpen = view?.averageOpenPrice
    val amount = base?.amount
    // Saxo gives P/L in currency but no P/L ratio; derive it from the cost basis when both are known.
    val costBasis = if (avgOpen != null && amount != null) abs(avgOpen * amount) else null
    val pnl = view?.profitLossOnTrade
    val pnlPct = if (pnl != null && costBasis != null && costBasis != 0.0) pnl / costBasis else null

    return PositionDto(
        netPositionId = netPositionId,
        uic = base?.uic ?: 0,
        symbol = displayAndFormat?.symbol ?: netPositionId,
        description = displayAndFormat?.description ?: displayAndFormat?.symbol ?: netPositionId,
        assetType = base?.assetType ?: "",
        currency = displayAndFormat?.currency ?: view?.exposureCurrency,
        amount = amount,
        openingDirection = base?.openingDirection,
        averageOpenPrice = avgOpen,
        currentPrice = view?.currentPrice,
        marketValue = view?.marketValue,
        profitLoss = pnl,
        profitLossPct = pnlPct,
        // Saxo expresses the day change as a percentage number (1.2 = 1.2%); the API contract is a ratio.
        dayChangePct = view?.instrumentPriceDayPercentChange?.let { it / 100.0 },
    )
}
