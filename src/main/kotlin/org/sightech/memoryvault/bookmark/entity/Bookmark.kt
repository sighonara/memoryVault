package org.sightech.memoryvault.bookmark.entity

import jakarta.persistence.*
import org.sightech.memoryvault.tag.entity.Tag
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "bookmarks")
class Bookmark(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false, length = 2048)
    val url: String,

    @Column(nullable = false, length = 500)
    var title: String,

    val createdAt: Instant = Instant.now(),

    var updatedAt: Instant = Instant.now(),

    var deletedAt: Instant? = null,

    @Version
    val version: Long = 0,

    @Column(name = "folder_id")
    var folderId: UUID? = null,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "normalized_url", length = 2048)
    var normalizedUrl: String? = null,

    @ManyToMany
    @JoinTable(
        name = "bookmark_tags",
        joinColumns = [JoinColumn(name = "bookmark_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")]
    )
    val tags: MutableSet<Tag> = mutableSetOf()
)
