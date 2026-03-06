package org.sightech.memoryvault.youtube.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

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
class YtDlpService(private val objectMapper: ObjectMapper) {

    private val logger = LoggerFactory.getLogger(YtDlpService::class.java)

    fun fetchPlaylistMetadata(playlistUrl: String): List<VideoMetadata> {
        val process = ProcessBuilder(
            "yt-dlp", "--flat-playlist", "--dump-json", playlistUrl
        )
            .redirectErrorStream(false)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            val error = process.errorStream.bufferedReader().readText()
            logger.error("yt-dlp metadata fetch failed (exit {}): {}", exitCode, error)
            throw RuntimeException("yt-dlp failed with exit code $exitCode: $error")
        }

        return parsePlaylistJson(output)
    }

    fun parsePlaylistJson(ndjson: String): List<VideoMetadata> {
        return ndjson.lines()
            .filter { it.isNotBlank() }
            .map { line ->
                val node = objectMapper.readTree(line)
                val id = node.get("id")?.asText() ?: error("Missing id in yt-dlp output")
                VideoMetadata(
                    videoId = id,
                    title = node.get("title")?.asText(),
                    url = node.get("url")?.asText() ?: "https://www.youtube.com/watch?v=$id",
                    channel = node.get("channel")?.asText(),
                    durationSeconds = node.get("duration")?.asInt(),
                    description = node.get("description")?.asText()
                )
            }
    }

    fun downloadVideo(videoUrl: String, outputPath: String): DownloadResult {
        logger.info("Downloading video: {} -> {}", videoUrl, outputPath)

        val process = ProcessBuilder(
            "yt-dlp", "-o", outputPath, videoUrl
        )
            .redirectErrorStream(false)
            .start()

        val exitCode = process.waitFor()

        return if (exitCode == 0) {
            logger.info("Download complete: {}", outputPath)
            DownloadResult(success = true, filePath = outputPath)
        } else {
            val error = process.errorStream.bufferedReader().readText()
            logger.error("yt-dlp download failed (exit {}): {}", exitCode, error)
            DownloadResult(success = false, error = error)
        }
    }
}
