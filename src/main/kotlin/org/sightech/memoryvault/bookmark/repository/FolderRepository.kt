package org.sightech.memoryvault.bookmark.repository

import org.sightech.memoryvault.bookmark.entity.Folder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface FolderRepository : JpaRepository<Folder, UUID> {

    @Query("SELECT f FROM Folder f WHERE f.userId = :userId AND f.deletedAt IS NULL ORDER BY f.sortOrder")
    fun findAllActiveByUserId(userId: UUID): List<Folder>

    @Query("SELECT f FROM Folder f WHERE f.id = :id AND f.userId = :userId AND f.deletedAt IS NULL")
    fun findActiveByIdAndUserId(id: UUID, userId: UUID): Folder?

    @Query("SELECT f FROM Folder f WHERE f.parentId = :parentId AND f.userId = :userId AND f.deletedAt IS NULL ORDER BY f.sortOrder")
    fun findChildrenByParentId(parentId: UUID, userId: UUID): List<Folder>

    fun countByUserIdAndDeletedAtIsNull(userId: UUID): Long
}
