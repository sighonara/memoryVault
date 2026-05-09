package org.sightech.memoryvault.billing

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.billing.controller.StripeWebhookController
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
}
