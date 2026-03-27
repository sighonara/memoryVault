package org.sightech.memoryvault.auth.entity

// TODO: Phase 7 stub — API key authentication
// Uncomment and implement when API key auth is needed.

// import jakarta.persistence.*
// import java.time.Instant
// import java.util.UUID
//
// @Entity
// @Table(name = "api_keys")
// class ApiKey(
//     @Id
//     val id: UUID = UUID.randomUUID(),
//
//     @Column(name = "user_id", nullable = false)
//     val userId: UUID,
//
//     @Column(nullable = false, length = 255)
//     var name: String,
//
//     @Column(name = "key_hash", nullable = false, length = 255)
//     val keyHash: String,
//
//     @Column(name = "last_used_at")
//     var lastUsedAt: Instant? = null,
//
//     @Column(name = "created_at", nullable = false, updatable = false)
//     val createdAt: Instant = Instant.now(),
//
//     @Column(name = "deleted_at")
//     var deletedAt: Instant? = null,
//
//     @Version
//     val version: Long = 0
// )
