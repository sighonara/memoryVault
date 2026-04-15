package org.sightech.memoryvault.auth.service

import org.sightech.memoryvault.auth.dto.LoginResponse
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

@Service
@Profile("local | test")
class AuthService(
    private val userService: UserService,
    private val jwtService: JwtService,
    private val passwordEncoder: BCryptPasswordEncoder
) {

    fun login(email: String, password: String): LoginResponse {
        val user = userService.findByEmail(email)
            ?: throw IllegalArgumentException("Invalid credentials")

        if (user.passwordHash == null || !passwordEncoder.matches(password, user.passwordHash)) {
            throw IllegalArgumentException("Invalid credentials")
        }

        val token = jwtService.generateToken(user.id, user.email, user.role.name)

        return LoginResponse(
            token = token,
            email = user.email,
            displayName = user.displayName,
            role = user.role.name
        )
    }
}
