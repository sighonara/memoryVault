package org.sightech.memoryvault.auth.dto

data class LoginResponse(val token: String, val email: String, val displayName: String, val role: String)
