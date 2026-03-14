package org.sightech.memoryvault.bookmark.repository

import org.sightech.memoryvault.bookmark.entity.IngestPreviewEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface IngestPreviewRepository : JpaRepository<IngestPreviewEntity, UUID> {
    @Query("SELECT p FROM IngestPreviewEntity p WHERE p.id = :id AND p.userId = :userId AND p.committed = false AND p.expiresAt > CURRENT_TIMESTAMP")
    fun findActiveByIdAndUserId(id: UUID, userId: UUID): IngestPreviewEntity?

    @Query("SELECT p FROM IngestPreviewEntity p WHERE p.userId = :userId AND p.committed = false AND p.expiresAt > CURRENT_TIMESTAMP ORDER BY p.createdAt DESC")
    fun findPendingByUserId(userId: UUID): List<IngestPreviewEntity>
}
