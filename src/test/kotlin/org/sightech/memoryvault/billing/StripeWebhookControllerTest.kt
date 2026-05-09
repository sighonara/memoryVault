package org.sightech.memoryvault.billing

import com.stripe.model.Event
import com.stripe.model.EventDataObjectDeserializer
import com.stripe.model.Invoice
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session as CheckoutSession
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.billing.controller.StripeWebhookController
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.service.BillingService
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class StripeWebhookControllerTest {

    private val billingService = mockk<BillingService>(relaxed = true)
    private val controller = StripeWebhookController(billingService, "whsec_test")

    @Test
    fun `invalid signature returns 400`() {
        val response = controller.handleWebhook("bad-payload".toByteArray(), "bad-sig")
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `malformed payload returns 400`() {
        val response = controller.handleWebhook(ByteArray(0), "t=123,v1=abc")
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `invalid signature never invokes billing service`() {
        controller.handleWebhook("bad-payload".toByteArray(), "bad-sig")
        verify(exactly = 0) { billingService.handleCheckoutCompleted(any(), any(), any(), any()) }
        verify(exactly = 0) { billingService.handleSubscriptionDeleted(any()) }
        verify(exactly = 0) { billingService.handleInvoicePaid(any()) }
        verify(exactly = 0) { billingService.handleInvoicePaymentFailed(any()) }
    }

    @Test
    fun `disabled webhook secret returns 503`() {
        val disabledController = StripeWebhookController(billingService, "disabled")
        val response = disabledController.handleWebhook("payload".toByteArray(), "sig")
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
    }

    // Helper to build a mock Event with a given type and data object
    private fun mockEvent(type: String, dataObject: com.stripe.model.StripeObject): Event {
        val deserializer = mockk<EventDataObjectDeserializer>()
        every { deserializer.deserializeUnsafe() } returns dataObject
        val event = mockk<Event>()
        every { event.type } returns type
        every { event.id } returns "evt_test"
        every { event.dataObjectDeserializer } returns deserializer
        return event
    }

    @Test
    fun `checkout session completed dispatches to billing service`() {
        val session = mockk<CheckoutSession>()
        every { session.customer } returns "cus_123"
        every { session.subscription } returns "sub_456"
        every { session.metadata } returns mapOf("tier" to "PREMIUM", "interval" to "MONTHLY")
        val event = mockEvent("checkout.session.completed", session)

        controller.dispatchEvent(event)

        verify { billingService.handleCheckoutCompleted("cus_123", "sub_456", BillingInterval.MONTHLY, Tier.PREMIUM) }
    }

    @Test
    fun `invoice paid dispatches to billing service`() {
        val invoice = mockk<Invoice>()
        every { invoice.subscription } returns "sub_456"
        val event = mockEvent("invoice.paid", invoice)

        controller.dispatchEvent(event)

        verify { billingService.handleInvoicePaid("sub_456") }
    }

    @Test
    fun `invoice payment failed dispatches to billing service`() {
        val invoice = mockk<Invoice>()
        every { invoice.subscription } returns "sub_456"
        val event = mockEvent("invoice.payment_failed", invoice)

        controller.dispatchEvent(event)

        verify { billingService.handleInvoicePaymentFailed("sub_456") }
    }

    @Test
    fun `subscription deleted dispatches to billing service`() {
        val sub = mockk<Subscription>()
        every { sub.id } returns "sub_456"
        val event = mockEvent("customer.subscription.deleted", sub)

        controller.dispatchEvent(event)

        verify { billingService.handleSubscriptionDeleted("sub_456") }
    }
}
