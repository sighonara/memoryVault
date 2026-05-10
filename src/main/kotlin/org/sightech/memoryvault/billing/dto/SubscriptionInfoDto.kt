package org.sightech.memoryvault.billing.dto

import java.time.Instant

data class SubscriptionInfoDto(
    val tier: String,
    val status: String,
    val billingInterval: String,
    val gracePeriodEndsAt: Instant?
)
