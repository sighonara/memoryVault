package org.sightech.memoryvault.mcp

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.service.BillingService
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class BillingTools(
    private val userRepository: UserRepository,
    private val billingService: BillingService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Tool(description = "Get the current user's tier and subscription status. Use this to check what features are available.")
    fun getMySubscription(): Map<String, Any?> {
        val userId = CurrentUser.userId()
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: return mapOf("error" to "User not found")
        val sub = billingService.getSubscription(user)
        return mapOf(
            "tier" to user.tier.name,
            "subscriptionStatus" to sub?.status?.name,
            "gracePeriodEndsAt" to sub?.gracePeriodEndsAt?.toString()
        )
    }

    @Tool(description = "List all users with their tier and subscription status. Only usable by OWNER role.")
    fun adminListUsers(): Any {
        val caller = userRepository.findByIdAndDeletedAtIsNull(CurrentUser.userId())
        if (caller?.role != UserRole.OWNER) return mapOf("error" to "OWNER role required")
        return userRepository.findAll()
            .filter { it.deletedAt == null }
            .map { u ->
                val sub = billingService.getSubscription(u)
                mapOf("id" to u.id, "email" to u.email, "tier" to u.tier.name, "status" to sub?.status?.name)
            }
    }

    @Tool(description = "Change a user's tier. Provide userId (UUID string) and tier (FREE, PREMIUM, or ARCHIVE). Only usable by OWNER role.")
    fun adminSetUserTier(userId: String, tier: String): Map<String, Any> {
        val caller = userRepository.findByIdAndDeletedAtIsNull(CurrentUser.userId())
        if (caller?.role != UserRole.OWNER) return mapOf("success" to false, "error" to "OWNER role required")
        val user = userRepository.findByIdAndDeletedAtIsNull(
            runCatching { UUID.fromString(userId) }.getOrElse { return mapOf("success" to false, "error" to "Invalid UUID") }
        ) ?: return mapOf("success" to false, "error" to "User not found")
        user.tier = runCatching { Tier.valueOf(tier) }.getOrElse { return mapOf("success" to false, "error" to "Invalid tier: $tier") }
        userRepository.save(user)
        log.info("MCP adminSetUserTier userId={} tier={}", userId, tier)
        return mapOf("success" to true, "email" to user.email, "tier" to user.tier.name)
    }
}
