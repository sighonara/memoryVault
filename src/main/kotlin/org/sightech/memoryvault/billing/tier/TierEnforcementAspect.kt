package org.sightech.memoryvault.billing.tier

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Aspect
@Component
class TierEnforcementAspect(private val userRepository: UserRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Around("@annotation(tierGate)")
    fun enforceTier(joinPoint: ProceedingJoinPoint, tierGate: TierGate): Any? {
        val userId = CurrentUser.userId()
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: throw TierRequiredException(tierGate.value)

        if (user.role == UserRole.OWNER) {
            return joinPoint.proceed()
        }

        if (user.tier.ordinal < tierGate.value.ordinal) {
            log.warn("Tier gate blocked userId={} tier={} required={}", userId, user.tier, tierGate.value)
            throw TierRequiredException(tierGate.value)
        }

        return joinPoint.proceed()
    }
}
