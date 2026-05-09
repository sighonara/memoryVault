package org.sightech.memoryvault.auth.controller

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.auth.dto.UserProfileDto
import org.sightech.memoryvault.auth.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/me")
class UserController(private val userRepository: UserRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun getMe(): ResponseEntity<UserProfileDto> {
        val userId = CurrentUser.userId()
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: return ResponseEntity.notFound().build()
        log.debug("GET /api/me userId={}", userId)
        return ResponseEntity.ok(UserProfileDto(
            id = user.id,
            email = user.email,
            displayName = user.displayName,
            tier = user.tier,
            role = user.role
        ))
    }
}
