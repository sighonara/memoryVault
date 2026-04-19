package org.sightech.memoryvault.auth.controller

import org.sightech.memoryvault.auth.dto.LoginRequest
import org.sightech.memoryvault.auth.dto.LoginResponse
import org.sightech.memoryvault.auth.service.AuthService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
@Profile("local | test")
class AuthController(private val authService: AuthService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        return try {
            val response = authService.login(request.email, request.password)
            ResponseEntity.ok(response)
        } catch (e: IllegalArgumentException) {
            log.warn("Login attempt failed for email={}", request.email)
            ResponseEntity.status(401).build()
        }
    }
}
