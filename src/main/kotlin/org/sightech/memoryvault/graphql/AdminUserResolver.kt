package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.service.BillingService
import org.slf4j.LoggerFactory
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.time.Instant
import java.util.UUID

data class AdminUserView(
    val id: UUID,
    val email: String,
    val displayName: String,
    val tier: String,
    val role: String,
    val subscriptionStatus: String?,
    val createdAt: Instant
)

@Controller
class AdminUserResolver(
    private val userRepository: UserRepository,
    private val billingService: BillingService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @QueryMapping
    fun adminUsers(@Argument limit: Int?, @Argument offset: Int?): List<AdminUserView> {
        val users = userRepository.findAll()
            .filter { it.deletedAt == null }
            .drop(offset ?: 0)
            .take(limit ?: 50)
        return users.map { user ->
            val sub = billingService.getSubscription(user)
            AdminUserView(
                id = user.id,
                email = user.email,
                displayName = user.displayName,
                tier = user.tier.name,
                role = user.role.name,
                subscriptionStatus = sub?.status?.name,
                createdAt = user.createdAt
            )
        }
    }

    @MutationMapping
    fun adminSetUserTier(@Argument userId: UUID, @Argument tier: String): Boolean {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId) ?: return false
        user.tier = runCatching { Tier.valueOf(tier) }.getOrElse { return false }
        userRepository.save(user)
        log.info("Admin set tier userId={} tier={}", userId, tier)
        return true
    }

    @MutationMapping
    fun adminSetUserActive(@Argument userId: UUID, @Argument active: Boolean): Boolean {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId) ?: return false
        user.deletedAt = if (active) null else Instant.now()
        userRepository.save(user)
        log.info("Admin set active userId={} active={}", userId, active)
        return true
    }
}
