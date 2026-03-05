package org.sightech.memoryvault.feed.repository

import org.sightech.memoryvault.feed.entity.FeedItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface FeedItemRepository : JpaRepository<FeedItem, UUID> {

    @Query("SELECT fi FROM FeedItem fi LEFT JOIN FETCH fi.tags WHERE fi.feed.id = :feedId ORDER BY fi.publishedAt DESC NULLS LAST")
    fun findByFeedId(feedId: UUID): List<FeedItem>

    @Query("SELECT fi FROM FeedItem fi LEFT JOIN FETCH fi.tags WHERE fi.feed.id = :feedId AND fi.readAt IS NULL ORDER BY fi.publishedAt DESC NULLS LAST")
    fun findUnreadByFeedId(feedId: UUID): List<FeedItem>

    fun existsByFeedIdAndGuid(feedId: UUID, guid: String): Boolean

    @Query("SELECT COUNT(fi) FROM FeedItem fi WHERE fi.feed.id = :feedId AND fi.readAt IS NULL")
    fun countUnreadByFeedId(feedId: UUID): Long

    @Modifying
    @Query("UPDATE FeedItem fi SET fi.readAt = :readAt WHERE fi.feed.id = :feedId AND fi.readAt IS NULL")
    fun markAllReadByFeedId(feedId: UUID, readAt: Instant): Int
}
