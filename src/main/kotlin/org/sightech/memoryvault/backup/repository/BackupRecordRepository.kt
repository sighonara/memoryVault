package org.sightech.memoryvault.backup.repository

import org.sightech.memoryvault.backup.entity.BackupRecord
import org.sightech.memoryvault.backup.entity.BackupStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BackupRecordRepository : JpaRepository<BackupRecord, UUID> {

    fun findByVideoId(videoId: UUID): List<BackupRecord>

    fun findByVideoIdAndProviderId(videoId: UUID, providerId: UUID): BackupRecord?

    @Query("SELECT r FROM BackupRecord r WHERE r.status = :status ORDER BY r.createdAt ASC")
    fun findByStatus(status: BackupStatus): List<BackupRecord>

    @Query("SELECT r FROM BackupRecord r WHERE r.status = 'BACKED_UP'")
    fun findAllBackedUp(): List<BackupRecord>

    @Query("SELECT COUNT(r) FROM BackupRecord r WHERE r.providerId IN (SELECT p.id FROM BackupProviderEntity p WHERE p.userId = :userId AND p.deletedAt IS NULL)")
    fun countByUserId(userId: UUID): Long

    @Query("SELECT COUNT(r) FROM BackupRecord r WHERE r.status = :status AND r.providerId IN (SELECT p.id FROM BackupProviderEntity p WHERE p.userId = :userId AND p.deletedAt IS NULL)")
    fun countByUserIdAndStatus(userId: UUID, status: BackupStatus): Long

    @Query("SELECT r.videoId FROM BackupRecord r WHERE r.providerId = :providerId")
    fun findVideoIdsByProviderId(providerId: UUID): List<UUID>
}
