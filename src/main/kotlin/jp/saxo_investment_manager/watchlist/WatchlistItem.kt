package jp.saxo_investment_manager.watchlist

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * A single instrument a user has pinned to their watchlist.
 *
 * Symbol and description are denormalized snapshots from Saxo taken when the item is added, so
 * the list renders instantly; live prices are fetched separately on read. An instrument may
 * appear at most once per asset type (enforced by the unique constraint).
 */
@Entity
@Table(
    name = "watchlist_item",
    uniqueConstraints = [UniqueConstraint(name = "uk_watchlist_uic_asset_type", columnNames = ["uic", "asset_type"])],
)
class WatchlistItem(
    @Column(nullable = false)
    var uic: Long,

    @Column(name = "asset_type", nullable = false)
    var assetType: String,

    @Column(nullable = false)
    var symbol: String,

    @Column(nullable = false, length = 512)
    var description: String,

    @Column(name = "added_at", nullable = false)
    var addedAt: Instant = Instant.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)
