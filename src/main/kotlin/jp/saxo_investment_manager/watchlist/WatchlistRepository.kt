package jp.saxo_investment_manager.watchlist

import org.springframework.data.jpa.repository.JpaRepository

interface WatchlistRepository : JpaRepository<WatchlistItem, Long> {
    fun findByUicAndAssetType(uic: Long, assetType: String): WatchlistItem?
}
