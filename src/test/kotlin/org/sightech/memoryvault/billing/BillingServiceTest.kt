package org.sightech.memoryvault.billing

import com.stripe.model.Customer
import com.stripe.model.checkout.Session
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.entity.SubscriptionEntity
import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.sightech.memoryvault.billing.service.BillingService
import org.sightech.memoryvault.billing.service.StripeGateway
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BillingServiceTest {

    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val stripeService = mockk<StripeGateway>()
    private val service = BillingService(userRepository, subscriptionRepository, stripeService)

    private fun user(tier: Tier = Tier.FREE, stripeId: String? = null): User {
        val u = mockk<User>(relaxed = true)
        every { u.id } returns UUID.randomUUID()
        every { u.email } returns "test@example.com"
        every { u.tier } returns tier
        every { u.stripeCustomerId } returns stripeId
        return u
    }

    private fun concreteUser(tier: Tier = Tier.FREE, stripeId: String? = null): User {
        val u = User(email = "test@example.com", displayName = "Test", role = UserRole.VIEWER)
        u.tier = tier
        u.stripeCustomerId = stripeId
        return u
    }

    @Test
    fun `createCheckoutSession finds or creates Stripe customer and returns URL`() {
        // user already has the stripe customer id so the save branch is skipped
        val u = user(stripeId = "cus_123")
        val customer = mockk<Customer>()
        every { customer.id } returns "cus_123"
        every { stripeService.findOrCreateCustomer(any()) } returns customer

        val session = mockk<Session>()
        every { session.url } returns "https://checkout.stripe.com/pay/cs_test_123"
        every { stripeService.createCheckoutSession(any(), any(), any(), any(), any()) } returns session

        val url = service.createCheckoutSession(u, Tier.PREMIUM, BillingInterval.MONTHLY, "https://mem-vault.com")
        assertEquals("https://checkout.stripe.com/pay/cs_test_123", url)
    }

    @Test
    fun `getSubscription returns null when user has no subscription`() {
        val u = user()
        every { subscriptionRepository.findByUserId(u.id) } returns null
        val result = service.getSubscription(u)
        assertEquals(null, result)
    }

    @Test
    fun `getSubscription returns subscription when present`() {
        val u = user(tier = Tier.PREMIUM, stripeId = "cus_123")
        val sub = SubscriptionEntity(
            userId = u.id,
            stripeCustomerId = "cus_123",
            stripeSubscriptionId = "sub_123",
            billingInterval = BillingInterval.MONTHLY,
            status = SubscriptionStatus.ACTIVE
        )
        every { subscriptionRepository.findByUserId(u.id) } returns sub
        val result = service.getSubscription(u)
        assertNotNull(result)
        assertEquals(SubscriptionStatus.ACTIVE, result.status)
    }

    @Test
    fun `handleCheckoutCompleted creates subscription and upgrades tier`() {
        val user = concreteUser(tier = Tier.FREE, stripeId = "cus_123")
        every { userRepository.findByStripeCustomerIdAndDeletedAtIsNull("cus_123") } returns user
        every { subscriptionRepository.findByUserId(user.id) } returns null
        every { subscriptionRepository.save(any()) } returnsArgument 0
        every { userRepository.save(any()) } returnsArgument 0

        service.handleCheckoutCompleted("cus_123", "sub_456", BillingInterval.MONTHLY, Tier.PREMIUM)

        assertEquals(Tier.PREMIUM, user.tier)
        verify { subscriptionRepository.save(match { it.status == SubscriptionStatus.ACTIVE }) }
    }

    @Test
    fun `handleSubscriptionDeleted downgrades tier and sets grace period for video user`() {
        val user = concreteUser(tier = Tier.PREMIUM, stripeId = "cus_123")
        val sub = SubscriptionEntity(
            userId = user.id,
            stripeCustomerId = "cus_123",
            stripeSubscriptionId = "sub_456",
            billingInterval = BillingInterval.MONTHLY,
            status = SubscriptionStatus.ACTIVE
        )
        every { subscriptionRepository.findByStripeSubscriptionId("sub_456") } returns sub
        every { userRepository.findByIdAndDeletedAtIsNull(user.id) } returns user
        every { userRepository.save(any()) } returnsArgument 0
        every { subscriptionRepository.save(any()) } returnsArgument 0

        service.handleSubscriptionDeleted("sub_456")

        assertEquals(Tier.FREE, user.tier)
        assertEquals(SubscriptionStatus.GRACE_PERIOD, sub.status)
        assertNotNull(sub.gracePeriodEndsAt)
    }

    @Test
    fun `handleSubscriptionDeleted does not set grace period for FREE user`() {
        val user = concreteUser(tier = Tier.FREE, stripeId = "cus_123")
        val sub = SubscriptionEntity(
            userId = user.id,
            stripeCustomerId = "cus_123",
            stripeSubscriptionId = "sub_456",
            billingInterval = BillingInterval.MONTHLY,
            status = SubscriptionStatus.ACTIVE
        )
        every { subscriptionRepository.findByStripeSubscriptionId("sub_456") } returns sub
        every { userRepository.findByIdAndDeletedAtIsNull(user.id) } returns user
        every { userRepository.save(any()) } returnsArgument 0
        every { subscriptionRepository.save(any()) } returnsArgument 0

        service.handleSubscriptionDeleted("sub_456")

        assertEquals(SubscriptionStatus.GRACE_PERIOD, sub.status)
        assertNull(sub.gracePeriodEndsAt)
    }
}
