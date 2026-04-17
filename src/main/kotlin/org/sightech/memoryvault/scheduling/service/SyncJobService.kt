package org.sightech.memoryvault.scheduling.service

import org.sightech.memoryvault.scheduling.entity.JobStatus
import org.sightech.memoryvault.scheduling.entity.JobType
import org.sightech.memoryvault.scheduling.entity.SyncJob
import org.sightech.memoryvault.scheduling.entity.TriggerSource
import org.sightech.memoryvault.scheduling.repository.SyncJobRepository
import org.sightech.memoryvault.websocket.JobStatusChanged
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@Service
class SyncJobService(
    private val syncJobRepository: SyncJobRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val objectMapper = ObjectMapper()

    fun recordStart(type: JobType, triggeredBy: TriggerSource, userId: UUID): SyncJob {
        val job = SyncJob(
            userId = userId,
            type = type,
            triggeredBy = triggeredBy
        )
        job.status = JobStatus.RUNNING
        val saved = syncJobRepository.save(job)
        eventPublisher.publishEvent(JobStatusChanged(
            userId = userId, timestamp = Instant.now(),
            jobId = saved.id, jobType = type.name,
            oldStatus = JobStatus.PENDING.name, newStatus = JobStatus.RUNNING.name
        ))
        return saved
    }

    fun recordSuccess(jobId: UUID, metadata: Map<String, Any>?) {
        val job = syncJobRepository.findById(jobId).orElse(null) ?: return
        val oldStatus = job.status.name
        job.status = JobStatus.SUCCESS
        job.completedAt = Instant.now()
        if (metadata != null) {
            job.metadata = objectMapper.writeValueAsString(metadata)
        }
        syncJobRepository.save(job)
        eventPublisher.publishEvent(JobStatusChanged(
            userId = job.userId, timestamp = Instant.now(),
            jobId = jobId, jobType = job.type.name,
            oldStatus = oldStatus, newStatus = JobStatus.SUCCESS.name
        ))
    }

    fun runTracked(
        jobType: JobType,
        triggeredBy: TriggerSource,
        userId: UUID,
        task: () -> Map<String, Any>?
    ): Map<String, Any>? {
        val job = recordStart(jobType, triggeredBy, userId)
        return try {
            val metadata = task()
            recordSuccess(job.id, metadata)
            metadata
        } catch (e: Exception) {
            recordFailure(job.id, e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    fun recordFailure(jobId: UUID, error: String) {
        val job = syncJobRepository.findById(jobId).orElse(null) ?: return
        val oldStatus = job.status.name
        job.status = JobStatus.FAILED
        job.completedAt = Instant.now()
        job.errorMessage = error
        syncJobRepository.save(job)
        eventPublisher.publishEvent(JobStatusChanged(
            userId = job.userId, timestamp = Instant.now(),
            jobId = jobId, jobType = job.type.name,
            oldStatus = oldStatus, newStatus = JobStatus.FAILED.name
        ))
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
