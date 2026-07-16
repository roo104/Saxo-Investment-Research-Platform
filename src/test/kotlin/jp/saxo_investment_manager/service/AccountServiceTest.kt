package jp.saxo_investment_manager.service

import io.mockk.coEvery
import io.mockk.mockk
import jp.saxo_investment_manager.api.PerformancePeriod
import jp.saxo_investment_manager.saxo.AccountBalance
import jp.saxo_investment_manager.saxo.AccountClient
import jp.saxo_investment_manager.saxo.AccountPerformance
import jp.saxo_investment_manager.saxo.BalancePerformance
import jp.saxo_investment_manager.saxo.ClientInfo
import jp.saxo_investment_manager.saxo.ClosedPositionData
import jp.saxo_investment_manager.saxo.PerformanceSample
import jp.saxo_investment_manager.saxo.TimeWeightedPerformance
import jp.saxo_investment_manager.saxo.ClosedPositionEntry
import jp.saxo_investment_manager.saxo.DisplayAndFormat
import jp.saxo_investment_manager.saxo.NetPosition
import jp.saxo_investment_manager.saxo.NetPositionBase
import jp.saxo_investment_manager.saxo.NetPositionView
import jp.saxo_investment_manager.saxo.SaxoAccount
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountServiceTest {
    private val client = mockk<AccountClient>()
    private val service = AccountService(client)

    @Test
    fun `overview maps accounts and balance`() = runBlocking {
        coEvery { client.accounts() } returns listOf(
            SaxoAccount(
                accountId = "9226248",
                accountKey = "k",
                currency = "USD",
                accountType = "Normal",
                active = true
            ),
        )
        coEvery { client.balance() } returns AccountBalance(
            currency = "USD", cashBalance = 100000.0, totalValue = 117500.0,
            marginAvailableForTrading = 95000.0, marginUsedByCurrentPositions = 5000.0, openPositionsCount = 2,
        )

        val overview = service.overview()

        assertEquals("9226248", overview.accounts.single().accountId)
        assertEquals(95000.0, overview.balance.marginAvailable)
        assertEquals(5000.0, overview.balance.marginUsed)
        assertEquals(2, overview.balance.openPositionsCount)
    }

    @Test
    fun `positions derive P&L ratio from cost basis and convert day change to a ratio`() = runBlocking {
        coEvery { client.netPositions() } returns listOf(
            NetPosition(
                netPositionId = "211__Stock",
                base = NetPositionBase(uic = 211, assetType = "Stock", amount = 100.0, openingDirection = "Buy"),
                view = NetPositionView(
                    averageOpenPrice = 150.0, currentPrice = 175.0, marketValue = 17500.0,
                    profitLossOnTrade = 2500.0, profitLossOnTradeInBaseCurrency = 2400.0,
                    instrumentPriceDayPercentChange = 1.2,
                ),
                displayAndFormat = DisplayAndFormat(currency = "USD", description = "Apple Inc.", symbol = "AAPL:xnas"),
            ),
        )

        val pos = service.positions().single()

        assertEquals("AAPL:xnas", pos.symbol)
        assertEquals(2500.0, pos.profitLoss)
        // Base-currency P/L is passed through verbatim for account-level aggregation.
        assertEquals(2400.0, pos.profitLossBase)
        // 2500 / (150 * 100) = 0.1667
        assertEquals(0.1667, pos.profitLossPct!!, 1e-4)
        // 1.2% expressed as a ratio
        assertEquals(0.012, pos.dayChangePct!!, 1e-9)
    }

    @Test
    fun `P&L ratio is null when cost basis is unknown`() = runBlocking {
        coEvery { client.netPositions() } returns listOf(
            NetPosition(
                netPositionId = "21__FxSpot",
                base = NetPositionBase(uic = 21, assetType = "FxSpot", amount = null),
                view = NetPositionView(profitLossOnTrade = 12.5),
                displayAndFormat = DisplayAndFormat(symbol = "EURUSD"),
            ),
        )

        val pos = service.positions().single()

        assertEquals(12.5, pos.profitLoss)
        assertNull(pos.profitLossPct)
    }

    @Test
    fun `closed positions map costs and currency-conversion P&L in base currency`() = runBlocking {
        coEvery { client.closedPositions() } returns listOf(
            ClosedPositionEntry(
                closedPositionUniqueId = "1-2",
                netPositionId = "EURUSD__FxSpot",
                closed = ClosedPositionData(
                    uic = 21, assetType = "FxSpot", amount = 100000.0, buyOrSell = "Buy",
                    openPrice = 1.13054, closingPrice = 1.13025,
                    costOpeningInBaseCurrency = -5.0, costClosingInBaseCurrency = -4.0,
                    closedProfitLoss = 29.0, closedProfitLossInBaseCurrency = 25.64,
                    profitLossCurrencyConversion = -1.2,
                ),
                displayAndFormat = DisplayAndFormat(
                    currency = "USD",
                    description = "Euro/US Dollar",
                    symbol = "EURUSD"
                ),
            ),
        )

        val cp = service.closedPositions().single()

        assertEquals("EURUSD", cp.symbol)
        assertEquals("1-2", cp.closedPositionId)
        assertEquals(-5.0, cp.openingCost)
        assertEquals(-4.0, cp.closingCost)
        assertEquals(25.64, cp.profitLossBase)
        assertEquals(-1.2, cp.currencyConversionPl)
    }

    @Test
    fun `performance resolves the client key and derives returns from the value curve`() = runBlocking {
        coEvery { client.me() } returns ClientInfo(clientKey = "ck==")
        coEvery { client.performance("ck==", "Year") } returns AccountPerformance(
            balance = BalancePerformance(
                accountValue = listOf(
                    PerformanceSample("2026-01-01", 100000.0),
                    PerformanceSample("2026-07-15", 117500.0),
                ),
            ),
            timeWeighted = TimeWeightedPerformance(
                accumulated = listOf(
                    PerformanceSample("2026-01-01", 0.0),
                    PerformanceSample("2026-07-15", 0.175),
                ),
            ),
        )

        val perf = service.performance(PerformancePeriod.Year)

        assertTrue(perf.available)
        assertEquals(PerformancePeriod.Year, perf.period)
        assertEquals(100000.0, perf.startValue)
        assertEquals(117500.0, perf.endValue)
        assertEquals(17500.0, perf.absoluteReturn)
        // Prefers Saxo's accumulated time-weighted return over the simple curve change.
        assertEquals(0.175, perf.returnPct)
        assertEquals(2, perf.points.size)
        assertEquals("2026-01-01", perf.points.first().date)
    }

    @Test
    fun `performance falls back to the simple curve change when no time-weighted series`() = runBlocking {
        coEvery { client.me() } returns ClientInfo(clientKey = "ck==")
        coEvery { client.performance("ck==", "Quarter") } returns AccountPerformance(
            balance = BalancePerformance(
                accountValue = listOf(
                    PerformanceSample("2026-04-01", 200.0),
                    PerformanceSample("2026-06-30", 250.0),
                ),
            ),
        )

        val perf = service.performance(PerformancePeriod.Quarter)

        // 250 / 200 - 1 = 0.25
        assertEquals(0.25, perf.returnPct!!, 1e-9)
        assertEquals(50.0, perf.absoluteReturn)
    }

    @Test
    fun `performance is unavailable when the value curve is flat at zero`() = runBlocking {
        // A dead/unfunded account: Saxo returns points, but every account value is zero.
        coEvery { client.me() } returns ClientInfo(clientKey = "ck==")
        coEvery { client.performance("ck==", "Year") } returns AccountPerformance(
            balance = BalancePerformance(
                accountValue = listOf(
                    PerformanceSample("2016-03-28", 0.0),
                    PerformanceSample("2016-03-29", 0.0),
                ),
            ),
        )

        val perf = service.performance(PerformancePeriod.Year)

        assertFalse(perf.available)
        // The curve is still carried; the frontend simply doesn't chart a flat-zero line.
        assertEquals(2, perf.points.size)
    }

    @Test
    fun `performance is marked unavailable when Saxo returns no series`() = runBlocking {
        coEvery { client.me() } returns ClientInfo(clientKey = "ck==")
        coEvery { client.performance("ck==", "AllTime") } returns AccountPerformance()

        val perf = service.performance(PerformancePeriod.AllTime)

        assertFalse(perf.available)
        assertNull(perf.startValue)
        assertNull(perf.returnPct)
        assertTrue(perf.points.isEmpty())
    }
}
