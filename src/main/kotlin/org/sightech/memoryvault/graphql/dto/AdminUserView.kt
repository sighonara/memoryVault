package org.sightech.memoryvault.graphql.dto

import java.time.Instant
import java.util.UUID

data class AdminUserView(
    val id: UUID,
    val email: String,
    val displayName: String,
    val tier: String,
    val role: String,
    val subscriptionStatus: String?,
    val createdAt: Instant
)
