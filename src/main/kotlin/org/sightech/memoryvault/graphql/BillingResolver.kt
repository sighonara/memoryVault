package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.auth.dto.UserProfileDto
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.dto.SubscriptionInfoDto
import org.sightech.memoryvault.billing.service.BillingService
import org.slf4j.LoggerFactory
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

@Controller
class BillingResolver(
    private val userRepository: UserRepository,
    private val billingService: BillingService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @QueryMapping
    fun me(): UserProfileDto {
        val userId = CurrentUser.userId()
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: error("Authenticated user not found: $userId")
        log.debug("GraphQL me userId={}", userId)
        return UserProfileDto(
            id = user.id,
            email = user.email,
            displayName = user.displayName,
            tier = user.tier,
            role = user.role
        )
    }

    @QueryMapping
    fun subscription(): SubscriptionInfoDto? {
        val userId = CurrentUser.userId()
        val user = userRepository.findByIdAndDeletedAtIsNull(userId) ?: return null
        val sub = billingService.getSubscription(user) ?: return null
        return SubscriptionInfoDto(
            tier = user.tier.name,
            status = sub.status.name,
            billingInterval = sub.billingInterval.name,
            gracePeriodEndsAt = sub.gracePeriodEndsAt
        )
    }
}
