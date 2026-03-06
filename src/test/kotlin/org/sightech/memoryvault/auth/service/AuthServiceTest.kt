package org.sightech.memoryvault.auth.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.UUID
import kotlin.test.assertEquals

class AuthServiceTest {

    private val userService = mockk<UserService>()
    private val jwtService = mockk<JwtService>()
    private val passwordEncoder = BCryptPasswordEncoder()

    private val authService = AuthService(userService, jwtService, passwordEncoder)

    @Test
    fun `login returns token for valid credentials`() {
        val userId = UUID.randomUUID()
        val hashedPassword = passwordEncoder.encode("correct-password")
        val user = User(
            id = userId,
            email = "test@example.com",
            passwordHash = hashedPassword,
            displayName = "Test User",
            role = UserRole.OWNER
        )

        every { userService.findByEmail("test@example.com") } returns user
        every { jwtService.generateToken(userId, "test@example.com", "OWNER") } returns "mock-jwt-token"

        val response = authService.login("test@example.com", "correct-password")

        assertEquals("mock-jwt-token", response.token)
        assertEquals("test@example.com", response.email)
        assertEquals("Test User", response.displayName)
        assertEquals("OWNER", response.role)
    }

    @Test
    fun `login throws for invalid email`() {
        every { userService.findByEmail("unknown@example.com") } returns null

        assertThrows<IllegalArgumentException> {
            authService.login("unknown@example.com", "any-password")
        }
    }

    @Test
    fun `login throws for wrong password`() {
        val hashedPassword = passwordEncoder.encode("correct-password")
        val user = User(
            id = UUID.randomUUID(),
            email = "test@example.com",
            passwordHash = hashedPassword,
            displayName = "Test User",
            role = UserRole.OWNER
        )

        every { userService.findByEmail("test@example.com") } returns user

        assertThrows<IllegalArgumentException> {
            authService.login("test@example.com", "wrong-password")
        }
    }
}
