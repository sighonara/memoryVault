package org.sightech.memoryvault.feed.repository

import org.sightech.memoryvault.feed.entity.Feed
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface FeedRepository : JpaRepository<Feed, UUID> {

    @Query("SELECT f FROM Feed f WHERE f.deletedAt IS NULL AND f.userId = :userId ORDER BY f.title")
    fun findAllActiveByUserId(userId: UUID): List<Feed>

    @Query("SELECT f FROM Feed f WHERE f.id = :id AND f.userId = :userId AND f.deletedAt IS NULL")
    fun findActiveByIdAndUserId(id: UUID, userId: UUID): Feed?

    fun countByUserIdAndDeletedAtIsNull(userId: UUID): Long

    fun countByUserIdAndDeletedAtIsNullAndFailureCountGreaterThan(userId: UUID, failureCount: Int): Long

    @Query("SELECT f FROM Feed f WHERE f.deletedAt IS NULL AND f.userId = :userId AND f.category.id = :categoryId ORDER BY f.title")
    fun findAllActiveByCategoryId(userId: UUID, categoryId: UUID): List<Feed>

    @Modifying
    @Query("UPDATE feeds SET category_id = :targetCategoryId, updated_at = :now WHERE category_id = :sourceCategoryId AND user_id = :userId AND deleted_at IS NULL", nativeQuery = true)
    fun moveFeedsBetweenCategories(sourceCategoryId: UUID, targetCategoryId: UUID, userId: UUID, now: Instant): Int
}
