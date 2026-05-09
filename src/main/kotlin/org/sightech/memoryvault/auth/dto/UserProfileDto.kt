package org.sightech.memoryvault.auth.dto

import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.UserRole
import java.util.UUID

data class UserProfileDto(
    val id: UUID,
    val email: String,
    val displayName: String,
    val tier: Tier,
    val role: UserRole
)
