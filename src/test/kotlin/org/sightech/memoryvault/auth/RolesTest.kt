package org.sightech.memoryvault.auth

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RolesTest {

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun `no authentication outside a web request is treated as the trusted owner channel`() {
        SecurityContextHolder.clearContext()
        RequestContextHolder.resetRequestAttributes()

        assertTrue(Roles.isOwner())
    }

    @Test
    fun `no authentication inside a web request is not owner`() {
        SecurityContextHolder.clearContext()
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(MockHttpServletRequest()))

        assertFalse(Roles.isOwner())
    }

    @Test
    fun `authenticated owner is owner and authenticated viewer is not`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("u", null, listOf(SimpleGrantedAuthority("ROLE_OWNER")))
        assertTrue(Roles.isOwner())

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("u", null, listOf(SimpleGrantedAuthority("ROLE_VIEWER")))
        assertFalse(Roles.isOwner())
    }
}
