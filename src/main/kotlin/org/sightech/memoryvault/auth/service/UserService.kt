package org.sightech.memoryvault.auth.service

import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.repository.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserService(private val userRepository: UserRepository) {

    fun findByEmail(email: String): User? =
        userRepository.findByEmailAndDeletedAtIsNull(email)

    fun findById(id: UUID): User? =
        userRepository.findById(id).orElse(null)
}
