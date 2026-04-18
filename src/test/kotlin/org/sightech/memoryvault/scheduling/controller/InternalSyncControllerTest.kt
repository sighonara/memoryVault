package org.sightech.memoryvault.scheduling.controller

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.cost.service.CostService
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.service.FeedService
import org.sightech.memoryvault.scheduling.entity.JobStatus
import org.sightech.memoryvault.scheduling.entity.JobType
import org.sightech.memoryvault.scheduling.entity.SyncJob
import org.sightech.memoryvault.scheduling.entity.TriggerSource
import org.sightech.memoryvault.scheduling.service.SyncJobService
import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.service.SyncResult
import org.sightech.memoryvault.youtube.service.YoutubeListService
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InternalSyncControllerTest {

    private val feedService = mockk<FeedService>()
    private val youtubeListService = mockk<YoutubeListService>()
    private val syncJobService = mockk<SyncJobService>()
    private val costService = mockk<CostService>()
    private val controller = InternalSyncController(feedService, youtubeListService, syncJobService, costService)

    @Test
    fun `syncFeeds invokes feedService and returns 200 with metadata`() {
        val userId = UUID.randomUUID()
        val feed = Feed(id = UUID.randomUUID(), userId = userId, url = "https://example.com/feed")

        coEvery { feedService.refreshFeed(null) } returns listOf(feed to 3, feed to 2)
        val taskSlot = slot<() -> Map<String, Any>?>()
        every {
            syncJobService.runTracked(JobType.RSS_FETCH, TriggerSource.SCHEDULED, any(), capture(taskSlot))
        } answers { taskSlot.captured.invoke() }

        val response = controller.syncFeeds()

        assertEquals(200, response.statusCode.value())
        assertEquals(2, response.body?.get("feedsRefreshed"))
        assertEquals(5, response.body?.get("newItems"))
    }

    @Test
    fun `syncFeeds propagates exception when feedService fails`() {
        coEvery { feedService.refreshFeed(null) } throws RuntimeException("rss down")
        val taskSlot = slot<() -> Map<String, Any>?>()
        every {
            syncJobService.runTracked(JobType.RSS_FETCH, TriggerSource.SCHEDULED, any(), capture(taskSlot))
        } answers { taskSlot.captured.invoke() }

        val ex = assertFailsWith<RuntimeException> { controller.syncFeeds() }
        assertEquals("rss down", ex.message)
    }

    @Test
    fun `syncYoutube invokes youtubeListService and returns 200 with metadata`() {
        val list = YoutubeList(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            youtubeListId = "PL1",
            url = "https://www.youtube.com/playlist?list=PL1"
        )
        val result = SyncResult(
            list = list,
            newVideos = 4,
            removedVideos = 1,
            downloadSuccesses = 3,
            downloadFailures = 1
        )
        every { youtubeListService.refreshList(null) } returns listOf(result, result)
        val taskSlot = slot<() -> Map<String, Any>?>()
        every {
            syncJobService.runTracked(JobType.YT_SYNC, TriggerSource.SCHEDULED, any(), capture(taskSlot))
        } answers { taskSlot.captured.invoke() }

        val response = controller.syncYoutube()

        assertEquals(200, response.statusCode.value())
        assertEquals(2, response.body?.get("listsSynced"))
        assertEquals(8, response.body?.get("newVideos"))
        assertEquals(2, response.body?.get("removedVideos"))
        assertEquals(6, response.body?.get("downloadSuccesses"))
        assertEquals(2, response.body?.get("downloadFailures"))
    }

    @Test
    fun `runTracked records success on happy path`() {
        // Lightweight check that the service-level contract the controller depends on
        // (start -> task -> success, rethrow on failure) is honored by SyncJobService.
        val repo = mockk<org.sightech.memoryvault.scheduling.repository.SyncJobRepository>()
        val publisher = mockk<org.springframework.context.ApplicationEventPublisher>(relaxed = true)
        val service = SyncJobService(repo, publisher)
        val startedJob = SyncJob(userId = UUID.randomUUID(), type = JobType.RSS_FETCH, triggeredBy = TriggerSource.SCHEDULED).apply {
            status = JobStatus.RUNNING
        }
        every { repo.save(any()) } returnsMany listOf(startedJob, startedJob) andThen startedJob
        every { repo.findById(startedJob.id) } returns java.util.Optional.of(startedJob)

        val metadata = service.runTracked(JobType.RSS_FETCH, TriggerSource.SCHEDULED, UUID.randomUUID()) {
            mapOf("count" to 1)
        }

        assertEquals(1, metadata?.get("count"))
        verify(atLeast = 2) { repo.save(any()) }
    }

    @Test
    fun `runTracked records failure and rethrows`() {
        val repo = mockk<org.sightech.memoryvault.scheduling.repository.SyncJobRepository>()
        val publisher = mockk<org.springframework.context.ApplicationEventPublisher>(relaxed = true)
        val service = SyncJobService(repo, publisher)
        val startedJob = SyncJob(userId = UUID.randomUUID(), type = JobType.YT_SYNC, triggeredBy = TriggerSource.SCHEDULED).apply {
            status = JobStatus.RUNNING
        }
        every { repo.save(any()) } returns startedJob
        every { repo.findById(startedJob.id) } returns java.util.Optional.of(startedJob)

        val ex = assertFailsWith<IllegalStateException> {
            service.runTracked(JobType.YT_SYNC, TriggerSource.SCHEDULED, UUID.randomUUID()) {
                throw IllegalStateException("boom")
            }
        }

        assertEquals("boom", ex.message)
        assertTrue(startedJob.status == JobStatus.FAILED)
    }
}
