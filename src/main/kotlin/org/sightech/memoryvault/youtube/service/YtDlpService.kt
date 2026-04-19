package org.sightech.memoryvault.youtube.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.TimeUnit

data class VideoMetadata(
    val videoId: String,
    val title: String?,
    val url: String,
    val channel: String?,
    val durationSeconds: Int?,
    val description: String?
)

data class DownloadResult(
    val success: Boolean,
    val filePath: String? = null,
    val error: String? = null
)

@Service
class YtDlpService(
    private val objectMapper: ObjectMapper,
    @Value("\${memoryvault.youtube.download-timeout-minutes:30}") private val downloadTimeoutMinutes: Long
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun fetchPlaylistMetadata(playlistUrl: String): List<VideoMetadata> {
        val process = try {
            ProcessBuilder(
                "yt-dlp", "--flat-playlist", "--dump-json", playlistUrl
            )
                .redirectErrorStream(false)
                .start()
        } catch (e: java.io.IOException) {
            log.error("Failed to start yt-dlp: {}", e.message)
            throw RuntimeException("yt-dlp not found or failed to start: ${e.message}", e)
        }

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            val error = process.errorStream.bufferedReader().readText()
            log.error("yt-dlp metadata fetch failed (exit {}): {}", exitCode, error)
            throw RuntimeException("yt-dlp failed with exit code $exitCode: $error")
        }

        return parsePlaylistJson(output)
    }

    fun parsePlaylistJson(ndjson: String): List<VideoMetadata> {
        return ndjson.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    val node = objectMapper.readTree(line)
                    val id = node.get("id")?.asText() ?: return@mapNotNull null
                    VideoMetadata(
                        videoId = id,
                        title = node.get("title")?.asText(),
                        url = node.get("url")?.asText() ?: "https://www.youtube.com/watch?v=$id",
                        channel = node.get("channel")?.asText(),
                        durationSeconds = node.get("duration")?.asInt(),
                        description = node.get("description")?.asText()
                    )
                } catch (e: Exception) {
                    log.warn("Skipping malformed yt-dlp JSON line: {}", e.message)
                    null
                }
            }
    }

    fun downloadVideo(videoUrl: String, outputPath: String): DownloadResult {
        log.info("Downloading video: {} -> {}", videoUrl, outputPath)

        val process = try {
            ProcessBuilder(
                "yt-dlp", "-o", outputPath, videoUrl
            )
                .redirectErrorStream(false)
                .start()
        } catch (e: java.io.IOException) {
            log.error("Failed to start yt-dlp: {}", e.message)
            return DownloadResult(success = false, error = "yt-dlp not found or failed to start: ${e.message}")
        }

        val finished = process.waitFor(downloadTimeoutMinutes, TimeUnit.MINUTES)
        if (!finished) {
            process.destroyForcibly()
            val msg = "yt-dlp timed out after ${downloadTimeoutMinutes}min"
            log.error(msg)
            return DownloadResult(success = false, error = msg)
        }

        val exitCode = process.exitValue()
        return if (exitCode == 0) {
            log.info("Download complete: {}", outputPath)
            DownloadResult(success = true, filePath = outputPath)
        } else {
            val error = process.errorStream.bufferedReader().readText()
            log.error("yt-dlp download failed (exit {}): {}", exitCode, error)
            DownloadResult(success = false, error = error)
        }
    }
}
