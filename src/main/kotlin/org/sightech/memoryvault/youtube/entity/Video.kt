package org.sightech.memoryvault.youtube.entity

import jakarta.persistence.*
import org.sightech.memoryvault.tag.entity.Tag
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "videos")
class Video(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "youtube_list_id", nullable = false)
    val youtubeList: YoutubeList,

    @Column(name = "youtube_video_id", nullable = false, length = 255)
    val youtubeVideoId: String,

    @Column(length = 500)
    var title: String? = null,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "channel_name", length = 255)
    var channelName: String? = null,

    @Column(name = "thumbnail_path", length = 1024)
    var thumbnailPath: String? = null,

    @Column(name = "youtube_url", nullable = false, length = 2048)
    val youtubeUrl: String,

    @Column(name = "file_path", length = 1024)
    var filePath: String? = null,

    @Column(name = "downloaded_at")
    var downloadedAt: Instant? = null,

    @Column(name = "duration_seconds")
    var durationSeconds: Int? = null,

    @Column(name = "removed_from_youtube", nullable = false)
    var removedFromYoutube: Boolean = false,

    @Column(name = "removed_detected_at")
    var removedDetectedAt: Instant? = null,

    val createdAt: Instant = Instant.now(),

    var updatedAt: Instant = Instant.now(),

    @ManyToMany
    @JoinTable(
        name = "video_tags",
        joinColumns = [JoinColumn(name = "video_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")]
    )
    val tags: MutableSet<Tag> = mutableSetOf()
)
