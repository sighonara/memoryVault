package org.sightech.memoryvault.auth.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(name = "password_hash")
    var passwordHash: String? = null,

    @Column(name = "display_name", nullable = false)
    var displayName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val role: UserRole = UserRole.OWNER,

    @Column(name = "view_mode", nullable = false, length = 10)
    var viewMode: String = "LIST",

    @Column(name = "sort_order", nullable = false, length = 20)
    var sortOrder: String = "NEWEST_FIRST",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    @Version
    val version: Long = 0
)
