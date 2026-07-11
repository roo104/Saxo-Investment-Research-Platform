package jp.saxo_investment_manager.portfolio

import org.springframework.data.jpa.repository.JpaRepository

interface PortfolioRepository : JpaRepository<PortfolioItem, Long> {
    fun findByUicAndAssetType(uic: Long, assetType: String): PortfolioItem?
}
