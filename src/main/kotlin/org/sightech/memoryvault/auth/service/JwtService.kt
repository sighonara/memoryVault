package org.sightech.memoryvault.auth.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${memoryvault.jwt.secret}") private val secret: String,
    @Value("\${memoryvault.jwt.expiration-hours}") private val expirationHours: Long
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    fun generateToken(userId: UUID, email: String, role: String): String {
        val now = Date()
        val expiry = Date(now.time + expirationHours * 3600 * 1000)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("userId", userId.toString())
            .claim("email", email)
            .claim("role", role)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Map<String, String>? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload

            mapOf(
                "userId" to (claims["userId"] as String),
                "email" to (claims["email"] as String),
                "role" to (claims["role"] as String)
            )
        } catch (e: Exception) {
            log.warn("JWT validation failed: {}", e.message)
            null
        }
    }
}
