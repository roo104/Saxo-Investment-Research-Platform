package jp.saxo_investment_manager.watchlist

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Verifies the watchlist repository against a real MySQL instance (Testcontainers).
 * Requires a running Docker daemon.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class WatchlistRepositoryTest(@Autowired val repository: WatchlistRepository) {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }

    @Test
    fun `saves and finds an item by uic and asset type`() {
        val saved = repository.save(
            WatchlistItem(
                uic = 211,
                assetType = "Stock",
                symbol = "AAPL:xnas",
                description = "Apple Inc."
            )
        )
        assertNotNull(saved.id)

        val found = repository.findByUicAndAssetType(211, "Stock")
        assertNotNull(found)
        assertEquals("AAPL:xnas", found.symbol)
    }

    @Test
    fun `enforces uniqueness per uic and asset type`() {
        repository.saveAndFlush(WatchlistItem(uic = 300, assetType = "Stock", symbol = "S", description = "D"))
        assertFailsWith<Exception> {
            repository.saveAndFlush(WatchlistItem(uic = 300, assetType = "Stock", symbol = "S2", description = "D2"))
        }
    }
}
