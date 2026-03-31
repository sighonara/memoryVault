package org.sightech.memoryvault.youtube.service

import org.sightech.memoryvault.youtube.entity.Video
import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.sightech.memoryvault.youtube.repository.YoutubeListRepository
import org.sightech.memoryvault.websocket.VideoDownloadCompleted
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant

data class SyncResult(
    val list: YoutubeList,
    val newVideos: Int,
    val removedVideos: Int,
    val downloadSuccesses: Int,
    val downloadFailures: Int
)

@Service
class VideoSyncService(
    private val ytDlpService: YtDlpService,
    private val videoRepository: VideoRepository,
    private val youtubeListRepository: YoutubeListRepository,
    private val videoDownloader: VideoDownloader,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val logger = LoggerFactory.getLogger(VideoSyncService::class.java)

    fun syncList(list: YoutubeList, metadata: List<VideoMetadata>): SyncResult {
        val currentList = youtubeListRepository.findById(list.id).orElse(list)

        // TODO: This queries findByYoutubeListId twice (once for dedup, once for removal detection).
        //  Collapse into a single query when playlists grow large. Also, the repository query
        //  eagerly fetches tags (LEFT JOIN FETCH) which sync doesn't need — consider adding a
        //  lightweight findVideoIdsByYoutubeListId query.

        // Find new videos (not already in DB)
        val existingVideoIds = videoRepository.findByYoutubeListIdInAndYoutubeVideoIdIn(listOf(list.id), metadata.map { it.videoId })
            .map { it.youtubeVideoId }
            .toSet()

        val newMetadata = metadata.filter { it.videoId !in existingVideoIds }

        // Detect removals: videos in DB but not in incoming metadata
        val incomingVideoIds = metadata.map { it.videoId }.toSet()
        val removedVideos = videoRepository.findByYoutubeListIdAndUserId(list.id, list.userId)
            .filter { !it.removedFromYoutube && it.youtubeVideoId !in incomingVideoIds }

        for (video in removedVideos) {
            video.removedFromYoutube = true
            video.removedDetectedAt = Instant.now()
            video.updatedAt = Instant.now()
            videoRepository.save(video)
            logger.info("Video removed from YouTube: {} ({})", video.title, video.youtubeVideoId)
        }

        // Save new video records
        val newVideos = newMetadata.map { meta ->
            videoRepository.save(
                Video(
                    youtubeList = list,
                    youtubeVideoId = meta.videoId,
                    title = meta.title,
                    description = meta.description,
                    channelName = meta.channel,
                    youtubeUrl = meta.url,
                    durationSeconds = meta.durationSeconds
                )
            )
        }

        // Download new videos
        var downloadSuccesses = 0
        var downloadFailures = 0
        for (video in newVideos) {
            val result = videoDownloader.download(video.youtubeUrl, video.id)
            if (result.success && result.filePath != null) {
                video.filePath = result.filePath
                video.downloadedAt = Instant.now()
                video.updatedAt = Instant.now()
                videoRepository.save(video)
                downloadSuccesses++
            } else {
                logger.warn("Failed to download video {}: {}", video.youtubeVideoId, result.error)
                downloadFailures++
            }
            eventPublisher.publishEvent(VideoDownloadCompleted(
                userId = list.userId, timestamp = Instant.now(),
                videoId = video.id, listId = list.id,
                success = result.success && result.filePath != null
            ))
        }

        // Update list metadata
        if (metadata.isNotEmpty()) {
            currentList.lastSyncedAt = Instant.now()
            currentList.updatedAt = Instant.now()
            currentList.failureCount = 0
            youtubeListRepository.save(currentList)
        }

        return SyncResult(
            list = currentList,
            newVideos = newVideos.size,
            removedVideos = removedVideos.size,
            downloadSuccesses = downloadSuccesses,
            downloadFailures = downloadFailures
        )
    }
}
