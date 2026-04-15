package org.sightech.memoryvault.config

import org.sightech.memoryvault.auth.repository.UserRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.security.Principal

@Component
@Profile("aws")
class CognitoStompTokenValidator(
    private val cognitoTokenValidator: CognitoTokenValidator,
    private val userRepository: UserRepository
) : StompTokenValidator {
    override fun validate(token: String): Principal? {
        val claims = cognitoTokenValidator.validate(token) ?: return null
        val user = userRepository.findByEmailAndDeletedAtIsNull(claims.email) ?: return null
        return Principal { user.id.toString() }
    }
}
