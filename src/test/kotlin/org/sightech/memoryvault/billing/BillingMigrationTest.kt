package org.sightech.memoryvault.billing

import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class BillingMigrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("memoryvault_test")
            withUsername("memoryvault")
            withPassword("memoryvault")
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var userRepository: UserRepository

    @Test
    fun `new user defaults to FREE tier with null stripeCustomerId`() {
        val user = userRepository.save(User(
            email = "test-tier@example.com",
            displayName = "Test",
            role = UserRole.VIEWER
        ))
        assertEquals(Tier.FREE, user.tier)
        assertNull(user.stripeCustomerId)
    }

    @Test
    fun `user tier can be updated to PREMIUM`() {
        val user = userRepository.save(User(
            email = "premium@example.com",
            displayName = "Premium User",
            role = UserRole.VIEWER
        ))
        user.tier = Tier.PREMIUM
        val saved = userRepository.save(user)
        assertEquals(Tier.PREMIUM, saved.tier)
    }

    @Test
    fun `findAllByTierAndDeletedAtIsNull returns only users with matching tier`() {
        userRepository.save(User(email = "finder-free@example.com", displayName = "Free", role = UserRole.VIEWER))
        userRepository.save(User(email = "finder-premium@example.com", displayName = "Premium", role = UserRole.VIEWER).also { it.tier = Tier.PREMIUM })
        val freeUsers = userRepository.findAllByTierAndDeletedAtIsNull(Tier.FREE)
        assert(freeUsers.any { it.email == "finder-free@example.com" })
        assert(freeUsers.none { it.email == "finder-premium@example.com" })
    }
}
