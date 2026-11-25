package org.sightech.memoryvault.config

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.net.URL

data class CognitoClaims(val email: String, val role: String)

@Component
@Profile("aws")
class CognitoTokenValidator(
    @Value("\${memoryvault.cognito.region:us-east-1}")
    private val region: String,
    @Value("\${memoryvault.cognito.user-pool-id}")
    private val userPoolId: String,
    @Value("\${memoryvault.cognito.client-id}")
    private val clientId: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val jwksUrl = "https://cognito-idp.$region.amazonaws.com/$userPoolId/.well-known/jwks.json"

    private val jwtProcessor by lazy {
        val jwkSource = JWKSourceBuilder.create<SecurityContext>(URL(jwksUrl))
            .cache(true)
            .build()
        val keySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, jwkSource)
        DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = keySelector
            jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
                JWTClaimsSet.Builder().issuer("https://cognito-idp.$region.amazonaws.com/$userPoolId").build(),
                setOf("sub", "email", "token_use")
            )
        }
    }

    fun validate(token: String): CognitoClaims? {
        return try {
            val claims = jwtProcessor.process(token, null)
            toClaims(claims)
        } catch (e: Exception) {
            log.warn("Cognito token validation failed: {}", e.message)
            null
        }
    }

    internal fun toClaims(claims: JWTClaimsSet): CognitoClaims? {
        if (claims.getStringClaim("token_use") != "id") {
            log.warn("Cognito token rejected: token_use is not 'id'")
            return null
        }
        if (clientId !in claims.audience.orEmpty()) {
            log.warn("Cognito token rejected: audience does not include the configured client")
            return null
        }
        val verified = claims.getClaim("email_verified")
        if (!(verified == true || verified == "true")) {
            log.warn("Cognito token rejected: email is not verified")
            return null
        }
        val email = claims.getStringClaim("email")
        if (email.isNullOrBlank()) {
            log.warn("Cognito token rejected: email claim missing")
            return null
        }
        // role is retained for compatibility only; authorization uses the database role.
        val role = claims.getStringClaim("custom:role") ?: "VIEWER"
        return CognitoClaims(email, role)
    }
}
