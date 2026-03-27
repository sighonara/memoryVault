package org.sightech.memoryvault.auth.entity

// TODO: Phase 7 stub — OAuth provider linking
// Uncomment and implement when OAuth integration is needed.
// Will also need spring-boot-starter-oauth2-client dependency in build.gradle.kts.

// import jakarta.persistence.*
// import java.time.Instant
// import java.util.UUID
//
// @Entity
// @Table(name = "user_auth_providers")
// class UserAuthProvider(
//     @Id
//     val id: UUID = UUID.randomUUID(),
//
//     @Column(name = "user_id", nullable = false)
//     val userId: UUID,
//
//     @Column(nullable = false, length = 50)
//     val provider: String,
//
//     @Column(name = "external_id", nullable = false, length = 255)
//     val externalId: String,
//
//     @Column(name = "access_token", length = 1024)
//     var accessToken: String? = null,
//
//     @Column(name = "refresh_token", length = 1024)
//     var refreshToken: String? = null,
//
//     @Column(name = "created_at", nullable = false, updatable = false)
//     val createdAt: Instant = Instant.now(),
//
//     @Column(name = "updated_at", nullable = false)
//     var updatedAt: Instant = Instant.now(),
//
//     @Column(name = "deleted_at")
//     var deletedAt: Instant? = null,
//
//     @Version
//     val version: Long = 0
// )
