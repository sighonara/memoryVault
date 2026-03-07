package org.sightech.memoryvault.feed.repository

import org.sightech.memoryvault.feed.entity.FeedItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface FeedItemRepository : JpaRepository<FeedItem, UUID> {

    @Query("SELECT fi FROM FeedItem fi LEFT JOIN FETCH fi.tags WHERE fi.feed.id = :feedId AND fi.feed.userId = :userId ORDER BY fi.publishedAt DESC NULLS LAST")
    fun findByFeedIdAndUserId(feedId: UUID, userId: UUID): List<FeedItem>

    @Query("SELECT fi FROM FeedItem fi LEFT JOIN FETCH fi.tags WHERE fi.feed.id = :feedId AND fi.feed.userId = :userId AND fi.readAt IS NULL ORDER BY fi.publishedAt DESC NULLS LAST")
    fun findUnreadByFeedIdAndUserId(feedId: UUID, userId: UUID): List<FeedItem>

    @Query("SELECT fi FROM FeedItem fi WHERE fi.id = :id AND fi.feed.userId = :userId")
    fun findByIdAndUserId(id: UUID, userId: UUID): FeedItem?

    fun existsByFeedIdAndGuid(feedId: UUID, guid: String): Boolean

    @Query("SELECT COUNT(fi) FROM FeedItem fi WHERE fi.feed.id = :feedId AND fi.feed.userId = :userId AND fi.readAt IS NULL")
    fun countUnreadByFeedIdAndUserId(feedId: UUID, userId: UUID): Long

    @Modifying
    @Query("UPDATE FeedItem fi SET fi.readAt = :readAt WHERE fi.feed.id = :feedId AND fi.readAt IS NULL AND EXISTS (SELECT f FROM Feed f WHERE f.id = :feedId AND f.userId = :userId)")
    fun markAllReadByFeedIdAndUserId(feedId: UUID, userId: UUID, readAt: Instant): Int

    fun countByFeedUserIdAndFeedDeletedAtIsNull(userId: UUID): Long

    fun countByFeedUserIdAndFeedDeletedAtIsNullAndReadAtIsNull(userId: UUID): Long
}
