package org.sightech.memoryvault.auth.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.repository.UserRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val service = UserService(userRepository)

    @Test
    fun `findByEmail returns user when found`() {
        val user = User(email = "test@example.com", displayName = "Test")
        every { userRepository.findByEmailAndDeletedAtIsNull("test@example.com") } returns user

        val result = service.findByEmail("test@example.com")
        assertNotNull(result)
        assertEquals("test@example.com", result.email)
    }

    @Test
    fun `findByEmail returns null when not found`() {
        every { userRepository.findByEmailAndDeletedAtIsNull("missing@example.com") } returns null

        val result = service.findByEmail("missing@example.com")
        assertNull(result)
    }

    @Test
    fun `findById returns user`() {
        val id = UUID.randomUUID()
        val user = User(id = id, email = "test@example.com", displayName = "Test")
        every { userRepository.findById(id) } returns java.util.Optional.of(user)

        val result = service.findById(id)
        assertNotNull(result)
        assertEquals(id, result.id)
    }
}
