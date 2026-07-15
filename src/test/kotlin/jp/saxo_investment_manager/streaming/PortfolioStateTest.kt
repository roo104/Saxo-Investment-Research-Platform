package jp.saxo_investment_manager.streaming

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PortfolioStateTest {
    private val mapper = jacksonObjectMapper()
    private val state = PortfolioState(mapper)

    private fun obj(json: String): ObjectNode = mapper.readTree(json) as ObjectNode
    private fun dataOf(json: String): JsonNode = mapper.readTree(json).get("Data")

    @Test
    fun `balance delta deep-merges onto the retained snapshot`() {
        state.applyBalanceSnapshot(
            obj("""{"Currency":"USD","CashBalance":100000.0,"TotalValue":117500.0,"OpenPositionsCount":2}"""),
        )

        // A delta carrying only the changed fields must not wipe currency/cash.
        val dto = state.applyBalanceDelta("""{"TotalValue":118000.0}""".toByteArray())

        assertEquals("USD", dto.currency)
        assertEquals(100000.0, dto.cashBalance)
        assertEquals(118000.0, dto.totalValue)
        assertEquals(2, dto.openPositionsCount)
    }

    @Test
    fun `positions snapshot maps the list and derives the P&L ratio`() {
        val positions = state.applyPositionsSnapshot(
            dataOf(
                """
                {"Data":[
                  {"NetPositionId":"211__Stock",
                   "NetPositionBase":{"Uic":211,"AssetType":"Stock","Amount":100,"OpeningDirection":"Buy"},
                   "NetPositionView":{"AverageOpenPrice":150.0,"CurrentPrice":175.0,"MarketValue":17500.0,
                     "ProfitLossOnTrade":2500.0,"InstrumentPriceDayPercentChange":1.2},
                   "DisplayAndFormat":{"Currency":"USD","Description":"Apple Inc.","Symbol":"AAPL:xnas"}}
                ]}
                """.trimIndent(),
            ),
        )

        val pos = positions.single()
        assertEquals("AAPL:xnas", pos.symbol)
        assertEquals(2500.0, pos.profitLoss)
        assertEquals(0.1667, pos.profitLossPct!!, 1e-4) // 2500 / (150 * 100)
        assertEquals(0.012, pos.dayChangePct!!, 1e-9) // 1.2% as a ratio
    }

    @Test
    fun `position delta merges partial view fields onto the retained position`() {
        state.applyPositionsSnapshot(
            dataOf(
                """
                {"Data":[
                  {"NetPositionId":"211__Stock",
                   "NetPositionBase":{"Uic":211,"AssetType":"Stock","Amount":100,"OpeningDirection":"Buy"},
                   "NetPositionView":{"AverageOpenPrice":150.0,"CurrentPrice":175.0,"MarketValue":17500.0,
                     "ProfitLossOnTrade":2500.0},
                   "DisplayAndFormat":{"Currency":"USD","Description":"Apple Inc.","Symbol":"AAPL:xnas"}}
                ]}
                """.trimIndent(),
            ),
        )

        // Only the moving view fields arrive in the delta; identity/base fields must survive.
        val positions = state.applyPositionsDelta(
            dataOf(
                """{"Data":[{"NetPositionId":"211__Stock",
                   "NetPositionView":{"CurrentPrice":180.0,"MarketValue":18000.0,"ProfitLossOnTrade":3000.0}}]}""",
            ),
        )

        val pos = positions.single()
        assertEquals("AAPL:xnas", pos.symbol) // retained from the snapshot
        assertEquals(180.0, pos.currentPrice)
        assertEquals(3000.0, pos.profitLoss)
        assertEquals(0.2, pos.profitLossPct!!, 1e-9) // 3000 / (150 * 100), avg-open retained
    }

    @Test
    fun `position delta adds a newly opened position`() {
        state.applyPositionsSnapshot(
            dataOf("""{"Data":[{"NetPositionId":"211__Stock","NetPositionBase":{"Uic":211,"AssetType":"Stock"}}]}"""),
        )

        val positions = state.applyPositionsDelta(
            dataOf("""{"Data":[{"NetPositionId":"21__FxSpot","NetPositionBase":{"Uic":21,"AssetType":"FxSpot"}}]}"""),
        )

        assertEquals(setOf("211__Stock", "21__FxSpot"), positions.map { it.netPositionId }.toSet())
    }

    @Test
    fun `position delta flagged deleted removes the position`() {
        state.applyPositionsSnapshot(
            dataOf(
                """{"Data":[
                  {"NetPositionId":"211__Stock","NetPositionBase":{"Uic":211,"AssetType":"Stock"}},
                  {"NetPositionId":"21__FxSpot","NetPositionBase":{"Uic":21,"AssetType":"FxSpot"}}
                ]}""",
            ),
        )

        val positions = state.applyPositionsDelta(
            dataOf("""{"Data":[{"NetPositionId":"211__Stock","__meta":{"Deleted":true}}]}"""),
        )

        assertEquals(listOf("21__FxSpot"), positions.map { it.netPositionId })
    }

    @Test
    fun `clear drops retained balance and positions`() {
        state.applyBalanceSnapshot(obj("""{"Currency":"USD","TotalValue":1.0}"""))
        state.applyPositionsSnapshot(dataOf("""{"Data":[{"NetPositionId":"x","NetPositionBase":{"Uic":1,"AssetType":"Stock"}}]}"""))

        state.clear()

        assertNull(state.currentBalance())
        assertEquals(emptyList(), state.currentPositions())
    }
}
