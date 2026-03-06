package org.sightech.memoryvault.scheduling.service

import org.sightech.memoryvault.scheduling.entity.JobStatus
import org.sightech.memoryvault.scheduling.entity.JobType
import org.sightech.memoryvault.scheduling.entity.SyncJob
import org.sightech.memoryvault.scheduling.entity.TriggerSource
import org.sightech.memoryvault.scheduling.repository.SyncJobRepository
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@Service
class SyncJobService(
    private val syncJobRepository: SyncJobRepository
) {

    private val objectMapper = ObjectMapper()

    fun recordStart(type: JobType, triggeredBy: TriggerSource, userId: UUID): SyncJob {
        val job = SyncJob(
            userId = userId,
            type = type,
            triggeredBy = triggeredBy
        )
        job.status = JobStatus.RUNNING
        return syncJobRepository.save(job)
    }

    fun recordSuccess(jobId: UUID, metadata: Map<String, Any>?) {
        val job = syncJobRepository.findById(jobId).orElse(null) ?: return
        job.status = JobStatus.SUCCESS
        job.completedAt = Instant.now()
        if (metadata != null) {
            job.metadata = objectMapper.writeValueAsString(metadata)
        }
        syncJobRepository.save(job)
    }

    fun recordFailure(jobId: UUID, error: String) {
        val job = syncJobRepository.findById(jobId).orElse(null) ?: return
        job.status = JobStatus.FAILED
        job.completedAt = Instant.now()
        job.errorMessage = error
        syncJobRepository.save(job)
    }

    fun listJobs(userId: UUID, type: JobType?, limit: Int): List<SyncJob> {
        return if (type != null) {
            syncJobRepository.findRecentByUserIdAndType(userId, type, limit)
        } else {
            syncJobRepository.findRecentByUserId(userId, limit)
        }
    }

    fun findLastSuccessful(userId: UUID, type: JobType): SyncJob? {
        return syncJobRepository.findLastSuccessful(userId, type)
    }
}
