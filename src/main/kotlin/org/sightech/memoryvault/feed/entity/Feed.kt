package org.sightech.memoryvault.feed.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "feeds")
class Feed(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false, length = 2048)
    val url: String,

    @Column(length = 500)
    var title: String? = null,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "site_url", length = 2048)
    var siteUrl: String? = null,

    @Column(name = "last_fetched_at")
    var lastFetchedAt: Instant? = null,

    @Column(name = "fetch_interval_minutes", nullable = false)
    var fetchIntervalMinutes: Int = 60,

    @Column(name = "failure_count", nullable = false)
    var failureCount: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    var category: FeedCategory? = null,

    val createdAt: Instant = Instant.now(),

    var updatedAt: Instant = Instant.now(),

    var deletedAt: Instant? = null,

    @Version
    val version: Long = 0
)
