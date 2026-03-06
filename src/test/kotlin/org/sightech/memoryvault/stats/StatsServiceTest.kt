package org.sightech.memoryvault.stats

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.bookmark.repository.BookmarkRepository
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.sightech.memoryvault.scheduling.entity.JobType
import org.sightech.memoryvault.scheduling.entity.SyncJob
import org.sightech.memoryvault.scheduling.entity.TriggerSource
import org.sightech.memoryvault.scheduling.service.SyncJobService
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.tag.repository.TagRepository
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.sightech.memoryvault.youtube.repository.YoutubeListRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class StatsServiceTest {

    private val bookmarkRepository = mockk<BookmarkRepository>()
    private val feedRepository = mockk<FeedRepository>()
    private val feedItemRepository = mockk<FeedItemRepository>()
    private val youtubeListRepository = mockk<YoutubeListRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val tagRepository = mockk<TagRepository>()
    private val syncJobService = mockk<SyncJobService>()
    private val storageService = mockk<StorageService>()

    private val service = StatsService(
        bookmarkRepository, feedRepository, feedItemRepository,
        youtubeListRepository, videoRepository, tagRepository,
        syncJobService, storageService
    )

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `getStats returns correct counts`() {
        every { bookmarkRepository.countByUserIdAndDeletedAtIsNull(userId) } returns 10
        every { feedRepository.countByUserIdAndDeletedAtIsNull(userId) } returns 3
        every { feedItemRepository.countByFeedUserIdAndFeedDeletedAtIsNull(userId) } returns 150
        every { feedItemRepository.countByFeedUserIdAndFeedDeletedAtIsNullAndReadAtIsNull(userId) } returns 42
        every { youtubeListRepository.countByUserIdAndDeletedAtIsNull(userId) } returns 2
        every { videoRepository.countByYoutubeListUserIdAndYoutubeListDeletedAtIsNull(userId) } returns 75
        every { videoRepository.countByYoutubeListUserIdAndYoutubeListDeletedAtIsNullAndDownloadedAtIsNotNull(userId) } returns 60
        every { videoRepository.countByYoutubeListUserIdAndYoutubeListDeletedAtIsNullAndRemovedFromYoutubeTrue(userId) } returns 5
        every { tagRepository.countByUserId(userId) } returns 12
        every { storageService.usedBytes() } returns 1_073_741_824L
        every { syncJobService.findLastSuccessful(userId, JobType.RSS_FETCH) } returns SyncJob(
            userId = userId, type = JobType.RSS_FETCH, triggeredBy = TriggerSource.SCHEDULED
        ).apply { completedAt = Instant.parse("2026-03-05T10:00:00Z") }
        every { syncJobService.findLastSuccessful(userId, JobType.YT_SYNC) } returns null
        every { feedRepository.countByUserIdAndDeletedAtIsNullAndFailureCountGreaterThan(userId, 0) } returns 1
        every { youtubeListRepository.countByUserIdAndDeletedAtIsNullAndFailureCountGreaterThan(userId, 0) } returns 0

        val stats = service.getStats(userId)

        assertEquals(10, stats.bookmarkCount)
        assertEquals(3, stats.feedCount)
        assertEquals(150, stats.feedItemCount)
        assertEquals(42, stats.unreadFeedItemCount)
        assertEquals(2, stats.youtubeListCount)
        assertEquals(75, stats.videoCount)
        assertEquals(60, stats.downloadedVideoCount)
        assertEquals(5, stats.removedVideoCount)
        assertEquals(12, stats.tagCount)
        assertEquals(1_073_741_824L, stats.storageUsedBytes)
        assertEquals(1, stats.feedsWithFailures)
        assertEquals(0, stats.youtubeListsWithFailures)
    }
}
