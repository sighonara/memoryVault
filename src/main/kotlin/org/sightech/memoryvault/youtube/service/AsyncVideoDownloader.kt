package org.sightech.memoryvault.youtube.service

import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.websocket.VideoDownloadCompleted
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.time.Instant
import java.util.UUID

@Component
class AsyncVideoDownloader(
    private val ytDlpService: YtDlpService,
    private val storageService: StorageService,
    private val videoRepository: VideoRepository,
    private val eventPublisher: ApplicationEventPublisher
) : VideoDownloader {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async("videoDownloadExecutor")
    @Transactional
    override fun download(youtubeUrl: String, videoId: UUID) {
        val video = videoRepository.findById(videoId).orElse(null)
        if (video == null) {
            log.warn("Video {} not found at download time (likely deleted)", videoId)
            return
        }

        val listId = video.youtubeList.id
        val userId = video.youtubeList.userId
        var success = false

        val tempDir = Files.createTempDirectory("memoryvault-dl-")
        try {
            val dlResult = ytDlpService.downloadVideo(youtubeUrl, tempDir.resolve("%(id)s.%(ext)s").toString())
            if (!dlResult.success) {
                log.warn("yt-dlp failed for video {}: {}", videoId, dlResult.error)
                video.downloadError = dlResult.error ?: "yt-dlp download failed"
                video.updatedAt = Instant.now()
                videoRepository.save(video)
                return
            }

            val downloadedFile = Files.list(tempDir).findFirst().orElse(null)
            if (downloadedFile == null) {
                log.warn("No output file for video {} after yt-dlp reported success", videoId)
                video.downloadError = "No output file after yt-dlp reported success"
                video.updatedAt = Instant.now()
                videoRepository.save(video)
                return
            }

            val storageKey = "videos/$videoId/${downloadedFile.fileName}"
            downloadedFile.toFile().inputStream().use { input -> storageService.store(storageKey, input) }

            val now = Instant.now()
            video.filePath = storageKey
            video.downloadedAt = now
            video.downloadError = null
            video.updatedAt = now
            videoRepository.save(video)
            success = true
            log.info("Video {} stored at key: {}", videoId, storageKey)
        } finally {
            tempDir.toFile().deleteRecursively()
            eventPublisher.publishEvent(
                VideoDownloadCompleted(
                    userId = userId,
                    timestamp = Instant.now(),
                    videoId = videoId,
                    listId = listId,
                    success = success
                )
            )
        }
    }
}
