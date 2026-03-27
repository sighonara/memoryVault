package org.sightech.memoryvault.feed.repository

import org.sightech.memoryvault.feed.entity.FeedCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface FeedCategoryRepository : JpaRepository<FeedCategory, UUID> {

    @Query("SELECT fc FROM FeedCategory fc WHERE fc.deletedAt IS NULL AND fc.userId = :userId ORDER BY fc.sortOrder, fc.name")
    fun findAllActiveByUserId(userId: UUID): List<FeedCategory>

    @Query("SELECT fc FROM FeedCategory fc WHERE fc.id = :id AND fc.userId = :userId AND fc.deletedAt IS NULL")
    fun findActiveByIdAndUserId(id: UUID, userId: UUID): FeedCategory?

    @Query("SELECT fc FROM FeedCategory fc WHERE fc.userId = :userId AND fc.deletedAt IS NULL AND LOWER(fc.name) = LOWER(:name)")
    fun findActiveByUserIdAndNameIgnoreCase(userId: UUID, name: String): FeedCategory?

    @Query("SELECT COALESCE(MAX(fc.sortOrder), 0) FROM FeedCategory fc WHERE fc.userId = :userId AND fc.deletedAt IS NULL")
    fun findMaxSortOrderByUserId(userId: UUID): Int
}
