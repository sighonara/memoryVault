package org.sightech.memoryvault.config

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.repository.UserRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CognitoStompTokenValidatorTest {

    private val cognitoTokenValidator = mockk<CognitoTokenValidator>()
    private val userRepository = mockk<UserRepository>()
    private val validator = CognitoStompTokenValidator(cognitoTokenValidator, userRepository)

    @Test
    fun `returns principal with user ID when token valid`() {
        val userId = UUID.randomUUID()
        every { cognitoTokenValidator.validate("token") } returns CognitoClaims("test@example.com", "OWNER")
        every { userRepository.findByEmailAndDeletedAtIsNull("test@example.com") } returns mockk<User> {
            every { id } returns userId
        }
        assertEquals(userId.toString(), validator.validate("token")?.name)
    }

    @Test
    fun `returns null when token invalid`() {
        every { cognitoTokenValidator.validate("bad") } returns null
        assertNull(validator.validate("bad"))
    }

    @Test
    fun `returns null when user not in database or soft-deleted`() {
        every { cognitoTokenValidator.validate("token") } returns CognitoClaims("unknown@example.com", "VIEWER")
        every { userRepository.findByEmailAndDeletedAtIsNull("unknown@example.com") } returns null
        assertNull(validator.validate("token"))
    }
}
