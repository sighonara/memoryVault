package org.sightech.memoryvault.billing.service

import com.stripe.model.Customer
import com.stripe.model.billingportal.Session as PortalSession
import com.stripe.model.checkout.Session as CheckoutSession
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.billing.entity.BillingInterval

/**
 * Abstraction over the Stripe API, allowing a no-op implementation
 * in the test profile where StripeService (@Profile("!test")) is absent.
 */
interface StripeGateway {
    fun findOrCreateCustomer(email: String): Customer
    fun createCheckoutSession(
        customerId: String,
        tier: Tier,
        interval: BillingInterval,
        successUrl: String,
        cancelUrl: String
    ): CheckoutSession
    fun createPortalSession(customerId: String, returnUrl: String): PortalSession
}
