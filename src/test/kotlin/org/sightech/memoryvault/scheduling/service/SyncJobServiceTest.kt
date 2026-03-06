package org.sightech.memoryvault.scheduling.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.scheduling.entity.SyncJob
import org.sightech.memoryvault.scheduling.repository.SyncJobRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SyncJobServiceTest {

    private val repository = mockk<SyncJobRepository>()
    private val service = SyncJobService(repository)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `recordStart creates a RUNNING job`() {
        val slot = slot<SyncJob>()
        every { repository.save(capture(slot)) } answers { slot.captured }

        val job = service.recordStart("RSS_FETCH", "SCHEDULED", userId)

        assertEquals("RSS_FETCH", job.type)
        assertEquals("RUNNING", job.status)
        assertEquals("SCHEDULED", job.triggeredBy)
        assertEquals(userId, job.userId)
    }

    @Test
    fun `recordSuccess updates status and metadata`() {
        val job = SyncJob(userId = userId, type = "YT_SYNC", triggeredBy = "MANUAL")
        job.status = "RUNNING"
        every { repository.findById(job.id) } returns java.util.Optional.of(job)
        every { repository.save(any()) } answers { firstArg() }

        service.recordSuccess(job.id, mapOf("newVideos" to 3))

        assertEquals("SUCCESS", job.status)
        assertNotNull(job.completedAt)
        assertNotNull(job.metadata)
    }

    @Test
    fun `recordFailure sets error message`() {
        val job = SyncJob(userId = userId, type = "RSS_FETCH", triggeredBy = "SCHEDULED")
        job.status = "RUNNING"
        every { repository.findById(job.id) } returns java.util.Optional.of(job)
        every { repository.save(any()) } answers { firstArg() }

        service.recordFailure(job.id, "Connection refused")

        assertEquals("FAILED", job.status)
        assertEquals("Connection refused", job.errorMessage)
        assertNotNull(job.completedAt)
    }

    @Test
    fun `listJobs delegates to repository`() {
        every { repository.findRecentByUserId(userId, 20) } returns listOf(
            SyncJob(userId = userId, type = "RSS_FETCH", triggeredBy = "SCHEDULED")
        )

        val result = service.listJobs(userId, null, 20)
        assertEquals(1, result.size)
    }

    @Test
    fun `listJobs with type filter`() {
        every { repository.findRecentByUserIdAndType(userId, "YT_SYNC", 10) } returns emptyList()

        val result = service.listJobs(userId, "YT_SYNC", 10)
        assertEquals(0, result.size)
    }
}
