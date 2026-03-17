package org.sightech.memoryvault.feed.entity

import jakarta.persistence.*
import org.sightech.memoryvault.tag.entity.Tag
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "feed_items")
class FeedItem(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_id", nullable = false)
    val feed: Feed,

    @Column(nullable = false, length = 2048)
    val guid: String,

    @Column(length = 500)
    val title: String? = null,

    @Column(length = 2048)
    val url: String? = null,

    @Column(columnDefinition = "TEXT")
    val content: String? = null,

    @Column(length = 255)
    val author: String? = null,

    @Column(name = "image_url", length = 2048)
    val imageUrl: String? = null,

    @Column(name = "published_at")
    val publishedAt: Instant? = null,

    @Column(name = "read_at")
    var readAt: Instant? = null,

    @Column(name = "starred_at")
    var starredAt: Instant? = null,

    val createdAt: Instant = Instant.now(),

    @ManyToMany
    @JoinTable(
        name = "feed_item_tags",
        joinColumns = [JoinColumn(name = "feed_item_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")]
    )
    val tags: MutableSet<Tag> = mutableSetOf()
)
