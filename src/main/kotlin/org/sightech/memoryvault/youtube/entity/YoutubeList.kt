package org.sightech.memoryvault.youtube.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "youtube_lists")
class YoutubeList(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "youtube_list_id", nullable = false, length = 255)
    val youtubeListId: String,

    @Column(nullable = false, length = 2048)
    val url: String,

    @Column(length = 500)
    var name: String? = null,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "last_synced_at")
    var lastSyncedAt: Instant? = null,

    @Column(name = "failure_count", nullable = false)
    var failureCount: Int = 0,

    val createdAt: Instant = Instant.now(),

    var updatedAt: Instant = Instant.now(),

    var deletedAt: Instant? = null,

    @Version
    val version: Long = 0
)
