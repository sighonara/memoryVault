package org.sightech.memoryvault.backup.provider

import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class InternetArchiveProvider(
    private val accessKey: String,
    private val secretKey: String
) : BackupProvider {

    private val log = LoggerFactory.getLogger(javaClass)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    fun itemIdentifier(youtubeVideoId: String): String = "yt-$youtubeVideoId"

    fun externalUrl(youtubeVideoId: String): String =
        "https://archive.org/details/${itemIdentifier(youtubeVideoId)}"

    fun buildMetadataHeaders(metadata: VideoBackupMetadata): Map<String, String?> {
        val headers = mutableMapOf<String, String?>()
        headers["x-archive-meta-mediatype"] = "movies"
        headers["x-archive-meta-title"] = metadata.title
        headers["x-archive-meta-description"] = metadata.description
        headers["x-archive-meta-youtube-video-id"] = metadata.youtubeVideoId
        headers["x-archive-meta-youtube-url"] = metadata.youtubeUrl
        headers["x-archive-meta-archived-by"] = "memoryvault"
        return headers
    }

    override fun search(youtubeVideoId: String): BackupSearchResult? {
        val query = "youtube-video-id:$youtubeVideoId"
        val url = "https://archive.org/advancedsearch.php?q=${java.net.URLEncoder.encode(query, Charsets.UTF_8)}&output=json&rows=1"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                log.warn("IA search returned status {} for videoId={}", response.statusCode(), youtubeVideoId)
                return null
            }
            val body = response.body()
            if (body.contains("\"numFound\":0") || body.contains("\"numFound\": 0")) {
                return null
            }
            val identifierRegex = """"identifier"\s*:\s*"([^"]+)"""".toRegex()
            val match = identifierRegex.find(body) ?: return null
            val identifier = match.groupValues[1]
            BackupSearchResult(
                externalId = identifier,
                externalUrl = "https://archive.org/details/$identifier"
            )
        } catch (e: Exception) {
            log.warn("IA search failed for videoId={}: {}", youtubeVideoId, e.message)
            null
        }
    }

    override fun upload(videoFile: InputStream, metadata: VideoBackupMetadata): BackupUploadResult {
        val identifier = itemIdentifier(metadata.youtubeVideoId)
        val url = "https://s3.us.archive.org/$identifier/${metadata.youtubeVideoId}.mp4"

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMinutes(30))
            .header("Authorization", "LOW $accessKey:$secretKey")
            .header("x-archive-auto-make-bucket", "1")

        val metadataHeaders = buildMetadataHeaders(metadata)
        for ((key, value) in metadataHeaders) {
            if (value != null) {
                requestBuilder.header(key, value)
            }
        }

        val request = requestBuilder
            .PUT(HttpRequest.BodyPublishers.ofInputStream { videoFile })
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("IA upload failed: HTTP ${response.statusCode()} — ${response.body()}")
        }

        log.info("Uploaded to IA: identifier={}", identifier)
        return BackupUploadResult(
            externalId = identifier,
            externalUrl = externalUrl(metadata.youtubeVideoId)
        )
    }

    override fun checkHealth(externalUrl: String): Boolean {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(externalUrl))
            .timeout(Duration.ofSeconds(15))
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            response.statusCode() in 200..399
        } catch (e: Exception) {
            log.warn("Health check failed for {}: {}", externalUrl, e.message)
            false
        }
    }

    override fun delete(externalId: String): Boolean {
        log.warn("Delete not supported on Internet Archive (by design). externalId={}", externalId)
        return false
    }
}
