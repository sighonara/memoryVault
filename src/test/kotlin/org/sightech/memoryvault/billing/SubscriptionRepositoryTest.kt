package org.sightech.memoryvault.billing

import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.entity.SubscriptionEntity
import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class SubscriptionRepositoryTest {

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

    @Autowired lateinit var subscriptionRepository: SubscriptionRepository
    @Autowired lateinit var userRepository: UserRepository

    @Test
    fun `can save and find subscription by userId`() {
        val user = userRepository.save(User(
            email = "sub-test@example.com",
            displayName = "Sub Test",
            role = UserRole.VIEWER,
            tier = Tier.PREMIUM
        ))
        subscriptionRepository.save(SubscriptionEntity(
            userId = user.id,
            stripeCustomerId = "cus_test123",
            stripeSubscriptionId = "sub_test123",
            billingInterval = BillingInterval.MONTHLY,
            status = SubscriptionStatus.ACTIVE
        ))
        val found = subscriptionRepository.findByUserId(user.id)
        assertNotNull(found)
        assert(found.stripeSubscriptionId == "sub_test123")
    }

    @Test
    fun `findExpiredGracePeriods returns subscriptions past deadline`() {
        val user = userRepository.save(User(
            email = "grace@example.com",
            displayName = "Grace",
            role = UserRole.VIEWER
        ))
        subscriptionRepository.save(SubscriptionEntity(
            userId = user.id,
            stripeCustomerId = "cus_grace",
            stripeSubscriptionId = "sub_grace",
            billingInterval = BillingInterval.MONTHLY,
            status = SubscriptionStatus.GRACE_PERIOD,
            gracePeriodEndsAt = Instant.now().minusSeconds(3600)
        ))
        val expired = subscriptionRepository.findExpiredGracePeriods(Instant.now())
        assertTrue(expired.any { it.stripeSubscriptionId == "sub_grace" })
    }
}
