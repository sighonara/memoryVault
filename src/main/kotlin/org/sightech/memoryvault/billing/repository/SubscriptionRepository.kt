package org.sightech.memoryvault.billing.repository

import org.sightech.memoryvault.billing.entity.SubscriptionEntity
import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SubscriptionRepository : JpaRepository<SubscriptionEntity, UUID> {
    fun findByUserId(userId: UUID): SubscriptionEntity?
    fun findByStripeSubscriptionId(stripeSubscriptionId: String): SubscriptionEntity?
    fun findAllByStatus(status: SubscriptionStatus): List<SubscriptionEntity>

    @Query("SELECT s FROM SubscriptionEntity s WHERE s.status = org.sightech.memoryvault.billing.entity.SubscriptionStatus.GRACE_PERIOD AND s.gracePeriodEndsAt <= :cutoff")
    fun findExpiredGracePeriods(cutoff: Instant): List<SubscriptionEntity>
}
