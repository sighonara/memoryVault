package org.sightech.memoryvault.config

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import jakarta.servlet.DispatcherType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: AppAuthenticationFilter,
    private val internalApiKeyFilterProvider: ObjectProvider<InternalApiKeyFilter>,
    @Value("\${memoryvault.cors.allowed-origins}") private val allowedOrigins: String
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/config").permitAll()
                    // Auth for /api/internal/** is enforced by InternalApiKeyFilter (aws profile only),
                    // not by JWT. Permit here so the chain reaches the filter instead of short-circuiting.
                    .requestMatchers("/api/internal/**").permitAll()
                    .requestMatchers("/graphiql/**").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    .requestMatchers("/", "/index.html", "/*.js", "/*.css", "/*.ico", "/*.png", "/*.svg", "/*.woff2", "/assets/**", "/media/**").permitAll()
                    // Angular client-side routes — must be permitted so requests fall through to the SPA
                    // index.html fallback (SpaWebConfig 404 handler). Auth is enforced by the Angular authGuard.
                    .requestMatchers("/login", "/reader", "/bookmarks/**", "/youtube/**", "/admin/**", "/search/**").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint { request, response, authException ->
                    log.warn("AuthEntryPoint fired: {} {} auth={} exception={}",
                        request.method, request.requestURI,
                        org.springframework.security.core.context.SecurityContextHolder.getContext().authentication != null,
                        authException.message)
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required")
                }
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        internalApiKeyFilterProvider.ifAvailable { filter ->
            http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter::class.java)
        }

        return http.build()
    }

    @Bean
    fun passwordEncoder() = BCryptPasswordEncoder()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = allowedOrigins.split(",").map { it.trim() }
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
