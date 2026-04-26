package org.sightech.memoryvault.backup.repository

import org.sightech.memoryvault.backup.entity.BackupProviderEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BackupProviderRepository : JpaRepository<BackupProviderEntity, UUID> {

    @Query("SELECT p FROM BackupProviderEntity p WHERE p.userId = :userId AND p.deletedAt IS NULL")
    fun findAllActiveByUserId(userId: UUID): List<BackupProviderEntity>

    @Query("SELECT p FROM BackupProviderEntity p WHERE p.id = :id AND p.userId = :userId AND p.deletedAt IS NULL")
    fun findActiveByIdAndUserId(id: UUID, userId: UUID): BackupProviderEntity?

    @Query("SELECT p FROM BackupProviderEntity p WHERE p.userId = :userId AND p.isPrimary = true AND p.deletedAt IS NULL")
    fun findPrimaryByUserId(userId: UUID): BackupProviderEntity?

    @Query("SELECT p FROM BackupProviderEntity p WHERE p.deletedAt IS NULL")
    fun findAllActive(): List<BackupProviderEntity>
}
