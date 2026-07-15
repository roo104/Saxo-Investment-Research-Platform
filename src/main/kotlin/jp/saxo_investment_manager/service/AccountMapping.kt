package jp.saxo_investment_manager.service

import jp.saxo_investment_manager.api.AccountBalanceDto
import jp.saxo_investment_manager.api.ClosedPositionDto
import jp.saxo_investment_manager.api.PositionDto
import jp.saxo_investment_manager.saxo.AccountBalance
import jp.saxo_investment_manager.saxo.ClosedPositionEntry
import jp.saxo_investment_manager.saxo.NetPosition
import kotlin.math.abs

/**
 * Maps Saxo's PascalCase Portfolio DTOs onto the frontend's camelCase account contract.
 *
 * Shared by [AccountService] (REST snapshot) and the streaming layer (live deltas), so the
 * P/L-ratio derivation lives in exactly one place regardless of how the data arrived.
 */

internal fun AccountBalance.toDto() = AccountBalanceDto(
    currency = currency,
    cashBalance = cashBalance,
    totalValue = totalValue,
    nonMarginPositionsValue = nonMarginPositionsValue,
    marginAvailable = marginAvailableForTrading,
    marginUsed = marginUsedByCurrentPositions,
    openPositionsCount = openPositionsCount,
)

internal fun NetPosition.toDto(): PositionDto {
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
        profitLossBase = view?.profitLossOnTradeInBaseCurrency,
        profitLossPct = pnlPct,
        // Saxo expresses the day change as a percentage number (1.2 = 1.2%); the API contract is a ratio.
        dayChangePct = view?.instrumentPriceDayPercentChange?.let { it / 100.0 },
        tradeCosts = view?.tradeCostsTotal,
        tradeCostsBase = view?.tradeCostsTotalInBaseCurrency,
    )
}

internal fun ClosedPositionEntry.toDto(): ClosedPositionDto {
    val c = closed
    return ClosedPositionDto(
        closedPositionId = closedPositionUniqueId,
        uic = c?.uic,
        symbol = displayAndFormat?.symbol ?: netPositionId ?: closedPositionUniqueId,
        description = displayAndFormat?.description ?: displayAndFormat?.symbol ?: netPositionId
        ?: closedPositionUniqueId,
        assetType = c?.assetType,
        currency = displayAndFormat?.currency,
        amount = c?.amount,
        buyOrSell = c?.buyOrSell,
        openPrice = c?.openPrice,
        closingPrice = c?.closingPrice,
        openedAt = c?.executionTimeOpen,
        closedAt = c?.executionTimeClose,
        // Costs are reported in the account base currency so they sum cleanly across instruments.
        openingCost = c?.costOpeningInBaseCurrency,
        closingCost = c?.costClosingInBaseCurrency,
        profitLoss = c?.closedProfitLoss,
        profitLossBase = c?.closedProfitLossInBaseCurrency,
        currencyConversionPl = c?.profitLossCurrencyConversion,
    )
}
