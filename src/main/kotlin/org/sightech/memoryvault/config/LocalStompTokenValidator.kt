package org.sightech.memoryvault.config

import org.sightech.memoryvault.auth.service.JwtService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.security.Principal

@Component
@Profile("local | test")
class LocalStompTokenValidator(
    private val jwtService: JwtService
) : StompTokenValidator {
    override fun validate(token: String): Principal? {
        val claims = jwtService.validateToken(token) ?: return null
        val userId = claims["userId"] ?: return null
        return Principal { userId }
    }
}
