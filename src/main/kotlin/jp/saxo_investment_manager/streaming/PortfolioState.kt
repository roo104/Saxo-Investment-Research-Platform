package jp.saxo_investment_manager.streaming

import jp.saxo_investment_manager.api.AccountBalanceDto
import jp.saxo_investment_manager.api.PositionDto
import jp.saxo_investment_manager.saxo.AccountBalance
import jp.saxo_investment_manager.saxo.NetPosition
import jp.saxo_investment_manager.service.toDto
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

/**
 * Holds the retained streaming state for one account context — the aggregate balance and the open
 * net positions keyed by `NetPositionId` — and merges Saxo deltas into it.
 *
 * Separated from [SaxoPortfolioStream]'s transport so the merge rules (field-level balance deltas,
 * per-position upserts, deletion, full-list rebuild) are unit-testable with plain JSON. All access
 * is synchronized: deltas arrive on the WebSocket receive thread while snapshots are created from
 * subscription coroutines.
 */
internal class PortfolioState(private val objectMapper: ObjectMapper) {
    private val lock = Any()
    private var balance: ObjectNode? = null
    private val positions = LinkedHashMap<String, ObjectNode>()

    /** Replaces the balance with a fresh subscription snapshot and returns its mapped DTO. */
    fun applyBalanceSnapshot(snapshot: ObjectNode): AccountBalanceDto = synchronized(lock) {
        balance = snapshot
        snapshot.toBalanceDto()
    }

    /** Deep-merges a field-level balance delta onto the retained snapshot and returns the result. */
    fun applyBalanceDelta(payload: ByteArray): AccountBalanceDto = synchronized(lock) {
        val base = balance ?: objectMapper.createObjectNode()
        val merged: ObjectNode = objectMapper.readerForUpdating(base).readValue(payload)
        balance = merged
        merged.toBalanceDto()
    }

    /** Replaces all positions from a subscription snapshot's `Data` array and returns the list. */
    fun applyPositionsSnapshot(data: JsonNode?): List<PositionDto> = synchronized(lock) {
        positions.clear()
        data?.forEach { upsert(it) }
        positionList()
    }

    /** Upserts each position in a delta's `Data` array and returns the full valued list. */
    fun applyPositionsDelta(data: JsonNode): List<PositionDto> = synchronized(lock) {
        data.forEach { upsert(it) }
        positionList()
    }

    /** Drops all retained state, e.g. on reconnect or `_resetsubscriptions`. */
    fun clear() = synchronized(lock) {
        balance = null
        positions.clear()
    }

    fun currentBalance(): AccountBalanceDto? = synchronized(lock) { balance?.toBalanceDto() }

    fun currentPositions(): List<PositionDto> = synchronized(lock) { positionList() }

    /**
     * Merges one delta position into the map, keyed by `NetPositionId`. A delta flagged deleted
     * (Saxo's `__meta.Deleted`) removes the position; otherwise its fields are deep-merged onto the
     * retained node so partial updates accumulate. Caller holds [lock].
     */
    private fun upsert(node: JsonNode) {
        val id = node.get("NetPositionId")?.asText() ?: return
        if (node.get("__meta")?.get("Deleted")?.asBoolean() == true) {
            positions.remove(id)
            return
        }
        val existing = positions[id]
        positions[id] = if (existing == null) {
            node as? ObjectNode ?: return
        } else {
            objectMapper.readerForUpdating(existing).readValue(node)
        }
    }

    private fun positionList(): List<PositionDto> =
        positions.values.map { objectMapper.treeToValue(it, NetPosition::class.java).toDto() }

    private fun ObjectNode.toBalanceDto(): AccountBalanceDto =
        objectMapper.treeToValue(this, AccountBalance::class.java).toDto()
}
