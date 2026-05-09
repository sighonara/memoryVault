package org.sightech.memoryvault.billing.service

import com.stripe.model.Customer
import com.stripe.model.billingportal.Session as PortalSession
import com.stripe.model.checkout.Session as CheckoutSession
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * No-op Stripe gateway for the test profile.
 * All methods throw UnsupportedOperationException — integration tests that
 * need real Stripe calls should stub at the service level with MockK.
 */
@Component
@Profile("test")
class NoopStripeGateway : StripeGateway {
    override fun findOrCreateCustomer(email: String): Customer =
        error("NoopStripeGateway: not available in test profile")

    override fun createCheckoutSession(
        customerId: String,
        tier: Tier,
        interval: BillingInterval,
        successUrl: String,
        cancelUrl: String
    ): CheckoutSession = error("NoopStripeGateway: not available in test profile")

    override fun createPortalSession(customerId: String, returnUrl: String): PortalSession =
        error("NoopStripeGateway: not available in test profile")
}
