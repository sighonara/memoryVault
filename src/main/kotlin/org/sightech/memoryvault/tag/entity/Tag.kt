package org.sightech.memoryvault.tag.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tags")
class Tag(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(length = 7)
    val color: String? = null,

    val createdAt: Instant = Instant.now()
)
