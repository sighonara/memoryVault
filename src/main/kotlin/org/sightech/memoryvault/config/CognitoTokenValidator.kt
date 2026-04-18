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
    private val userPoolId: String
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
            val email = claims.getStringClaim("email") ?: return null
            val role = claims.getStringClaim("custom:role") ?: "VIEWER"
            CognitoClaims(email, role)
        } catch (e: Exception) {
            log.warn("Cognito token validation failed: {}", e.message)
            null
        }
    }
}
