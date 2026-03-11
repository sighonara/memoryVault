package org.sightech.memoryvault.auth.service

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JwtServiceTest {

    private val secret = "test-secret-key-that-is-at-least-256-bits-long-for-hs256-signing!!"
    private val service = JwtService(secret, 24)

    @Test
    fun `generateToken creates valid JWT`() {
        val userId = UUID.randomUUID()
        val token = service.generateToken(userId, "test@example.com", "OWNER")
        assertNotNull(token)
    }

    @Test
    fun `validateToken extracts correct claims`() {
        val userId = UUID.randomUUID()
        val token = service.generateToken(userId, "test@example.com", "OWNER")

        val claims = service.validateToken(token)
        assertNotNull(claims)
        assertEquals(userId.toString(), claims["userId"])
        assertEquals("test@example.com", claims["email"])
        assertEquals("OWNER", claims["role"])
    }

    @Test
    fun `validateToken returns null for invalid token`() {
        val result = service.validateToken("invalid.token.here")
        assertNull(result)
    }

    @Test
    fun `validateToken returns null for tampered token`() {
        val userId = UUID.randomUUID()
        val token = service.generateToken(userId, "test@example.com", "OWNER")
        val tampered = token.dropLast(5) + "xxxxx"

        val result = service.validateToken(tampered)
        assertNull(result)
    }
}
