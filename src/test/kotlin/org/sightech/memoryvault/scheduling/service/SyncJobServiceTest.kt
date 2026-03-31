package org.sightech.memoryvault.scheduling.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.scheduling.entity.JobStatus
import org.sightech.memoryvault.scheduling.entity.JobType
import org.sightech.memoryvault.scheduling.entity.SyncJob
import org.sightech.memoryvault.scheduling.entity.TriggerSource
import org.sightech.memoryvault.scheduling.repository.SyncJobRepository
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SyncJobServiceTest {

    private val repository = mockk<SyncJobRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = SyncJobService(repository, eventPublisher)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `recordStart creates a RUNNING job`() {
        val slot = slot<SyncJob>()
        every { repository.save(capture(slot)) } answers { slot.captured }

        val job = service.recordStart(JobType.RSS_FETCH, TriggerSource.SCHEDULED, userId)

        assertEquals(JobType.RSS_FETCH, job.type)
        assertEquals(JobStatus.RUNNING, job.status)
        assertEquals(TriggerSource.SCHEDULED, job.triggeredBy)
        assertEquals(userId, job.userId)
    }

    @Test
    fun `recordSuccess updates status and metadata`() {
        val job = SyncJob(userId = userId, type = JobType.YT_SYNC, triggeredBy = TriggerSource.MANUAL)
        job.status = JobStatus.RUNNING
        every { repository.findById(job.id) } returns java.util.Optional.of(job)
        every { repository.save(any()) } answers { firstArg() }

        service.recordSuccess(job.id, mapOf("newVideos" to 3))

        assertEquals(JobStatus.SUCCESS, job.status)
        assertNotNull(job.completedAt)
        assertNotNull(job.metadata)
    }

    @Test
    fun `recordFailure sets error message`() {
        val job = SyncJob(userId = userId, type = JobType.RSS_FETCH, triggeredBy = TriggerSource.SCHEDULED)
        job.status = JobStatus.RUNNING
        every { repository.findById(job.id) } returns java.util.Optional.of(job)
        every { repository.save(any()) } answers { firstArg() }

        service.recordFailure(job.id, "Connection refused")

        assertEquals(JobStatus.FAILED, job.status)
        assertEquals("Connection refused", job.errorMessage)
        assertNotNull(job.completedAt)
    }

    @Test
    fun `listJobs delegates to repository`() {
        every { repository.findRecentByUserId(userId, 20) } returns listOf(
            SyncJob(userId = userId, type = JobType.RSS_FETCH, triggeredBy = TriggerSource.SCHEDULED)
        )

        val result = service.listJobs(userId, null, 20)
        assertEquals(1, result.size)
    }

    @Test
    fun `listJobs with type filter`() {
        every { repository.findRecentByUserIdAndType(userId, JobType.YT_SYNC, 10) } returns emptyList()

        val result = service.listJobs(userId, JobType.YT_SYNC, 10)
        assertEquals(0, result.size)
    }
}
