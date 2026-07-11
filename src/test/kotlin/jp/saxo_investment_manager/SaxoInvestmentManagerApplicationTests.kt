package jp.saxo_investment_manager

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Boots the full application context against a real MySQL (Testcontainers).
 * Requires a running Docker daemon.
 */
@SpringBootTest(properties = ["fundamentals.fmp.api-key=test-context-key"])
@Testcontainers
class SaxoInvestmentManagerApplicationTests {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }

    @Test
    fun contextLoads() {
    }
}
