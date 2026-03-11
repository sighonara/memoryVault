package org.sightech.memoryvault.bookmark.repository

import org.sightech.memoryvault.bookmark.entity.Bookmark
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BookmarkRepository : JpaRepository<Bookmark, UUID> {

    @Query("SELECT b FROM Bookmark b LEFT JOIN FETCH b.tags WHERE b.deletedAt IS NULL AND b.userId = :userId ORDER BY b.createdAt DESC")
    fun findAllActiveByUserId(userId: UUID): List<Bookmark>

    @Query("SELECT b FROM Bookmark b LEFT JOIN FETCH b.tags WHERE b.id = :id AND b.userId = :userId AND b.deletedAt IS NULL")
    fun findActiveByIdAndUserId(id: UUID, userId: UUID): Bookmark?

    fun countByUserIdAndDeletedAtIsNull(userId: UUID): Long
}
