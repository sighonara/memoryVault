package org.sightech.memoryvault.billing.controller

import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Invoice
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.service.BillingService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/stripe")
class StripeWebhookController(
    private val billingService: BillingService,
    @Value("\${memoryvault.billing.stripe-webhook-secret:disabled}") private val webhookSecret: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/webhook")
    fun handleWebhook(
        @RequestBody payload: ByteArray,
        @RequestHeader("Stripe-Signature") sigHeader: String
    ): ResponseEntity<String> {
        if (webhookSecret == "disabled") {
            log.warn("Stripe webhook secret not configured — rejecting webhook call")
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Webhook not configured")
        }

        val event = try {
            Webhook.constructEvent(String(payload), sigHeader, webhookSecret)
        } catch (e: SignatureVerificationException) {
            log.warn("Stripe webhook signature verification failed: {}", e.message)
            return ResponseEntity.badRequest().body("Invalid signature")
        } catch (e: Exception) {
            log.warn("Stripe webhook parse error: {}", e.message)
            return ResponseEntity.badRequest().body("Parse error")
        }

        log.info("Stripe webhook received type={} id={}", event.type, event.id)

        try {
            dispatchEvent(event)
        } catch (e: Exception) {
            log.warn("Error processing Stripe event type={} id={}: {}", event.type, event.id, e.message)
        }

        return ResponseEntity.ok("OK")
    }

    internal fun dispatchEvent(event: com.stripe.model.Event) {
        when (event.type) {
            "checkout.session.completed" -> {
                val session = event.dataObjectDeserializer.deserializeUnsafe() as Session
                val tierStr = session.metadata?.get("tier") ?: "PREMIUM"
                val intervalStr = session.metadata?.get("interval") ?: "MONTHLY"
                val tier = runCatching { Tier.valueOf(tierStr) }.getOrElse {
                    log.warn("Unknown tier in checkout metadata tierStr={} eventId={}", tierStr, event.id)
                    throw it
                }
                val interval = runCatching { BillingInterval.valueOf(intervalStr) }.getOrElse {
                    log.warn("Unknown interval in checkout metadata intervalStr={} eventId={}", intervalStr, event.id)
                    throw it
                }
                billingService.handleCheckoutCompleted(
                    stripeCustomerId = session.customer,
                    stripeSubscriptionId = session.subscription,
                    interval = interval,
                    tier = tier
                )
            }
            "invoice.paid" -> {
                val invoice = event.dataObjectDeserializer.deserializeUnsafe() as Invoice
                invoice.subscription?.let { billingService.handleInvoicePaid(it) }
            }
            "invoice.payment_failed" -> {
                val invoice = event.dataObjectDeserializer.deserializeUnsafe() as Invoice
                invoice.subscription?.let { billingService.handleInvoicePaymentFailed(it) }
            }
            "customer.subscription.deleted" -> {
                val sub = event.dataObjectDeserializer.deserializeUnsafe() as Subscription
                billingService.handleSubscriptionDeleted(sub.id)
            }
            else -> log.warn("Unhandled Stripe event type={}", event.type)
        }
    }
}
