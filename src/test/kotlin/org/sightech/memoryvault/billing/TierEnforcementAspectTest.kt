package org.sightech.memoryvault.billing

import io.mockk.every
import io.mockk.mockk
import org.aspectj.lang.ProceedingJoinPoint
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.tier.TierEnforcementAspect
import org.sightech.memoryvault.billing.tier.TierGate
import org.sightech.memoryvault.billing.tier.TierRequiredException
import kotlin.test.assertEquals

class TierEnforcementAspectTest {

    private val userRepository = mockk<UserRepository>()
    private val aspect = TierEnforcementAspect(userRepository)

    private fun mockTierGate(required: Tier): TierGate {
        val annotation = mockk<TierGate>()
        every { annotation.value } returns required
        return annotation
    }

    private fun mockJoinPoint(): ProceedingJoinPoint {
        val jp = mockk<ProceedingJoinPoint>()
        every { jp.proceed() } returns "result"
        return jp
    }

    private fun mockUser(role: UserRole, tier: Tier): User {
        val user = mockk<User>()
        every { user.role } returns role
        every { user.tier } returns tier
        return user
    }

    // Use any() in all stubs because CurrentUser.userId() returns SYSTEM_USER_ID
    // in a plain unit test (no SecurityContext). The aspect calls findByIdAndDeletedAtIsNull
    // with that constant UUID, not with a random UUID we create in the test.

    @Test
    fun `OWNER bypasses tier check regardless of tier`() {
        every { userRepository.findByIdAndDeletedAtIsNull(any()) } returns mockUser(UserRole.OWNER, Tier.FREE)
        val result = aspect.enforceTier(mockJoinPoint(), mockTierGate(Tier.ARCHIVE))
        assertEquals("result", result)
    }

    @Test
    fun `ARCHIVE user passes ARCHIVE gate`() {
        every { userRepository.findByIdAndDeletedAtIsNull(any()) } returns mockUser(UserRole.VIEWER, Tier.ARCHIVE)
        val result = aspect.enforceTier(mockJoinPoint(), mockTierGate(Tier.ARCHIVE))
        assertEquals("result", result)
    }

    @Test
    fun `FREE user fails ARCHIVE gate`() {
        every { userRepository.findByIdAndDeletedAtIsNull(any()) } returns mockUser(UserRole.VIEWER, Tier.FREE)
        assertThrows<TierRequiredException> {
            aspect.enforceTier(mockJoinPoint(), mockTierGate(Tier.ARCHIVE))
        }
    }

    @Test
    fun `PREMIUM user fails ARCHIVE gate`() {
        every { userRepository.findByIdAndDeletedAtIsNull(any()) } returns mockUser(UserRole.VIEWER, Tier.PREMIUM)
        assertThrows<TierRequiredException> {
            aspect.enforceTier(mockJoinPoint(), mockTierGate(Tier.ARCHIVE))
        }
    }

    @Test
    fun `FREE user passes FREE gate`() {
        every { userRepository.findByIdAndDeletedAtIsNull(any()) } returns mockUser(UserRole.VIEWER, Tier.FREE)
        val result = aspect.enforceTier(mockJoinPoint(), mockTierGate(Tier.FREE))
        assertEquals("result", result)
    }
}
