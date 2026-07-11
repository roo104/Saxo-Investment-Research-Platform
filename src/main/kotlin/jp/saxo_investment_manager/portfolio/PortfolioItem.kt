package jp.saxo_investment_manager.portfolio

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * A single instrument a user has pinned to their portfolio.
 *
 * Symbol and description are denormalized snapshots from Saxo taken when the item is added, so
 * the list renders instantly; live prices are fetched separately on read. An instrument may
 * appear at most once per asset type (enforced by the unique constraint).
 *
 * [quantity] and [openingPrice] record the size and entry price of the holding; together with the
 * current price they give the position a value, which drives the allocation breakdown. They are
 * nullable at the column level so the schema upgrades cleanly over pre-existing rows, but every
 * item added through the API supplies both.
 */
@Entity
@Table(
    name = "portfolio_item",
    uniqueConstraints = [UniqueConstraint(name = "uk_portfolio_uic_asset_type", columnNames = ["uic", "asset_type"])],
)
class PortfolioItem(
    @Column(nullable = false)
    var uic: Long,

    @Column(name = "asset_type", nullable = false)
    var assetType: String,

    @Column(nullable = false)
    var symbol: String,

    @Column(nullable = false, length = 512)
    var description: String,

    @Column(name = "quantity")
    var quantity: Double? = null,

    @Column(name = "opening_price")
    var openingPrice: Double? = null,

    /** Sector classification, snapshotted once from the fundamentals feed at add-time (null when
     * unavailable — non-equity, unknown ticker, or a non-US listing the free tier can't reach). */
    @Column(name = "sector")
    var sector: String? = null,

    @Column(name = "added_at", nullable = false)
    var addedAt: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
