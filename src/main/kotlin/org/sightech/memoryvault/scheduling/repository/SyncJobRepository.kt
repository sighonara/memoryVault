package org.sightech.memoryvault.scheduling.repository

import org.sightech.memoryvault.scheduling.entity.JobStatus
import org.sightech.memoryvault.scheduling.entity.JobType
import org.sightech.memoryvault.scheduling.entity.SyncJob
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SyncJobRepository : JpaRepository<SyncJob, UUID> {

    @Query("SELECT j FROM SyncJob j WHERE j.userId = :userId ORDER BY j.startedAt DESC LIMIT :limit")
    fun findRecentByUserId(userId: UUID, limit: Int): List<SyncJob>

    @Query("SELECT j FROM SyncJob j WHERE j.userId = :userId AND j.type = :type ORDER BY j.startedAt DESC LIMIT :limit")
    fun findRecentByUserIdAndType(userId: UUID, type: JobType, limit: Int): List<SyncJob>

    @Query("SELECT j FROM SyncJob j WHERE j.userId = :userId AND j.type = :type AND j.status = :status ORDER BY j.completedAt DESC LIMIT 1")
    fun findLastSuccessful(userId: UUID, type: JobType, status: JobStatus = JobStatus.SUCCESS): SyncJob?
}
