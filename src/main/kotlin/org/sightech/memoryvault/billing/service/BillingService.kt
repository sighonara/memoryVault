package org.sightech.memoryvault.billing.service

import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.entity.SubscriptionEntity
import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class BillingService(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val stripeService: StripeGateway,
    @Value("\${memoryvault.billing.grace-period-days:30}") private val gracePeriodDays: Long = 30
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createCheckoutSession(user: User, tier: Tier, interval: BillingInterval, baseUrl: String): String {
        val customer = stripeService.findOrCreateCustomer(user.email)
        if (user.stripeCustomerId != customer.id) {
            user.stripeCustomerId = customer.id
            userRepository.save(user)
        }
        val session = stripeService.createCheckoutSession(
            customerId = customer.id,
            tier = tier,
            interval = interval,
            successUrl = "$baseUrl/billing/success",
            cancelUrl = "$baseUrl/billing/cancelled"
        )
        log.info("Created checkout session userId={} tier={} interval={}", user.id, tier, interval)
        return session.url
    }

    fun createPortalSession(user: User, baseUrl: String): String {
        val customerId = user.stripeCustomerId
            ?: error("User ${user.id} has no Stripe customer ID")
        val session = stripeService.createPortalSession(customerId, "$baseUrl/billing")
        log.info("Created portal session userId={}", user.id)
        return session.url  // NOT session.returnUrl — that's the input; session.url is the hosted portal URL
    }

    fun getSubscription(user: User): SubscriptionEntity? =
        subscriptionRepository.findByUserId(user.id)

    @Transactional
    fun handleCheckoutCompleted(
        stripeCustomerId: String,
        stripeSubscriptionId: String,
        interval: BillingInterval,
        tier: Tier
    ) {
        val user = userRepository.findByStripeCustomerIdAndDeletedAtIsNull(stripeCustomerId)
            ?: run { log.warn("No user found for stripeCustomerId={}", stripeCustomerId); return }

        val existing = subscriptionRepository.findByUserId(user.id)
        if (existing != null) {
            existing.stripeSubscriptionId = stripeSubscriptionId
            existing.status = SubscriptionStatus.ACTIVE
            existing.gracePeriodEndsAt = null
            subscriptionRepository.save(existing)
        } else {
            subscriptionRepository.save(SubscriptionEntity(
                userId = user.id,
                stripeCustomerId = stripeCustomerId,
                stripeSubscriptionId = stripeSubscriptionId,
                billingInterval = interval,
                status = SubscriptionStatus.ACTIVE
            ))
        }
        user.tier = tier
        userRepository.save(user)
        log.info("Checkout completed userId={} tier={}", user.id, tier)
    }

    @Transactional
    fun handleSubscriptionDeleted(stripeSubscriptionId: String) {
        val sub = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId) ?: return
        val user = userRepository.findByIdAndDeletedAtIsNull(sub.userId) ?: return

        val hadVideoAccess = user.tier == Tier.PREMIUM || user.tier == Tier.ARCHIVE
        user.tier = Tier.FREE
        userRepository.save(user)

        sub.status = SubscriptionStatus.GRACE_PERIOD
        if (hadVideoAccess) sub.gracePeriodEndsAt = Instant.now().plus(gracePeriodDays, ChronoUnit.DAYS)
        subscriptionRepository.save(sub)
        log.info("Subscription deleted userId={} hadVideoAccess={} gracePeriodEndsAt={}", user.id, hadVideoAccess, sub.gracePeriodEndsAt)
    }

    @Transactional
    fun handleInvoicePaid(stripeSubscriptionId: String) {
        val sub = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId) ?: return
        sub.status = SubscriptionStatus.ACTIVE
        subscriptionRepository.save(sub)
        log.info("Invoice paid subscriptionId={}", stripeSubscriptionId)
    }

    @Transactional
    fun handleInvoicePaymentFailed(stripeSubscriptionId: String) {
        val sub = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId) ?: return
        sub.status = SubscriptionStatus.PAST_DUE
        subscriptionRepository.save(sub)
        log.warn("Invoice payment failed subscriptionId={}", stripeSubscriptionId)
    }
}
