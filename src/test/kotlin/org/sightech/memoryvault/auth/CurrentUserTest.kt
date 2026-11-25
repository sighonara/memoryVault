package org.sightech.memoryvault.auth

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CurrentUserTest {

    @AfterEach
    fun cleanup() {
        SecurityContextHolder.clearContext()
        RequestContextHolder.resetRequestAttributes()
    }

    private fun enterRequest() {
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(MockHttpServletRequest()))
    }

    @Test
    fun `returns principal uuid when authenticated`() {
        val id = UUID.randomUUID()
        enterRequest()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(id.toString(), null, emptyList())

        assertEquals(id, CurrentUser.userId())
    }

    @Test
    fun `throws AccessDeniedException in a request without principal`() {
        enterRequest()

        assertFailsWith<AccessDeniedException> { CurrentUser.userId() }
    }

    @Test
    fun `throws AccessDeniedException in a request with a non-uuid principal`() {
        enterRequest()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("anonymousUser", null, emptyList())

        assertFailsWith<AccessDeniedException> { CurrentUser.userId() }
    }

    @Test
    fun `returns SYSTEM_USER_ID outside a request`() {
        assertEquals(CurrentUser.SYSTEM_USER_ID, CurrentUser.userId())
    }
}
