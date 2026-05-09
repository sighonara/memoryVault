package org.sightech.memoryvault.billing.service

import com.stripe.Stripe
import com.stripe.model.Customer
import com.stripe.model.billingportal.Session as PortalSession
import com.stripe.model.checkout.Session as CheckoutSession
import com.stripe.param.CustomerCreateParams
import com.stripe.param.CustomerSearchParams
import com.stripe.param.billingportal.SessionCreateParams as PortalSessionCreateParams
import com.stripe.param.checkout.SessionCreateParams
import jakarta.annotation.PostConstruct
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("!test")
class StripeService(
    @Value("\${memoryvault.billing.stripe-secret-key}") private val secretKey: String,
    @Value("\${memoryvault.billing.stripe-webhook-secret}") val webhookSecret: String,
    @Value("\${memoryvault.billing.stripe-premium-monthly-price-id}") private val premiumMonthlyPriceId: String,
    @Value("\${memoryvault.billing.stripe-premium-annual-price-id}") private val premiumAnnualPriceId: String,
    @Value("\${memoryvault.billing.stripe-archive-monthly-price-id}") private val archiveMonthlyPriceId: String,
    @Value("\${memoryvault.billing.stripe-archive-annual-price-id}") private val archiveAnnualPriceId: String
) : StripeGateway {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun init() {
        Stripe.apiKey = secretKey
        log.info("Stripe client initialized (key prefix={})", secretKey.take(8))
    }

    override fun findOrCreateCustomer(email: String): Customer {
        require(!email.contains("'")) { "Email contains character illegal in Stripe search query" }
        val existing = Customer.search(
            CustomerSearchParams.builder()
                .setQuery("email:'$email'")
                .setLimit(1)
                .build()
        )
        if (existing.data.isNotEmpty()) return existing.data.first()
        return Customer.create(
            CustomerCreateParams.builder()
                .setEmail(email)
                .build()
        )
    }

    override fun createCheckoutSession(
        customerId: String,
        tier: Tier,
        interval: BillingInterval,
        successUrl: String,
        cancelUrl: String
    ): CheckoutSession {
        val priceId = priceIdFor(tier, interval)
        val params = SessionCreateParams.builder()
            .setCustomer(customerId)
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1)
                    .build()
            )
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .build()
        return CheckoutSession.create(params)
    }

    override fun createPortalSession(customerId: String, returnUrl: String): PortalSession {
        val params = PortalSessionCreateParams.builder()
            .setCustomer(customerId)
            .setReturnUrl(returnUrl)
            .build()
        return PortalSession.create(params)
    }

    private fun priceIdFor(tier: Tier, interval: BillingInterval): String = when (tier to interval) {
        Tier.PREMIUM to BillingInterval.MONTHLY -> premiumMonthlyPriceId
        Tier.PREMIUM to BillingInterval.ANNUAL  -> premiumAnnualPriceId
        Tier.ARCHIVE to BillingInterval.MONTHLY -> archiveMonthlyPriceId
        Tier.ARCHIVE to BillingInterval.ANNUAL  -> archiveAnnualPriceId
        else -> throw IllegalArgumentException("No price configured for tier=$tier interval=$interval")
    }
}
