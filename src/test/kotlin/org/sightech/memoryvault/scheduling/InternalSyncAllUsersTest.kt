package org.sightech.memoryvault.scheduling

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.cost.service.CostService
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.service.FeedService
import org.sightech.memoryvault.scheduling.controller.InternalSyncController
import org.sightech.memoryvault.scheduling.entity.JobType
import org.sightech.memoryvault.scheduling.entity.TriggerSource
import org.sightech.memoryvault.scheduling.service.SyncJobService
import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.service.SyncResult
import org.sightech.memoryvault.youtube.service.YoutubeListService
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InternalSyncAllUsersTest {

    private val feedService = mockk<FeedService>()
    private val youtubeListService = mockk<YoutubeListService>()
    private val syncJobService = mockk<SyncJobService>()
    private val costService = mockk<CostService>()
    private val userRepository = mockk<UserRepository>()
    private val controller = InternalSyncController(
        feedService, youtubeListService, syncJobService, costService, userRepository
    )

    private val user1 = User(email = "a@example.com", displayName = "A")
    private val user2 = User(email = "b@example.com", displayName = "B")

    private fun stubTracked(type: JobType) {
        val taskSlot = slot<() -> Map<String, Any>?>()
        every {
            syncJobService.runTracked(type, TriggerSource.SCHEDULED, any(), capture(taskSlot))
        } answers { taskSlot.captured.invoke() }
    }

    private fun result(userId: java.util.UUID, newVideos: Int): SyncResult {
        val list = YoutubeList(userId = userId, youtubeListId = "PL", url = "https://www.youtube.com/playlist?list=PL")
        return SyncResult(list, newVideos, 0, 0, 0)
    }

    @Test
    fun `syncFeeds refreshes feeds for every active user and aggregates`() {
        every { userRepository.findAllByDeletedAtIsNull() } returns listOf(user1, user2)
        val feed = Feed(userId = user1.id, url = "https://example.com/feed")
        coEvery { feedService.refreshFeedsForUser(user1.id) } returns listOf(feed to 3)
        coEvery { feedService.refreshFeedsForUser(user2.id) } returns listOf(feed to 2, feed to 1)
        stubTracked(JobType.RSS_FETCH)

        val response = controller.syncFeeds()

        assertEquals(3, response.body?.get("feedsRefreshed"))
        assertEquals(6, response.body?.get("newItems"))
        coVerify(exactly = 1) { feedService.refreshFeedsForUser(user1.id) }
        coVerify(exactly = 1) { feedService.refreshFeedsForUser(user2.id) }
    }

    @Test
    fun `syncFeeds continues with the next user when one user fails`() {
        every { userRepository.findAllByDeletedAtIsNull() } returns listOf(user1, user2)
        val feed = Feed(userId = user2.id, url = "https://example.com/feed")
        coEvery { feedService.refreshFeedsForUser(user1.id) } throws RuntimeException("boom")
        coEvery { feedService.refreshFeedsForUser(user2.id) } returns listOf(feed to 4)
        stubTracked(JobType.RSS_FETCH)

        val response = controller.syncFeeds()

        coVerify(exactly = 1) { feedService.refreshFeedsForUser(user2.id) }
        assertEquals(1, response.body?.get("feedsRefreshed"))
        assertEquals(4, response.body?.get("newItems"))
        assertEquals(1, response.body?.get("failedUsers"))
    }

    @Test
    fun `syncFeeds fails the run when every user fails`() {
        every { userRepository.findAllByDeletedAtIsNull() } returns listOf(user1, user2)
        coEvery { feedService.refreshFeedsForUser(any()) } throws RuntimeException("boom")
        stubTracked(JobType.RSS_FETCH)

        assertFailsWith<IllegalStateException> { controller.syncFeeds() }
    }

    @Test
    fun `syncFeeds succeeds with zero users`() {
        every { userRepository.findAllByDeletedAtIsNull() } returns emptyList()
        stubTracked(JobType.RSS_FETCH)

        val response = controller.syncFeeds()

        assertEquals(0, response.body?.get("feedsRefreshed"))
        assertEquals(0, response.body?.get("failedUsers"))
    }

    @Test
    fun `syncYoutube fails the run when every user fails`() {
        every { userRepository.findAllByDeletedAtIsNull() } returns listOf(user1, user2)
        every { youtubeListService.refreshListsForUser(any()) } throws RuntimeException("boom")
        stubTracked(JobType.YT_SYNC)

        assertFailsWith<IllegalStateException> { controller.syncYoutube() }
    }

    @Test
    fun `syncYoutube succeeds with zero users`() {
        every { userRepository.findAllByDeletedAtIsNull() } returns emptyList()
        stubTracked(JobType.YT_SYNC)

        val response = controller.syncYoutube()

        assertEquals(0, response.body?.get("listsSynced"))
        assertEquals(0, response.body?.get("failedUsers"))
    }

    @Test
    fun `syncYoutube refreshes lists for every active user and aggregates`() {
        every { userRepository.findAllByDeletedAtIsNull() } returns listOf(user1, user2)
        every { youtubeListService.refreshListsForUser(user1.id) } returns listOf(result(user1.id, 2))
        every { youtubeListService.refreshListsForUser(user2.id) } returns listOf(result(user2.id, 5))
        stubTracked(JobType.YT_SYNC)

        val response = controller.syncYoutube()

        assertEquals(2, response.body?.get("listsSynced"))
        assertEquals(7, response.body?.get("newVideos"))
        verify(exactly = 1) { youtubeListService.refreshListsForUser(user1.id) }
        verify(exactly = 1) { youtubeListService.refreshListsForUser(user2.id) }
    }

    @Test
    fun `syncYoutube continues with the next user when one user fails`() {
        every { userRepository.findAllByDeletedAtIsNull() } returns listOf(user1, user2)
        every { youtubeListService.refreshListsForUser(user1.id) } throws RuntimeException("boom")
        every { youtubeListService.refreshListsForUser(user2.id) } returns listOf(result(user2.id, 5))
        stubTracked(JobType.YT_SYNC)

        val response = controller.syncYoutube()

        verify(exactly = 1) { youtubeListService.refreshListsForUser(user2.id) }
        assertEquals(1, response.body?.get("listsSynced"))
        assertEquals(5, response.body?.get("newVideos"))
        assertEquals(1, response.body?.get("failedUsers"))
    }
}
