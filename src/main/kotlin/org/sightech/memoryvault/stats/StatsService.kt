package org.sightech.memoryvault.stats

import org.sightech.memoryvault.bookmark.repository.BookmarkRepository
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.sightech.memoryvault.scheduling.service.SyncJobService
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.tag.repository.TagRepository
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.sightech.memoryvault.youtube.repository.YoutubeListRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

data class SystemStats(
    val bookmarkCount: Long,
    val feedCount: Long,
    val feedItemCount: Long,
    val unreadFeedItemCount: Long,
    val youtubeListCount: Long,
    val videoCount: Long,
    val downloadedVideoCount: Long,
    val removedVideoCount: Long,
    val tagCount: Long,
    val storageUsedBytes: Long,
    val lastFeedSync: Instant?,
    val lastYoutubeSync: Instant?,
    val feedsWithFailures: Long,
    val youtubeListsWithFailures: Long
)

@Service
class StatsService(
    private val bookmarkRepository: BookmarkRepository,
    private val feedRepository: FeedRepository,
    private val feedItemRepository: FeedItemRepository,
    private val youtubeListRepository: YoutubeListRepository,
    private val videoRepository: VideoRepository,
    private val tagRepository: TagRepository,
    private val syncJobService: SyncJobService,
    private val storageService: StorageService
) {

    // TODO: Phase 6 — add AWS stats: S3 bucket size, Lambda invocation counts/error rates,
    //  AWS cost per billing cycle (compute, storage, transfer) via Cost Explorer API,
    //  RDS connection/query metrics.

    fun getStats(userId: UUID): SystemStats {
        val lastFeedSync = syncJobService.findLastSuccessful(userId, "RSS_FETCH")?.completedAt
        val lastYoutubeSync = syncJobService.findLastSuccessful(userId, "YT_SYNC")?.completedAt

        return SystemStats(
            bookmarkCount = bookmarkRepository.countByUserIdAndDeletedAtIsNull(userId),
            feedCount = feedRepository.countByUserIdAndDeletedAtIsNull(userId),
            feedItemCount = feedItemRepository.countByFeedUserIdAndFeedDeletedAtIsNull(userId),
            unreadFeedItemCount = feedItemRepository.countByFeedUserIdAndFeedDeletedAtIsNullAndReadAtIsNull(userId),
            youtubeListCount = youtubeListRepository.countByUserIdAndDeletedAtIsNull(userId),
            videoCount = videoRepository.countByYoutubeListUserIdAndYoutubeListDeletedAtIsNull(userId),
            downloadedVideoCount = videoRepository.countByYoutubeListUserIdAndYoutubeListDeletedAtIsNullAndDownloadedAtIsNotNull(userId),
            removedVideoCount = videoRepository.countByYoutubeListUserIdAndYoutubeListDeletedAtIsNullAndRemovedFromYoutubeTrue(userId),
            tagCount = tagRepository.countByUserId(userId),
            storageUsedBytes = storageService.usedBytes(),
            lastFeedSync = lastFeedSync,
            lastYoutubeSync = lastYoutubeSync,
            feedsWithFailures = feedRepository.countByUserIdAndDeletedAtIsNullAndFailureCountGreaterThan(userId, 0),
            youtubeListsWithFailures = youtubeListRepository.countByUserIdAndDeletedAtIsNullAndFailureCountGreaterThan(userId, 0)
        )
    }
}
