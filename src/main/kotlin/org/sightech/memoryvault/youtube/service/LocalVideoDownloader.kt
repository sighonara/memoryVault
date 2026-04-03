package org.sightech.memoryvault.youtube.service

import org.sightech.memoryvault.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.util.UUID

@Component
@Profile("local | test")
class LocalVideoDownloader(
    private val ytDlpService: YtDlpService,
    private val storageService: StorageService
) : VideoDownloader {

    private val logger = LoggerFactory.getLogger(LocalVideoDownloader::class.java)

    override fun download(youtubeUrl: String, videoId: UUID): DownloadResult {
        val tempDir = Files.createTempDirectory("memoryvault-dl-")
        val outputTemplate = tempDir.resolve("%(id)s.%(ext)s").toString()

        try {
            val dlResult = ytDlpService.downloadVideo(youtubeUrl, outputTemplate)
            if (!dlResult.success) return dlResult

            // yt-dlp replaces %(id)s and %(ext)s — find the actual output file
            // TODO: Filter out .part files in case of interrupted downloads (yt-dlp can leave
            //  partial files alongside completed ones).
            val downloadedFile = Files.list(tempDir).findFirst().orElse(null)
                ?: return DownloadResult(success = false, error = "No output file found after download")

            val storageKey = "videos/$videoId/${downloadedFile.fileName}"
            downloadedFile.toFile().inputStream().use { inputStream ->
                storageService.store(storageKey, inputStream)
            }

            logger.info("Video {} stored at key: {}", videoId, storageKey)
            return DownloadResult(success = true, filePath = storageKey)
        } finally {
            // Clean up temp directory
            tempDir.toFile().deleteRecursively()
        }
    }
}
