package org.sightech.memoryvault.billing.dto

import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import java.time.Instant
import java.util.UUID

data class SubscriptionDto(
    val id: UUID,
    val status: SubscriptionStatus,
    val billingInterval: BillingInterval,
    val gracePeriodEndsAt: Instant?
)
