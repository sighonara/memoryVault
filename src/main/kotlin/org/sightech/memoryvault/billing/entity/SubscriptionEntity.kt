package org.sightech.memoryvault.billing.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "subscriptions")
class SubscriptionEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "stripe_customer_id", nullable = false)
    val stripeCustomerId: String,

    @Column(name = "stripe_subscription_id", nullable = false, unique = true)
    val stripeSubscriptionId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 10)
    val billingInterval: BillingInterval,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SubscriptionStatus,

    @Column(name = "grace_period_ends_at")
    var gracePeriodEndsAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
