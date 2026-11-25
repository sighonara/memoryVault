package org.sightech.memoryvault.config

import com.nimbusds.jwt.JWTClaimsSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CognitoTokenValidatorTest {

    private val clientId = "client-abc"
    private val validator = CognitoTokenValidator("us-east-1", "us-east-1_dummy", clientId)

    private fun claims(
        tokenUse: Any? = "id",
        audience: String? = clientId,
        emailVerified: Any? = true,
        email: String? = "owner@example.com"
    ): JWTClaimsSet {
        val b = JWTClaimsSet.Builder().subject("sub-1")
        if (tokenUse != null) b.claim("token_use", tokenUse)
        if (audience != null) b.audience(audience)
        if (emailVerified != null) b.claim("email_verified", emailVerified)
        if (email != null) b.claim("email", email)
        return b.build()
    }

    @Test
    fun `accepts id token for our client with boolean verified email`() {
        val result = validator.toClaims(claims(emailVerified = true))
        assertNotNull(result)
        assertEquals("owner@example.com", result!!.email)
    }

    @Test
    fun `accepts id token for our client with string verified email`() {
        assertNotNull(validator.toClaims(claims(emailVerified = "true")))
    }

    @Test
    fun `rejects access token`() {
        assertNull(validator.toClaims(claims(tokenUse = "access")))
    }

    @Test
    fun `rejects missing token_use`() {
        assertNull(validator.toClaims(claims(tokenUse = null)))
    }

    @Test
    fun `rejects wrong audience`() {
        assertNull(validator.toClaims(claims(audience = "other-client")))
    }

    @Test
    fun `rejects missing audience`() {
        assertNull(validator.toClaims(claims(audience = null)))
    }

    @Test
    fun `rejects unverified email`() {
        assertNull(validator.toClaims(claims(emailVerified = false)))
        assertNull(validator.toClaims(claims(emailVerified = "false")))
    }

    @Test
    fun `rejects missing email_verified`() {
        assertNull(validator.toClaims(claims(emailVerified = null)))
    }

    @Test
    fun `rejects missing email`() {
        assertNull(validator.toClaims(claims(email = null)))
    }
}
