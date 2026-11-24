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

    private companion object {
        const val METADATA_TIMEOUT_SECONDS = 120L
    }

    fun fetchPlaylistMetadata(playlistUrl: String): List<VideoMetadata> {
        YoutubeUrlValidator.requireYoutubeUrl(playlistUrl)
        val process = try {
            ProcessBuilder(metadataCommand(playlistUrl))
                .redirectErrorStream(false)
                .start()
        } catch (e: java.io.IOException) {
            log.error("Failed to start yt-dlp: {}", e.message)
            throw RuntimeException("yt-dlp not found or failed to start: ${e.message}", e)
        }

        // Drain both pipes concurrently so neither can fill and block the child.
        val stdout = java.util.concurrent.CompletableFuture.supplyAsync {
            process.inputStream.bufferedReader().readText()
        }
        val stderr = java.util.concurrent.CompletableFuture.supplyAsync {
            process.errorStream.bufferedReader().readText()
        }

        if (!process.waitFor(METADATA_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            log.warn("yt-dlp metadata fetch timed out after {}s", METADATA_TIMEOUT_SECONDS)
            throw RuntimeException("yt-dlp metadata fetch timed out")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            val error = try { stderr.get(5, TimeUnit.SECONDS) } catch (e: Exception) { "" }
            log.warn("yt-dlp metadata fetch failed (exit {}): {}", exitCode, error)
            throw RuntimeException("yt-dlp failed with exit code $exitCode")
        }

        return parsePlaylistJson(stdout.get(30, TimeUnit.SECONDS))
    }

    internal fun metadataCommand(url: String): List<String> =
        listOf("yt-dlp", "--flat-playlist", "--dump-json", "--", url)

    internal fun downloadCommand(url: String, out: String): List<String> =
        listOf("yt-dlp", "-o", out, "--", url)

    fun parsePlaylistJson(ndjson: String): List<VideoMetadata> {
        return ndjson.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    val node = objectMapper.readTree(line)
                    val id = node.get("id")?.stringValue() ?: return@mapNotNull null
                    VideoMetadata(
                        videoId = id,
                        title = node.get("title")?.stringValue(),
                        url = node.get("url")?.stringValue() ?: "https://www.youtube.com/watch?v=$id",
                        channel = node.get("channel")?.stringValue(),
                        durationSeconds = node.get("duration")?.intValue(),
                        description = node.get("description")?.stringValue()
                    )
                } catch (e: Exception) {
                    log.warn("Skipping malformed yt-dlp JSON line: {}", e.message)
                    null
                }
            }
    }

    fun downloadVideo(videoUrl: String, outputPath: String): DownloadResult {
        YoutubeUrlValidator.requireYoutubeUrl(videoUrl)
        log.info("Downloading video: {} -> {}", videoUrl, outputPath)

        val process = try {
            ProcessBuilder(downloadCommand(videoUrl, outputPath))
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
