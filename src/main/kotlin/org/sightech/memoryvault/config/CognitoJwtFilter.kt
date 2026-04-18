package org.sightech.memoryvault.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
                log.warn("Token validation returned null for request {} {}", request.method, request.requestURI)
            }
            if (claims != null) {
                val user = userRepository.findByEmailAndDeletedAtIsNull(claims.email)
                if (user != null) {
                    val authorities = listOf(SimpleGrantedAuthority("ROLE_${claims.role}"))
                    val auth = UsernamePasswordAuthenticationToken(user.id.toString(), null, authorities)
                    SecurityContextHolder.getContext().authentication = auth
                } else {
                    log.warn("Cognito user {} not found in database", claims.email)
                }
            }
        }
        filterChain.doFilter(request, response)
    }
}
