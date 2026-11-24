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
        const val MAX_LOGGED_STDERR_CHARS = 2000
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

        // Both pipes are drained on dedicated daemon threads (never the common pool) so neither
        // can fill and block the child, and the wait below can enforce the timeout.
        val stdout = PipeDrainer(process.inputStream, Int.MAX_VALUE, "yt-dlp-stdout").also { it.start() }
        val stderr = PipeDrainer(process.errorStream, MAX_LOGGED_STDERR_CHARS, "yt-dlp-stderr").also { it.start() }

        val finished = try {
            process.waitFor(METADATA_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            terminate(process)
        }

        if (!finished) {
            log.warn("yt-dlp metadata fetch timed out after {}s", METADATA_TIMEOUT_SECONDS)
            throw RuntimeException("yt-dlp metadata fetch timed out")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            log.warn("yt-dlp metadata fetch failed (exit {}): {}", exitCode, stderr.text())
            throw RuntimeException("yt-dlp failed with exit code $exitCode")
        }

        return parsePlaylistJson(stdout.text())
    }

    /** Kills the process if still alive, waits for it to exit so its pipes close. */
    private fun terminate(process: Process) {
        if (process.isAlive) {
            process.destroyForcibly()
            try {
                process.waitFor(5, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /** Reads a stream to EOF on its own daemon thread, keeping at most [maxChars] characters. */
    private class PipeDrainer(
        private val stream: java.io.InputStream,
        private val maxChars: Int,
        name: String
    ) : Thread(name) {
        private val buffer = StringBuilder()

        init {
            isDaemon = true
        }

        override fun run() {
            try {
                stream.bufferedReader().use { reader ->
                    val chunk = CharArray(8192)
                    while (true) {
                        val n = reader.read(chunk)
                        if (n < 0) break
                        synchronized(buffer) {
                            val room = maxChars - buffer.length
                            if (room > 0) buffer.append(chunk, 0, minOf(n, room))
                        }
                    }
                }
            } catch (e: java.io.IOException) {
                // stream closed because the process ended or was killed
            }
        }

        fun text(): String {
            join(5000)
            return synchronized(buffer) { buffer.toString() }
        }
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
            return DownloadResult(success = false, error = "yt-dlp failed to start")
        }

        val stdout = PipeDrainer(process.inputStream, 0, "yt-dlp-dl-stdout").also { it.start() }
        val stderr = PipeDrainer(process.errorStream, MAX_LOGGED_STDERR_CHARS, "yt-dlp-dl-stderr").also { it.start() }

        val finished = try {
            process.waitFor(downloadTimeoutMinutes, TimeUnit.MINUTES)
        } finally {
            terminate(process)
        }
        if (!finished) {
            val msg = "yt-dlp timed out after ${downloadTimeoutMinutes}min"
            log.error(msg)
            return DownloadResult(success = false, error = msg)
        }

        stdout.join(5000)
        val exitCode = process.exitValue()
        return if (exitCode == 0) {
            log.info("Download complete: {}", outputPath)
            DownloadResult(success = true, filePath = outputPath)
        } else {
            log.warn("yt-dlp download failed (exit {}): {}", exitCode, stderr.text())
            DownloadResult(success = false, error = "yt-dlp download failed (exit $exitCode)")
        }
    }
}
