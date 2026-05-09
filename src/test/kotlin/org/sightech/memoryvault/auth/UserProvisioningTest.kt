package org.sightech.memoryvault.auth

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.config.CognitoClaims
import org.sightech.memoryvault.config.CognitoJwtFilter
import org.sightech.memoryvault.config.CognitoTokenValidator
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals

class UserProvisioningTest {

    private val tokenValidator = mockk<CognitoTokenValidator>()
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val filterChain = mockk<FilterChain>(relaxed = true)
    private lateinit var filter: CognitoJwtFilter

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        filter = CognitoJwtFilter(tokenValidator, userRepository)
    }

    @Test
    fun `first login auto-provisions user with FREE tier and VIEWER role`() {
        val email = "newuser@example.com"
        every { tokenValidator.validate(any()) } returns CognitoClaims(email, "VIEWER")
        every { userRepository.findByEmailAndDeletedAtIsNull(email) } returns null

        val userSlot = slot<User>()
        every { userRepository.save(capture(userSlot)) } answers { userSlot.captured }

        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer sometoken")

        filter.doFilter(request, MockHttpServletResponse(), filterChain)

        verify { userRepository.save(any()) }
        assertEquals(email, userSlot.captured.email)
        assertEquals(Tier.FREE, userSlot.captured.tier)
        assertEquals(UserRole.VIEWER, userSlot.captured.role)
    }

    @Test
    fun `existing user is not re-provisioned`() {
        val email = "existing@example.com"
        val existing = User(email = email, displayName = "Existing", role = UserRole.VIEWER)
        every { tokenValidator.validate(any()) } returns CognitoClaims(email, "VIEWER")
        every { userRepository.findByEmailAndDeletedAtIsNull(email) } returns existing

        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer sometoken")

        filter.doFilter(request, MockHttpServletResponse(), filterChain)

        verify(exactly = 0) { userRepository.save(any()) }
    }
}
