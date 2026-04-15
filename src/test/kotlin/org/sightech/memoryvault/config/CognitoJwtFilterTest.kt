package org.sightech.memoryvault.config

import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CognitoJwtFilterTest {

    private val userRepository = mockk<UserRepository>()
    private val tokenValidator = mockk<CognitoTokenValidator>()
    private val filterChain = mockk<FilterChain>(relaxed = true)
    private lateinit var filter: CognitoJwtFilter

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        filter = CognitoJwtFilter(tokenValidator, userRepository)
    }

    @Test
    fun `sets security context when token is valid`() {
        val userId = UUID.randomUUID()
        val user = mockk<User> {
            every { id } returns userId
            every { role } returns UserRole.OWNER
        }

        every { tokenValidator.validate("valid-token") } returns CognitoClaims("test@example.com", "OWNER")
        every { userRepository.findByEmailAndDeletedAtIsNull("test@example.com") } returns user

        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer valid-token")

        filter.doFilter(request, MockHttpServletResponse(), filterChain)

        val auth = SecurityContextHolder.getContext().authentication!!
        assertEquals(userId.toString(), auth.principal)
    }

    @Test
    fun `skips auth for requests without Authorization header`() {
        val request = MockHttpServletRequest()
        filter.doFilter(request, MockHttpServletResponse(), filterChain)
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `skips auth when token validation fails`() {
        every { tokenValidator.validate("bad-token") } returns null

        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer bad-token")

        filter.doFilter(request, MockHttpServletResponse(), filterChain)
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `skips auth when user not found or soft-deleted`() {
        every { tokenValidator.validate("valid-token") } returns CognitoClaims("ghost@example.com", "OWNER")
        every { userRepository.findByEmailAndDeletedAtIsNull("ghost@example.com") } returns null

        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer valid-token")

        filter.doFilter(request, MockHttpServletResponse(), filterChain)
        assertNull(SecurityContextHolder.getContext().authentication)
    }
}
