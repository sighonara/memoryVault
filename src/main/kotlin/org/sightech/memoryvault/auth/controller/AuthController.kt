package org.sightech.memoryvault.auth.controller

import org.sightech.memoryvault.auth.dto.LoginRequest
import org.sightech.memoryvault.auth.dto.LoginResponse
import org.sightech.memoryvault.auth.service.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// TODO(phase-9d): Re-add @Profile("local | test") once Cognito auth ships.
// Profile gate was temporarily removed so the AWS-hosted deploy could use /api/auth/login
// (needed by smoke tests and manual login). Phase 9D swaps to Cognito in prod; at that
// point this controller must be gated again. See docs/plans/2026-04-05-phase-9d-cognito-auth-plan.md.
@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        return try {
            val response = authService.login(request.email, request.password)
            ResponseEntity.ok(response)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(401).build()
        }
    }
}
