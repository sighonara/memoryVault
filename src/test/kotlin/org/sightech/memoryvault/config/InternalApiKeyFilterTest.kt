package org.sightech.memoryvault.config

import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InternalApiKeyFilterTest {

    private val filterChain = mockk<FilterChain>(relaxed = true)
    private val validKey = "k".repeat(48)
    private val filter = InternalApiKeyFilter(validKey)

    @Test
    fun `blank key fails construction`() {
        assertFailsWith<IllegalStateException> { InternalApiKeyFilter("") }
        assertFailsWith<IllegalStateException> { InternalApiKeyFilter("   ") }
    }

    @Test
    fun `31-char key fails construction`() {
        assertFailsWith<IllegalStateException> { InternalApiKeyFilter("k".repeat(31)) }
    }

    @Test
    fun `32-char and 48-char keys are accepted`() {
        InternalApiKeyFilter("k".repeat(32))
        InternalApiKeyFilter("k".repeat(48))
    }

    @Test
    fun `blank header is rejected`() {
        val request = MockHttpServletRequest("POST", "/api/internal/sync/feeds")
        request.addHeader("X-Internal-Key", "")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        assertEquals(401, response.status)
    }

    @Test
    fun `allows internal request with valid API key`() {
        val request = MockHttpServletRequest("POST", "/api/internal/sync/feeds")
        request.addHeader("X-Internal-Key", validKey)
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        verify { filterChain.doFilter(request, response) }
        assertEquals(200, response.status)
    }

    @Test
    fun `rejects internal request with invalid API key`() {
        val request = MockHttpServletRequest("POST", "/api/internal/sync/feeds")
        request.addHeader("X-Internal-Key", "wrong-key")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        assertEquals(401, response.status)
    }

    @Test
    fun `rejects internal request with missing API key`() {
        val request = MockHttpServletRequest("POST", "/api/internal/sync/feeds")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        assertEquals(401, response.status)
    }

    @Test
    fun `passes through non-internal requests without checking key`() {
        val request = MockHttpServletRequest("GET", "/api/auth/login")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        verify { filterChain.doFilter(request, response) }
        assertEquals(200, response.status)
    }

    @Test
    fun `passes through non-internal requests even with wrong key present`() {
        val request = MockHttpServletRequest("GET", "/api/auth/login")
        request.addHeader("X-Internal-Key", "wrong-key")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        verify { filterChain.doFilter(request, response) }
        assertEquals(200, response.status)
    }
}
