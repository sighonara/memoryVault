package org.sightech.memoryvault.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
@Profile("aws")
class CognitoJwtFilter(
    private val tokenValidator: CognitoTokenValidator,
    private val userRepository: UserRepository
) : AppAuthenticationFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.substring(7)
            val claims = tokenValidator.validate(token)
            if (claims == null) {
                log.warn("Token validation returned null for {} {}", request.method, request.requestURI)
            }
            if (claims != null) {
                val user = userRepository.findByEmailAndDeletedAtIsNull(claims.email)
                    ?: provisionUser(claims.email, claims.role)
                val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
                val auth = UsernamePasswordAuthenticationToken(user.id.toString(), null, authorities)
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun provisionUser(email: String, roleHint: String): User {
        val role = runCatching { UserRole.valueOf(roleHint) }.getOrDefault(UserRole.VIEWER)
        val user = userRepository.save(User(
            email = email,
            displayName = email.substringBefore("@"),
            role = role
        ))
        log.info("Auto-provisioned user email={} role={}", email, role)
        return user
    }
}
