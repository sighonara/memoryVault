package org.sightech.memoryvault.billing.controller

import jakarta.servlet.http.HttpServletRequest
import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.entity.SubscriptionEntity
import org.sightech.memoryvault.billing.service.BillingService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class CheckoutRequest(val tier: String, val interval: String)
data class CheckoutResponse(val url: String)
data class PortalResponse(val url: String)

@RestController
@RequestMapping("/api/billing")
class BillingController(
    private val billingService: BillingService,
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/checkout")
    fun checkout(@RequestBody req: CheckoutRequest, httpRequest: HttpServletRequest): ResponseEntity<CheckoutResponse> {
        val user = userRepository.findByIdAndDeletedAtIsNull(CurrentUser.userId())
            ?: return ResponseEntity.notFound().build()
        val tier = runCatching { Tier.valueOf(req.tier) }.getOrElse {
            return ResponseEntity.badRequest().build()
        }
        val interval = runCatching { BillingInterval.valueOf(req.interval) }.getOrElse {
            return ResponseEntity.badRequest().build()
        }
        if (tier == Tier.FREE) return ResponseEntity.badRequest().build()
        val baseUrl = "${httpRequest.scheme}://${httpRequest.serverName}"
        val url = billingService.createCheckoutSession(user, tier, interval, baseUrl)
        log.info("Checkout initiated userId={} tier={} interval={}", user.id, tier, interval)
        return ResponseEntity.ok(CheckoutResponse(url))
    }

    @PostMapping("/portal")
    fun portal(httpRequest: HttpServletRequest): ResponseEntity<PortalResponse> {
        val user = userRepository.findByIdAndDeletedAtIsNull(CurrentUser.userId())
            ?: return ResponseEntity.notFound().build()
        val baseUrl = "${httpRequest.scheme}://${httpRequest.serverName}"
        val url = billingService.createPortalSession(user, baseUrl)
        return ResponseEntity.ok(PortalResponse(url))
    }

    @GetMapping("/subscription")
    fun subscription(): ResponseEntity<*> {
        val user = userRepository.findByIdAndDeletedAtIsNull(CurrentUser.userId())
            ?: return ResponseEntity.notFound().build<Void>()
        val sub: SubscriptionEntity? = billingService.getSubscription(user)
        return if (sub != null) ResponseEntity.ok(sub) else ResponseEntity.noContent().build<Void>()
    }
}
