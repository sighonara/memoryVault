# Phase 3: YouTube Archival — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Track YouTube playlists and archive every video. Concrete local implementations for downloading and storage, stubs for AWS Lambda + S3.

**Architecture:** JPA entities for YoutubeList and Video (tables already exist). YtDlpService wraps the CLI. VideoDownloader and StorageService interfaces with local concrete + AWS stubs. 6 MCP tools. Reuses JobScheduler from Phase 2.

**Tech Stack:** Spring Boot 4.x, Kotlin, yt-dlp CLI, ProcessBuilder, Spring profiles, MockK, TestContainers

**GIT COMMIT RULE:** You MUST use exactly `git commit -m "your message here"` with a plain double-quoted string. NEVER use `$()`, heredocs, `cat`, or subshell patterns in commit commands.

---

### Task 1: Configuration and Dependencies

**Files:**
- Modify: `src/main/resources/application.properties`

**Step 1: Add YouTube and storage config properties**

Add to the end of `src/main/resources/application.properties`:

```properties
# YouTube sync schedule (cron syntax). Set to "-" to disable automatic sync.
memoryvault.youtube.sync-cron=-

# Local storage path for downloaded videos and other files
memoryvault.storage.local-path=${user.home}/.memoryvault/storage
```

No new dependencies needed — yt-dlp is invoked via ProcessBuilder (already in JDK), and all Spring dependencies are already present.

**Step 2: Commit**

```bash
git add src/main/resources/application.properties
git commit -m "feat: add YouTube sync and local storage config properties"
```

---

### Task 2: YoutubeList and Video JPA Entities

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/youtube/entity/YoutubeList.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/youtube/entity/Video.kt`

**Step 1: Create YoutubeList entity**

Create `src/main/kotlin/org/sightech/memoryvault/youtube/entity/YoutubeList.kt`:

```kotlin
package org.sightech.memoryvault.youtube.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "youtube_lists")
class YoutubeList(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "youtube_list_id", nullable = false, length = 255)
    val youtubeListId: String,

    @Column(nullable = false, length = 2048)
    val url: String,

    @Column(length = 500)
    var name: String? = null,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "last_synced_at")
    var lastSyncedAt: Instant? = null,

    @Column(name = "failure_count", nullable = false)
    var failureCount: Int = 0,

    val createdAt: Instant = Instant.now(),

    var updatedAt: Instant = Instant.now(),

    var deletedAt: Instant? = null,

    @Version
    val version: Long = 0
)
```

**Step 2: Create Video entity**

Create `src/main/kotlin/org/sightech/memoryvault/youtube/entity/Video.kt`:

```kotlin
package org.sightech.memoryvault.youtube.entity

import jakarta.persistence.*
import org.sightech.memoryvault.tag.entity.Tag
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "videos")
class Video(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "youtube_list_id", nullable = false)
    val youtubeList: YoutubeList,

    @Column(name = "youtube_video_id", nullable = false, length = 255)
    val youtubeVideoId: String,

    @Column(length = 500)
    var title: String? = null,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "channel_name", length = 255)
    var channelName: String? = null,

    @Column(name = "thumbnail_path", length = 1024)
    var thumbnailPath: String? = null,

    @Column(name = "youtube_url", nullable = false, length = 2048)
    val youtubeUrl: String,

    @Column(name = "file_path", length = 1024)
    var filePath: String? = null,

    @Column(name = "downloaded_at")
    var downloadedAt: Instant? = null,

    @Column(name = "duration_seconds")
    var durationSeconds: Int? = null,

    @Column(name = "removed_from_youtube", nullable = false)
    var removedFromYoutube: Boolean = false,

    @Column(name = "removed_detected_at")
    var removedDetectedAt: Instant? = null,

    val createdAt: Instant = Instant.now(),

    var updatedAt: Instant = Instant.now(),

    @ManyToMany
    @JoinTable(
        name = "video_tags",
        joinColumns = [JoinColumn(name = "video_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")]
    )
    val tags: MutableSet<Tag> = mutableSetOf()
)
```

**Step 3: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/youtube/entity/YoutubeList.kt src/main/kotlin/org/sightech/memoryvault/youtube/entity/Video.kt
git commit -m "feat: add YoutubeList and Video JPA entities"
```

---

### Task 3: Repositories

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/youtube/repository/YoutubeListRepository.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/youtube/repository/VideoRepository.kt`

**Step 1: Create YoutubeListRepository**

Create `src/main/kotlin/org/sightech/memoryvault/youtube/repository/YoutubeListRepository.kt`:

```kotlin
package org.sightech.memoryvault.youtube.repository

import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface YoutubeListRepository : JpaRepository<YoutubeList, UUID> {

    @Query("SELECT yl FROM YoutubeList yl WHERE yl.deletedAt IS NULL AND yl.userId = :userId ORDER BY yl.name")
    fun findAllActiveByUserId(userId: UUID): List<YoutubeList>

    @Query("SELECT yl FROM YoutubeList yl WHERE yl.id = :id AND yl.deletedAt IS NULL")
    fun findActiveById(id: UUID): YoutubeList?
}
```

**Step 2: Create VideoRepository**

Create `src/main/kotlin/org/sightech/memoryvault/youtube/repository/VideoRepository.kt`:

```kotlin
package org.sightech.memoryvault.youtube.repository

import org.sightech.memoryvault.youtube.entity.Video
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface VideoRepository : JpaRepository<Video, UUID> {

    @Query("SELECT v FROM Video v LEFT JOIN FETCH v.tags WHERE v.youtubeList.id = :listId ORDER BY v.createdAt DESC")
    fun findByYoutubeListId(listId: UUID): List<Video>

    @Query("SELECT v FROM Video v LEFT JOIN FETCH v.tags WHERE v.youtubeList.id = :listId AND v.removedFromYoutube = true ORDER BY v.removedDetectedAt DESC")
    fun findRemovedByYoutubeListId(listId: UUID): List<Video>

    fun existsByYoutubeListIdAndYoutubeVideoId(youtubeListId: UUID, youtubeVideoId: String): Boolean

    fun findByYoutubeListIdAndYoutubeVideoId(youtubeListId: UUID, youtubeVideoId: String): Video?

    @Query("SELECT COUNT(v) FROM Video v WHERE v.youtubeList.id = :listId")
    fun countByYoutubeListId(listId: UUID): Long

    @Query("SELECT COUNT(v) FROM Video v WHERE v.youtubeList.id = :listId AND v.downloadedAt IS NOT NULL")
    fun countDownloadedByYoutubeListId(listId: UUID): Long

    @Query("SELECT COUNT(v) FROM Video v WHERE v.youtubeList.id = :listId AND v.removedFromYoutube = true")
    fun countRemovedByYoutubeListId(listId: UUID): Long

    @Query("SELECT v FROM Video v LEFT JOIN FETCH v.tags WHERE v.youtubeList.id IN :listIds AND v.youtubeVideoId IN :videoIds")
    fun findByYoutubeListIdInAndYoutubeVideoIdIn(listIds: List<UUID>, videoIds: List<String>): List<Video>
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/youtube/repository/YoutubeListRepository.kt src/main/kotlin/org/sightech/memoryvault/youtube/repository/VideoRepository.kt
git commit -m "feat: add YoutubeListRepository and VideoRepository"
```

---

### Task 4: StorageService Interface and Implementations

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/storage/StorageService.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/storage/LocalStorageService.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/storage/S3StorageService.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/storage/LocalStorageServiceTest.kt`

**Step 1: Create StorageService interface**

Create `src/main/kotlin/org/sightech/memoryvault/storage/StorageService.kt`:

```kotlin
package org.sightech.memoryvault.storage

import java.io.InputStream

interface StorageService {
    fun store(key: String, inputStream: InputStream): String
    fun retrieve(key: String): InputStream
    fun delete(key: String)
    fun exists(key: String): Boolean
}
```

**Step 2: Create LocalStorageService**

Create `src/main/kotlin/org/sightech/memoryvault/storage/LocalStorageService.kt`:

```kotlin
package org.sightech.memoryvault.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Component
@Profile("!aws")
class LocalStorageService(
    @Value("\${memoryvault.storage.local-path:\${user.home}/.memoryvault/storage}")
    private val basePath: String
) : StorageService {

    private val logger = LoggerFactory.getLogger(LocalStorageService::class.java)

    private fun resolve(key: String): Path = Path.of(basePath).resolve(key)

    override fun store(key: String, inputStream: InputStream): String {
        val target = resolve(key)
        Files.createDirectories(target.parent)
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING)
        logger.info("Stored file: {}", target)
        return target.toString()
    }

    override fun retrieve(key: String): InputStream {
        val target = resolve(key)
        return Files.newInputStream(target)
    }

    override fun delete(key: String) {
        val target = resolve(key)
        Files.deleteIfExists(target)
        logger.info("Deleted file: {}", target)
    }

    override fun exists(key: String): Boolean {
        return Files.exists(resolve(key))
    }
}
```

**Step 3: Create S3StorageService stub**

Create `src/main/kotlin/org/sightech/memoryvault/storage/S3StorageService.kt`:

```kotlin
package org.sightech.memoryvault.storage

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.InputStream

// AWS S3 implementation of StorageService.
//
// When activated (spring.profiles.active=aws), this replaces LocalStorageService.
//
// Implementation notes for Phase 6:
// - Use AWS SDK v2 S3Client (software.amazon.awssdk:s3)
// - Configure via properties:
//     memoryvault.storage.s3-bucket=memoryvault-storage
//     memoryvault.storage.s3-region=us-east-1
// - store(): Use S3Client.putObject() for small files, S3TransferManager for large videos
//   (multipart upload threshold ~100MB). The key becomes the S3 object key.
// - retrieve(): Use S3Client.getObject() to return an InputStream. For web UI access,
//   consider generating pre-signed URLs (S3Presigner) with 1-hour expiry instead.
// - delete(): Use S3Client.deleteObject()
// - exists(): Use S3Client.headObject(), catch NoSuchKeyException
// - Consider S3 lifecycle policies for cost optimization:
//   - Move to Infrequent Access after 90 days
//   - Move to Glacier Deep Archive after 365 days (videos unlikely to be re-watched)
// - Bucket should have versioning enabled for accidental deletion protection

@Component
@Profile("aws")
class S3StorageService : StorageService {

    private val logger = LoggerFactory.getLogger(S3StorageService::class.java)

    override fun store(key: String, inputStream: InputStream): String {
        logger.warn("S3StorageService.store() is a stub — AWS implementation pending (Phase 6)")
        throw UnsupportedOperationException("S3 storage not yet implemented. Use 'local' profile for development.")
    }

    override fun retrieve(key: String): InputStream {
        throw UnsupportedOperationException("S3 storage not yet implemented. Use 'local' profile for development.")
    }

    override fun delete(key: String) {
        throw UnsupportedOperationException("S3 storage not yet implemented. Use 'local' profile for development.")
    }

    override fun exists(key: String): Boolean {
        throw UnsupportedOperationException("S3 storage not yet implemented. Use 'local' profile for development.")
    }
}
```

**Step 4: Write LocalStorageService test**

Create `src/test/kotlin/org/sightech/memoryvault/storage/LocalStorageServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.storage

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalStorageServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var service: LocalStorageService

    @BeforeEach
    fun setUp() {
        service = LocalStorageService(tempDir.toString())
    }

    @Test
    fun `store saves file and returns path`() {
        val content = "test content"
        val result = service.store("videos/test.mp4", content.byteInputStream())

        assertTrue(result.endsWith("videos/test.mp4"))
        assertTrue(service.exists("videos/test.mp4"))
    }

    @Test
    fun `retrieve returns stored content`() {
        val content = "hello world"
        service.store("test.txt", content.byteInputStream())

        val retrieved = service.retrieve("test.txt").bufferedReader().readText()
        assertEquals(content, retrieved)
    }

    @Test
    fun `delete removes file`() {
        service.store("test.txt", "content".byteInputStream())
        assertTrue(service.exists("test.txt"))

        service.delete("test.txt")
        assertFalse(service.exists("test.txt"))
    }

    @Test
    fun `exists returns false for missing file`() {
        assertFalse(service.exists("nonexistent.txt"))
    }

    @Test
    fun `store creates parent directories`() {
        service.store("a/b/c/deep.txt", "deep".byteInputStream())
        assertTrue(service.exists("a/b/c/deep.txt"))
    }
}
```

**Step 5: Run tests**

Run: `./gradlew test --tests "*LocalStorageServiceTest"`
Expected: All 5 tests pass

**Step 6: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/storage/ src/test/kotlin/org/sightech/memoryvault/storage/
git commit -m "feat: add StorageService interface with local and S3 stub implementations"
```

---

### Task 5: YtDlpService

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/youtube/service/YtDlpService.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/youtube/service/YtDlpServiceTest.kt`
- Create: `src/test/resources/fixtures/sample-playlist.json`

**Step 1: Create the yt-dlp JSON fixture**

Create `src/test/resources/fixtures/sample-playlist.json`. This simulates `yt-dlp --flat-playlist --dump-json` output — one JSON object per line (NDJSON):

```json
{"id": "dQw4w9WgXcQ", "title": "Rick Astley - Never Gonna Give You Up", "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "channel": "Rick Astley", "duration": 212, "description": "The official video for Never Gonna Give You Up"}
{"id": "9bZkp7q19f0", "title": "PSY - GANGNAM STYLE", "url": "https://www.youtube.com/watch?v=9bZkp7q19f0", "channel": "officialpsy", "duration": 253, "description": "PSY - GANGNAM STYLE Music Video"}
{"id": "kJQP7kiw5Fk", "title": "Luis Fonsi - Despacito ft. Daddy Yankee", "url": "https://www.youtube.com/watch?v=kJQP7kiw5Fk", "channel": "Luis Fonsi", "duration": 282, "description": "Despacito official music video"}
```

**Step 2: Create data classes for yt-dlp output**

Create `src/main/kotlin/org/sightech/memoryvault/youtube/service/YtDlpService.kt`:

```kotlin
package org.sightech.memoryvault.youtube.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

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

@JsonIgnoreProperties(ignoreUnknown = true)
data class YtDlpJsonOutput(
    val id: String,
    val title: String? = null,
    val url: String? = null,
    val channel: String? = null,
    val duration: Int? = null,
    val description: String? = null
)

@Service
class YtDlpService {

    private val logger = LoggerFactory.getLogger(YtDlpService::class.java)
    private val mapper = jacksonObjectMapper()

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
                val json = mapper.readValue<YtDlpJsonOutput>(line)
                VideoMetadata(
                    videoId = json.id,
                    title = json.title,
                    url = json.url ?: "https://www.youtube.com/watch?v=${json.id}",
                    channel = json.channel,
                    durationSeconds = json.duration,
                    description = json.description
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
```

**Step 3: Write YtDlpService test (parsing only — no real yt-dlp calls)**

Create `src/test/kotlin/org/sightech/memoryvault/youtube/service/YtDlpServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.youtube.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class YtDlpServiceTest {

    private val service = YtDlpService()
    private val sampleJson = this::class.java.classLoader.getResource("fixtures/sample-playlist.json")!!.readText()

    @Test
    fun `parsePlaylistJson parses all videos`() {
        val result = service.parsePlaylistJson(sampleJson)
        assertEquals(3, result.size)
    }

    @Test
    fun `parsePlaylistJson extracts video metadata`() {
        val result = service.parsePlaylistJson(sampleJson)
        val first = result[0]

        assertEquals("dQw4w9WgXcQ", first.videoId)
        assertEquals("Rick Astley - Never Gonna Give You Up", first.title)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", first.url)
        assertEquals("Rick Astley", first.channel)
        assertEquals(212, first.durationSeconds)
    }

    @Test
    fun `parsePlaylistJson generates URL from ID when url is null`() {
        val json = """{"id": "abc123", "title": "Test"}"""
        val result = service.parsePlaylistJson(json)

        assertEquals("https://www.youtube.com/watch?v=abc123", result[0].url)
    }

    @Test
    fun `parsePlaylistJson skips blank lines`() {
        val jsonWithBlanks = sampleJson + "\n\n\n"
        val result = service.parsePlaylistJson(jsonWithBlanks)
        assertEquals(3, result.size)
    }
}
```

**Step 4: Run tests**

Run: `./gradlew test --tests "*YtDlpServiceTest"`
Expected: All 4 tests pass

**Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/youtube/service/YtDlpService.kt src/test/kotlin/org/sightech/memoryvault/youtube/service/YtDlpServiceTest.kt src/test/resources/fixtures/sample-playlist.json
git commit -m "feat: add YtDlpService with playlist metadata parsing"
```

---

### Task 6: VideoDownloader Interface and Implementations

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/youtube/service/VideoDownloader.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/youtube/service/LocalVideoDownloader.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/youtube/service/LambdaVideoDownloader.kt`

**Step 1: Create VideoDownloader interface**

Create `src/main/kotlin/org/sightech/memoryvault/youtube/service/VideoDownloader.kt`:

```kotlin
package org.sightech.memoryvault.youtube.service

import java.util.UUID

interface VideoDownloader {
    fun download(youtubeUrl: String, videoId: UUID): DownloadResult
}
```

**Step 2: Create LocalVideoDownloader**

Create `src/main/kotlin/org/sightech/memoryvault/youtube/service/LocalVideoDownloader.kt`:

```kotlin
package org.sightech.memoryvault.youtube.service

import org.sightech.memoryvault.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.util.UUID

@Component
@Profile("!aws")
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
```

**Step 3: Create LambdaVideoDownloader stub**

Create `src/main/kotlin/org/sightech/memoryvault/youtube/service/LambdaVideoDownloader.kt`:

```kotlin
package org.sightech.memoryvault.youtube.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

// AWS Lambda implementation of VideoDownloader.
//
// When activated (spring.profiles.active=aws), this replaces LocalVideoDownloader.
//
// Implementation notes for Phase 6:
// - Use AWS SDK v2 LambdaClient (software.amazon.awssdk:lambda)
// - Configure via properties:
//     memoryvault.youtube.lambda-function-name=memoryvault-video-downloader
//     memoryvault.youtube.s3-bucket=memoryvault-storage
//
// - download() should invoke the Lambda asynchronously (InvocationType.EVENT):
//     LambdaClient.invoke(InvokeRequest.builder()
//         .functionName(lambdaFunctionName)
//         .invocationType(InvocationType.EVENT)
//         .payload(SdkBytes.fromUtf8String(json))
//         .build())
//
// - Lambda payload shape:
//     {
//       "videoId": "<uuid>",
//       "youtubeUrl": "<url>",
//       "s3Bucket": "<bucket>",
//       "s3Key": "videos/<videoId>/<filename>"
//     }
//
// - The Lambda function (Python, content-processor/):
//     1. Invokes yt-dlp to download the video to /tmp
//     2. Uploads to S3 using boto3 multipart upload
//     3. Updates the videos table directly via psycopg2:
//        SET file_path = s3Key, downloaded_at = now(), updated_at = now()
//     4. Alternatively, posts a completion message to an SQS queue
//        that Spring Boot consumes to update the DB
//
// - Lambda timeout: 15 minutes (max). For very long videos, consider
//   ECS Fargate tasks instead of Lambda.
// - Lambda memory: 1024MB minimum (yt-dlp + ffmpeg need RAM)
// - Lambda layers: yt-dlp and ffmpeg as Lambda layers or bundled in container image

@Component
@Profile("aws")
class LambdaVideoDownloader : VideoDownloader {

    private val logger = LoggerFactory.getLogger(LambdaVideoDownloader::class.java)

    override fun download(youtubeUrl: String, videoId: UUID): DownloadResult {
        logger.warn("LambdaVideoDownloader.download() is a stub — AWS Lambda invocation pending (Phase 6)")
        return DownloadResult(
            success = false,
            error = "Lambda video downloader not yet implemented. Use 'local' profile for development."
        )
    }
}
```

**Step 4: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/youtube/service/VideoDownloader.kt src/main/kotlin/org/sightech/memoryvault/youtube/service/LocalVideoDownloader.kt src/main/kotlin/org/sightech/memoryvault/youtube/service/LambdaVideoDownloader.kt
git commit -m "feat: add VideoDownloader interface with local and Lambda stub implementations"
```

---

### Task 7: VideoSyncService

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/youtube/service/VideoSyncService.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/youtube/service/VideoSyncServiceTest.kt`

**Step 1: Create VideoSyncService**

Create `src/main/kotlin/org/sightech/memoryvault/youtube/service/VideoSyncService.kt`:

```kotlin
package org.sightech.memoryvault.youtube.service

import org.sightech.memoryvault.youtube.entity.Video
import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.sightech.memoryvault.youtube.repository.YoutubeListRepository
import org.slf4j.LoggerFactory
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
    private val videoDownloader: VideoDownloader
) {

    private val logger = LoggerFactory.getLogger(VideoSyncService::class.java)

    fun syncList(list: YoutubeList, metadata: List<VideoMetadata>): SyncResult {
        val currentList = youtubeListRepository.findById(list.id).orElse(list)

        // Find new videos (not already in DB)
        val existingVideoIds = videoRepository.findByYoutubeListId(list.id)
            .map { it.youtubeVideoId }
            .toSet()

        val newMetadata = metadata.filter { it.videoId !in existingVideoIds }

        // Detect removals: videos in DB but not in incoming metadata
        val incomingVideoIds = metadata.map { it.videoId }.toSet()
        val removedVideos = videoRepository.findByYoutubeListId(list.id)
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
```

**Step 2: Write VideoSyncService tests**

Create `src/test/kotlin/org/sightech/memoryvault/youtube/service/VideoSyncServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.youtube.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.youtube.entity.Video
import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.sightech.memoryvault.youtube.repository.YoutubeListRepository
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class VideoSyncServiceTest {

    private val ytDlpService = mockk<YtDlpService>()
    private val videoRepository = mockk<VideoRepository>()
    private val youtubeListRepository = mockk<YoutubeListRepository>()
    private val videoDownloader = mockk<VideoDownloader>()

    private val service = VideoSyncService(ytDlpService, videoRepository, youtubeListRepository, videoDownloader)

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val list = YoutubeList(userId = userId, youtubeListId = "PLtest", url = "https://youtube.com/playlist?list=PLtest")

    @BeforeEach
    fun setUp() {
        every { youtubeListRepository.findById(list.id) } returns Optional.of(list)
        every { youtubeListRepository.save(any()) } answers { firstArg() }
        every { videoRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `syncList creates new video records and downloads them`() {
        every { videoRepository.findByYoutubeListId(list.id) } returns emptyList()
        every { videoDownloader.download(any(), any()) } returns DownloadResult(success = true, filePath = "videos/test.mp4")

        val metadata = listOf(
            VideoMetadata("vid1", "Video 1", "https://youtube.com/watch?v=vid1", "Channel", 100, "desc")
        )

        val result = service.syncList(list, metadata)

        assertEquals(1, result.newVideos)
        assertEquals(0, result.removedVideos)
        assertEquals(1, result.downloadSuccesses)
        verify(exactly = 2) { videoRepository.save(any()) } // once for create, once for download update
    }

    @Test
    fun `syncList skips existing videos`() {
        val existingVideo = Video(
            youtubeList = list,
            youtubeVideoId = "vid1",
            youtubeUrl = "https://youtube.com/watch?v=vid1"
        )
        every { videoRepository.findByYoutubeListId(list.id) } returns listOf(existingVideo)

        val metadata = listOf(
            VideoMetadata("vid1", "Video 1", "https://youtube.com/watch?v=vid1", "Channel", 100, "desc"),
            VideoMetadata("vid2", "Video 2", "https://youtube.com/watch?v=vid2", "Channel", 200, "desc")
        )
        every { videoDownloader.download(any(), any()) } returns DownloadResult(success = true, filePath = "videos/test.mp4")

        val result = service.syncList(list, metadata)

        assertEquals(1, result.newVideos) // only vid2 is new
    }

    @Test
    fun `syncList detects removed videos`() {
        val existingVideo = Video(
            youtubeList = list,
            youtubeVideoId = "vid1",
            youtubeUrl = "https://youtube.com/watch?v=vid1"
        )
        every { videoRepository.findByYoutubeListId(list.id) } returns listOf(existingVideo)

        // Empty metadata = vid1 was removed
        val result = service.syncList(list, emptyList())

        assertEquals(0, result.newVideos)
        assertEquals(1, result.removedVideos)
        verify { videoRepository.save(match { it.removedFromYoutube && it.removedDetectedAt != null }) }
    }

    @Test
    fun `syncList does not re-flag already removed videos`() {
        val alreadyRemoved = Video(
            youtubeList = list,
            youtubeVideoId = "vid1",
            youtubeUrl = "https://youtube.com/watch?v=vid1"
        ).apply { removedFromYoutube = true }
        every { videoRepository.findByYoutubeListId(list.id) } returns listOf(alreadyRemoved)

        val result = service.syncList(list, emptyList())

        assertEquals(0, result.removedVideos) // already flagged, don't count again
    }

    @Test
    fun `syncList handles download failures gracefully`() {
        every { videoRepository.findByYoutubeListId(list.id) } returns emptyList()
        every { videoDownloader.download(any(), any()) } returns DownloadResult(success = false, error = "network error")

        val metadata = listOf(
            VideoMetadata("vid1", "Video 1", "https://youtube.com/watch?v=vid1", "Channel", 100, "desc")
        )

        val result = service.syncList(list, metadata)

        assertEquals(1, result.newVideos)
        assertEquals(0, result.downloadSuccesses)
        assertEquals(1, result.downloadFailures)
    }

    @Test
    fun `syncList updates list lastSyncedAt`() {
        every { videoRepository.findByYoutubeListId(list.id) } returns emptyList()

        val metadata = listOf(
            VideoMetadata("vid1", "Video 1", "https://youtube.com/watch?v=vid1", "Channel", 100, "desc")
        )
        every { videoDownloader.download(any(), any()) } returns DownloadResult(success = true, filePath = "videos/test.mp4")

        service.syncList(list, metadata)

        verify { youtubeListRepository.save(match { it.lastSyncedAt != null }) }
    }
}
```

**Step 3: Run tests**

Run: `./gradlew test --tests "*VideoSyncServiceTest"`
Expected: All 6 tests pass

**Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/youtube/service/VideoSyncService.kt src/test/kotlin/org/sightech/memoryvault/youtube/service/VideoSyncServiceTest.kt
git commit -m "feat: add VideoSyncService with sync, download, and removal detection"
```

---

### Task 8: YoutubeListService and VideoService

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/youtube/service/YoutubeListService.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/youtube/service/VideoService.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/youtube/service/YoutubeListServiceTest.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/youtube/service/VideoServiceTest.kt`

**Step 1: Create YoutubeListService**

Create `src/main/kotlin/org/sightech/memoryvault/youtube/service/YoutubeListService.kt`:

```kotlin
package org.sightech.memoryvault.youtube.service

import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.sightech.memoryvault.youtube.repository.YoutubeListRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

data class ListStats(
    val totalVideos: Long,
    val downloadedVideos: Long,
    val removedVideos: Long
)

@Service
class YoutubeListService(
    private val youtubeListRepository: YoutubeListRepository,
    private val videoRepository: VideoRepository,
    private val ytDlpService: YtDlpService,
    private val videoSyncService: VideoSyncService
) {

    companion object {
        val SYSTEM_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    fun addList(url: String): Pair<YoutubeList, SyncResult> {
        val playlistId = extractPlaylistId(url)
        val list = youtubeListRepository.save(
            YoutubeList(userId = SYSTEM_USER_ID, youtubeListId = playlistId, url = url)
        )

        val metadata = ytDlpService.fetchPlaylistMetadata(url)

        // Update list name from first video's playlist info (yt-dlp doesn't always include playlist title in flat mode)
        if (list.name == null && metadata.isNotEmpty()) {
            list.name = "Playlist $playlistId"
            list.updatedAt = Instant.now()
            youtubeListRepository.save(list)
        }

        val syncResult = videoSyncService.syncList(list, metadata)
        return list to syncResult
    }

    fun listLists(): List<Pair<YoutubeList, ListStats>> {
        val lists = youtubeListRepository.findAllActiveByUserId(SYSTEM_USER_ID)
        return lists.map { list ->
            val stats = ListStats(
                totalVideos = videoRepository.countByYoutubeListId(list.id),
                downloadedVideos = videoRepository.countDownloadedByYoutubeListId(list.id),
                removedVideos = videoRepository.countRemovedByYoutubeListId(list.id)
            )
            list to stats
        }
    }

    fun deleteList(listId: UUID): YoutubeList? {
        val list = youtubeListRepository.findActiveById(listId) ?: return null
        list.deletedAt = Instant.now()
        list.updatedAt = Instant.now()
        return youtubeListRepository.save(list)
    }

    fun refreshList(listId: UUID?): List<SyncResult> {
        val lists = if (listId != null) {
            val list = youtubeListRepository.findActiveById(listId) ?: return emptyList()
            listOf(list)
        } else {
            youtubeListRepository.findAllActiveByUserId(SYSTEM_USER_ID)
        }

        return lists.map { list ->
            try {
                val metadata = ytDlpService.fetchPlaylistMetadata(list.url)
                videoSyncService.syncList(list, metadata)
            } catch (e: Exception) {
                val currentList = youtubeListRepository.findById(list.id).orElse(list)
                currentList.failureCount++
                currentList.updatedAt = Instant.now()
                youtubeListRepository.save(currentList)
                SyncResult(list = currentList, newVideos = 0, removedVideos = 0, downloadSuccesses = 0, downloadFailures = 0)
            }
        }
    }

    private fun extractPlaylistId(url: String): String {
        val regex = Regex("[?&]list=([^&]+)")
        return regex.find(url)?.groupValues?.get(1) ?: url
    }
}
```

**Step 2: Create VideoService**

Create `src/main/kotlin/org/sightech/memoryvault/youtube/service/VideoService.kt`:

```kotlin
package org.sightech.memoryvault.youtube.service

import org.sightech.memoryvault.youtube.entity.Video
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class VideoService(private val videoRepository: VideoRepository) {

    fun getVideos(listId: UUID?, query: String?, removedOnly: Boolean): List<Video> {
        if (listId == null && query == null && !removedOnly) return emptyList()

        val videos = if (listId != null) {
            if (removedOnly) {
                videoRepository.findRemovedByYoutubeListId(listId)
            } else {
                videoRepository.findByYoutubeListId(listId)
            }
        } else {
            emptyList()
        }

        return if (query != null) {
            val q = query.lowercase()
            videos.filter { v ->
                (v.title?.lowercase()?.contains(q) == true) ||
                    (v.channelName?.lowercase()?.contains(q) == true)
            }
        } else {
            videos
        }
    }

    fun getVideoStatus(videoId: UUID): Video? {
        return videoRepository.findById(videoId).orElse(null)
    }
}
```

**Step 3: Write YoutubeListService tests**

Create `src/test/kotlin/org/sightech/memoryvault/youtube/service/YoutubeListServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.youtube.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.sightech.memoryvault.youtube.repository.YoutubeListRepository
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class YoutubeListServiceTest {

    private val youtubeListRepository = mockk<YoutubeListRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val ytDlpService = mockk<YtDlpService>()
    private val videoSyncService = mockk<VideoSyncService>()

    private val service = YoutubeListService(youtubeListRepository, videoRepository, ytDlpService, videoSyncService)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setUp() {
        every { youtubeListRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `addList creates list and syncs`() {
        val metadata = listOf(
            VideoMetadata("vid1", "Video 1", "https://youtube.com/watch?v=vid1", "Ch", 100, "d")
        )
        every { ytDlpService.fetchPlaylistMetadata(any()) } returns metadata
        every { videoSyncService.syncList(any(), any()) } returns SyncResult(
            list = YoutubeList(userId = userId, youtubeListId = "PLtest", url = "https://youtube.com/playlist?list=PLtest"),
            newVideos = 1, removedVideos = 0, downloadSuccesses = 1, downloadFailures = 0
        )

        val (list, result) = service.addList("https://youtube.com/playlist?list=PLtest")

        assertEquals("PLtest", list.youtubeListId)
        assertEquals(1, result.newVideos)
    }

    @Test
    fun `listLists returns lists with stats`() {
        val list = YoutubeList(userId = userId, youtubeListId = "PLtest", url = "https://youtube.com/playlist?list=PLtest", name = "Test")
        every { youtubeListRepository.findAllActiveByUserId(userId) } returns listOf(list)
        every { videoRepository.countByYoutubeListId(list.id) } returns 10
        every { videoRepository.countDownloadedByYoutubeListId(list.id) } returns 7
        every { videoRepository.countRemovedByYoutubeListId(list.id) } returns 2

        val result = service.listLists()

        assertEquals(1, result.size)
        assertEquals(10, result[0].second.totalVideos)
        assertEquals(7, result[0].second.downloadedVideos)
        assertEquals(2, result[0].second.removedVideos)
    }

    @Test
    fun `deleteList soft deletes`() {
        val list = YoutubeList(userId = userId, youtubeListId = "PLtest", url = "https://youtube.com/playlist?list=PLtest")
        every { youtubeListRepository.findActiveById(list.id) } returns list

        val result = service.deleteList(list.id)

        assertNotNull(result?.deletedAt)
    }

    @Test
    fun `deleteList returns null for missing list`() {
        every { youtubeListRepository.findActiveById(any()) } returns null
        assertNull(service.deleteList(UUID.randomUUID()))
    }

    @Test
    fun `refreshList handles fetch failure by incrementing failureCount`() {
        val list = YoutubeList(userId = userId, youtubeListId = "PLtest", url = "https://youtube.com/playlist?list=PLtest")
        every { youtubeListRepository.findActiveById(list.id) } returns list
        every { youtubeListRepository.findById(list.id) } returns Optional.of(list)
        every { ytDlpService.fetchPlaylistMetadata(any()) } throws RuntimeException("network error")

        val results = service.refreshList(list.id)

        assertEquals(1, results.size)
        assertEquals(0, results[0].newVideos)
        verify { youtubeListRepository.save(match { it.failureCount == 1 }) }
    }

    @Test
    fun `extractPlaylistId parses list parameter from URL`() {
        every { ytDlpService.fetchPlaylistMetadata(any()) } returns emptyList()
        every { videoSyncService.syncList(any(), any()) } returns SyncResult(
            list = YoutubeList(userId = userId, youtubeListId = "PLabc123", url = "test"),
            newVideos = 0, removedVideos = 0, downloadSuccesses = 0, downloadFailures = 0
        )

        val (list, _) = service.addList("https://www.youtube.com/playlist?list=PLabc123&other=param")
        assertEquals("PLabc123", list.youtubeListId)
    }
}
```

**Step 4: Write VideoService tests**

Create `src/test/kotlin/org/sightech/memoryvault/youtube/service/VideoServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.youtube.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.youtube.entity.Video
import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.repository.VideoRepository
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VideoServiceTest {

    private val videoRepository = mockk<VideoRepository>()
    private val service = VideoService(videoRepository)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val list = YoutubeList(userId = userId, youtubeListId = "PLtest", url = "https://youtube.com/playlist?list=PLtest")

    @Test
    fun `getVideos returns all videos for a list`() {
        val videos = listOf(
            Video(youtubeList = list, youtubeVideoId = "v1", youtubeUrl = "https://youtube.com/watch?v=v1", title = "First"),
            Video(youtubeList = list, youtubeVideoId = "v2", youtubeUrl = "https://youtube.com/watch?v=v2", title = "Second")
        )
        every { videoRepository.findByYoutubeListId(list.id) } returns videos

        val result = service.getVideos(list.id, null, false)
        assertEquals(2, result.size)
    }

    @Test
    fun `getVideos filters removed only`() {
        val removed = listOf(
            Video(youtubeList = list, youtubeVideoId = "v1", youtubeUrl = "https://youtube.com/watch?v=v1").apply { removedFromYoutube = true }
        )
        every { videoRepository.findRemovedByYoutubeListId(list.id) } returns removed

        val result = service.getVideos(list.id, null, true)
        assertEquals(1, result.size)
    }

    @Test
    fun `getVideos filters by query on title`() {
        val videos = listOf(
            Video(youtubeList = list, youtubeVideoId = "v1", youtubeUrl = "url1", title = "Kotlin Tutorial"),
            Video(youtubeList = list, youtubeVideoId = "v2", youtubeUrl = "url2", title = "Java Tutorial")
        )
        every { videoRepository.findByYoutubeListId(list.id) } returns videos

        val result = service.getVideos(list.id, "kotlin", false)
        assertEquals(1, result.size)
        assertEquals("Kotlin Tutorial", result[0].title)
    }

    @Test
    fun `getVideos returns empty when no listId and no query`() {
        val result = service.getVideos(null, null, false)
        assertEquals(0, result.size)
    }

    @Test
    fun `getVideoStatus returns video when found`() {
        val video = Video(youtubeList = list, youtubeVideoId = "v1", youtubeUrl = "url1", title = "Test")
        every { videoRepository.findById(video.id) } returns Optional.of(video)

        val result = service.getVideoStatus(video.id)
        assertNotNull(result)
    }

    @Test
    fun `getVideoStatus returns null when not found`() {
        every { videoRepository.findById(any()) } returns Optional.empty()
        assertNull(service.getVideoStatus(UUID.randomUUID()))
    }
}
```

**Step 5: Run tests**

Run: `./gradlew test --tests "*YoutubeListServiceTest" --tests "*VideoServiceTest"`
Expected: All tests pass

**Step 6: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/youtube/service/YoutubeListService.kt src/main/kotlin/org/sightech/memoryvault/youtube/service/VideoService.kt src/test/kotlin/org/sightech/memoryvault/youtube/service/YoutubeListServiceTest.kt src/test/kotlin/org/sightech/memoryvault/youtube/service/VideoServiceTest.kt
git commit -m "feat: add YoutubeListService and VideoService"
```

---

### Task 9: YoutubeSyncRegistrar

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/youtube/YoutubeSyncRegistrar.kt`

**Step 1: Create YoutubeSyncRegistrar**

Create `src/main/kotlin/org/sightech/memoryvault/youtube/YoutubeSyncRegistrar.kt`:

```kotlin
package org.sightech.memoryvault.youtube

import org.sightech.memoryvault.scheduling.JobScheduler
import org.sightech.memoryvault.youtube.service.YoutubeListService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class YoutubeSyncRegistrar(
    private val jobScheduler: JobScheduler,
    private val youtubeListService: YoutubeListService,
    @Value("\${memoryvault.youtube.sync-cron:-}") private val syncCron: String
) {

    private val logger = LoggerFactory.getLogger(YoutubeSyncRegistrar::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun registerYoutubeSyncJob() {
        jobScheduler.schedule("youtube-sync", syncCron) {
            logger.info("YouTube sync job starting")
            val results = youtubeListService.refreshList(null)
            val totalNew = results.sumOf { it.newVideos }
            val totalRemoved = results.sumOf { it.removedVideos }
            logger.info("YouTube sync complete: {} lists synced, {} new videos, {} removals detected",
                results.size, totalNew, totalRemoved)
        }
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/youtube/YoutubeSyncRegistrar.kt
git commit -m "feat: register YouTube sync job on application startup"
```

---

### Task 10: YoutubeTools (MCP)

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/mcp/YoutubeTools.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/mcp/YoutubeToolsTest.kt`

**Step 1: Create YoutubeTools**

Create `src/main/kotlin/org/sightech/memoryvault/mcp/YoutubeTools.kt`:

```kotlin
package org.sightech.memoryvault.mcp

import org.sightech.memoryvault.youtube.service.VideoService
import org.sightech.memoryvault.youtube.service.YoutubeListService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class YoutubeTools(
    private val youtubeListService: YoutubeListService,
    private val videoService: VideoService
) {

    @Tool(description = "Subscribe to a YouTube playlist for archival. Immediately fetches metadata and queues all videos for download. Use when the user wants to archive or track a YouTube playlist.")
    fun addYoutubeList(url: String): String {
        val (list, syncResult) = youtubeListService.addList(url)
        return "Added playlist: \"${list.name ?: list.url}\" — ${syncResult.newVideos} video(s) found, " +
            "${syncResult.downloadSuccesses} downloaded, ${syncResult.downloadFailures} failed — id: ${list.id}"
    }

    @Tool(description = "List all tracked YouTube playlists with video counts and download progress. Use when the user wants to see their archived playlists.")
    fun listYoutubeLists(): String {
        val lists = youtubeListService.listLists()
        if (lists.isEmpty()) return "No playlists tracked."

        val lines = lists.map { (list, stats) ->
            "- ${list.name ?: list.url} — ${stats.downloadedVideos}/${stats.totalVideos} downloaded" +
                (if (stats.removedVideos > 0) ", ${stats.removedVideos} removed" else "") +
                " — id: ${list.id}"
        }
        return "${lists.size} playlist(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Browse archived videos. Filter by playlist, search by title or channel, or show only videos removed from YouTube. Use when the user wants to see their archived videos.")
    fun listArchivedVideos(listId: String?, query: String?, removedOnly: Boolean?): String {
        val videos = videoService.getVideos(
            listId?.let { UUID.fromString(it) },
            query,
            removedOnly ?: false
        )
        if (videos.isEmpty()) return "No videos found."

        val lines = videos.map { v ->
            val status = when {
                v.removedFromYoutube -> "[REMOVED]"
                v.downloadedAt != null -> "[downloaded]"
                else -> "[pending]"
            }
            val tagStr = if (v.tags.isNotEmpty()) " [${v.tags.joinToString(", ") { it.name }}]" else ""
            "- $status ${v.title ?: "(no title)"} — ${v.channelName ?: "unknown"}$tagStr — id: ${v.id}"
        }
        return "${videos.size} video(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Get detailed status for a single archived video. Shows download status, file path, and whether it was removed from YouTube. Use when the user asks about a specific video.")
    fun getVideoStatus(videoId: String): String {
        val video = videoService.getVideoStatus(UUID.fromString(videoId))
            ?: return "Video not found."

        val lines = mutableListOf<String>()
        lines.add("Title: ${video.title ?: "(no title)"}")
        lines.add("Channel: ${video.channelName ?: "unknown"}")
        lines.add("YouTube URL: ${video.youtubeUrl}")
        lines.add("Duration: ${video.durationSeconds?.let { "${it / 60}m ${it % 60}s" } ?: "unknown"}")

        if (video.downloadedAt != null) {
            lines.add("Status: Downloaded")
            lines.add("File: ${video.filePath}")
            lines.add("Downloaded at: ${video.downloadedAt}")
        } else {
            lines.add("Status: Pending download")
        }

        if (video.removedFromYoutube) {
            lines.add("REMOVED from YouTube (detected: ${video.removedDetectedAt})")
        }

        if (video.tags.isNotEmpty()) {
            lines.add("Tags: ${video.tags.joinToString(", ") { it.name }}")
        }

        return lines.joinToString("\n")
    }

    @Tool(description = "Re-sync one or all YouTube playlists. Fetches latest metadata, detects removed videos, and downloads new ones. Pass a listId to refresh one playlist, or omit to refresh all.")
    fun refreshYoutubeList(listId: String?): String {
        val results = youtubeListService.refreshList(listId?.let { UUID.fromString(it) })
        if (results.isEmpty()) return "No playlists to refresh."

        val lines = results.map { r ->
            "- ${r.list.name ?: r.list.url}: ${r.newVideos} new, ${r.removedVideos} removed, " +
                "${r.downloadSuccesses} downloaded, ${r.downloadFailures} failed"
        }
        return "Refreshed ${results.size} playlist(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Remove a YouTube playlist from tracking. This is a soft delete — the playlist and its videos can be recovered. Use when the user wants to stop archiving a playlist.")
    fun deleteYoutubeList(listId: String): String {
        val list = youtubeListService.deleteList(UUID.fromString(listId))
            ?: return "Playlist not found."
        return "Deleted playlist: \"${list.name ?: list.url}\""
    }
}
```

**Step 2: Write YoutubeTools tests**

Create `src/test/kotlin/org/sightech/memoryvault/mcp/YoutubeToolsTest.kt`:

```kotlin
package org.sightech.memoryvault.mcp

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.youtube.entity.Video
import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.service.*
import java.time.Instant
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals

class YoutubeToolsTest {

    private val youtubeListService = mockk<YoutubeListService>()
    private val videoService = mockk<VideoService>()
    private val tools = YoutubeTools(youtubeListService, videoService)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val list = YoutubeList(userId = userId, youtubeListId = "PLtest", url = "https://youtube.com/playlist?list=PLtest", name = "Test Playlist")

    @Test
    fun `addYoutubeList returns summary`() {
        every { youtubeListService.addList(any()) } returns (list to SyncResult(
            list = list, newVideos = 5, removedVideos = 0, downloadSuccesses = 4, downloadFailures = 1
        ))

        val result = tools.addYoutubeList("https://youtube.com/playlist?list=PLtest")
        assertContains(result, "Test Playlist")
        assertContains(result, "5 video(s)")
        assertContains(result, "4 downloaded")
    }

    @Test
    fun `listYoutubeLists returns list with stats`() {
        every { youtubeListService.listLists() } returns listOf(
            list to ListStats(totalVideos = 10, downloadedVideos = 7, removedVideos = 2)
        )

        val result = tools.listYoutubeLists()
        assertContains(result, "Test Playlist")
        assertContains(result, "7/10 downloaded")
        assertContains(result, "2 removed")
    }

    @Test
    fun `listYoutubeLists returns message when empty`() {
        every { youtubeListService.listLists() } returns emptyList()
        assertEquals("No playlists tracked.", tools.listYoutubeLists())
    }

    @Test
    fun `listArchivedVideos returns formatted list`() {
        val videos = listOf(
            Video(youtubeList = list, youtubeVideoId = "v1", youtubeUrl = "url1", title = "Video 1", channelName = "Channel").apply {
                downloadedAt = Instant.now()
            }
        )
        every { videoService.getVideos(list.id, null, false) } returns videos

        val result = tools.listArchivedVideos(list.id.toString(), null, null)
        assertContains(result, "[downloaded]")
        assertContains(result, "Video 1")
    }

    @Test
    fun `listArchivedVideos shows removed status`() {
        val videos = listOf(
            Video(youtubeList = list, youtubeVideoId = "v1", youtubeUrl = "url1", title = "Gone Video").apply {
                removedFromYoutube = true
            }
        )
        every { videoService.getVideos(list.id, null, true) } returns videos

        val result = tools.listArchivedVideos(list.id.toString(), null, true)
        assertContains(result, "[REMOVED]")
    }

    @Test
    fun `getVideoStatus returns detailed info`() {
        val video = Video(youtubeList = list, youtubeVideoId = "v1", youtubeUrl = "https://youtube.com/watch?v=v1", title = "Test Video", channelName = "TestChannel", durationSeconds = 125)
        video.downloadedAt = Instant.now()
        video.filePath = "videos/test.mp4"
        every { videoService.getVideoStatus(video.id) } returns video

        val result = tools.getVideoStatus(video.id.toString())
        assertContains(result, "Test Video")
        assertContains(result, "TestChannel")
        assertContains(result, "Downloaded")
        assertContains(result, "videos/test.mp4")
        assertContains(result, "2m 5s")
    }

    @Test
    fun `getVideoStatus returns not found`() {
        every { videoService.getVideoStatus(any()) } returns null
        assertEquals("Video not found.", tools.getVideoStatus(UUID.randomUUID().toString()))
    }

    @Test
    fun `getVideoStatus shows removed status`() {
        val video = Video(youtubeList = list, youtubeVideoId = "v1", youtubeUrl = "url1", title = "Gone")
        video.removedFromYoutube = true
        video.removedDetectedAt = Instant.now()
        every { videoService.getVideoStatus(video.id) } returns video

        val result = tools.getVideoStatus(video.id.toString())
        assertContains(result, "REMOVED from YouTube")
    }

    @Test
    fun `refreshYoutubeList returns sync summary`() {
        every { youtubeListService.refreshList(null) } returns listOf(
            SyncResult(list = list, newVideos = 3, removedVideos = 1, downloadSuccesses = 2, downloadFailures = 1)
        )

        val result = tools.refreshYoutubeList(null)
        assertContains(result, "3 new")
        assertContains(result, "1 removed")
    }

    @Test
    fun `deleteYoutubeList returns confirmation`() {
        val deleted = YoutubeList(userId = userId, youtubeListId = "PLtest", url = "url", name = "Test Playlist").apply { deletedAt = Instant.now() }
        every { youtubeListService.deleteList(deleted.id) } returns deleted

        val result = tools.deleteYoutubeList(deleted.id.toString())
        assertContains(result, "Deleted playlist")
        assertContains(result, "Test Playlist")
    }

    @Test
    fun `deleteYoutubeList returns not found`() {
        every { youtubeListService.deleteList(any()) } returns null
        assertEquals("Playlist not found.", tools.deleteYoutubeList(UUID.randomUUID().toString()))
    }
}
```

**Step 3: Run tests**

Run: `./gradlew test --tests "*YoutubeToolsTest"`
Expected: All 11 tests pass

**Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/mcp/YoutubeTools.kt src/test/kotlin/org/sightech/memoryvault/mcp/YoutubeToolsTest.kt
git commit -m "feat: add YoutubeTools with 6 MCP tools"
```

---

### Task 11: YoutubeController REST Stub

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/youtube/controller/YoutubeController.kt`

**Step 1: Create YoutubeController**

Create `src/main/kotlin/org/sightech/memoryvault/youtube/controller/YoutubeController.kt`:

```kotlin
package org.sightech.memoryvault.youtube.controller

import org.sightech.memoryvault.youtube.service.YoutubeListService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/youtube")
class YoutubeController(private val youtubeListService: YoutubeListService) {

    @GetMapping("/lists")
    fun listLists() = youtubeListService.listLists().map { (list, stats) ->
        mapOf(
            "id" to list.id,
            "name" to list.name,
            "url" to list.url,
            "totalVideos" to stats.totalVideos,
            "downloadedVideos" to stats.downloadedVideos,
            "removedVideos" to stats.removedVideos
        )
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/youtube/controller/YoutubeController.kt
git commit -m "feat: add YoutubeController stub with list endpoint"
```

---

### Task 12: Integration Tests

**Files:**
- Create: `src/test/kotlin/org/sightech/memoryvault/youtube/YoutubeIntegrationTest.kt`

**Step 1: Write integration tests**

Create `src/test/kotlin/org/sightech/memoryvault/youtube/YoutubeIntegrationTest.kt`:

```kotlin
package org.sightech.memoryvault.youtube

import org.junit.jupiter.api.Test
import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.sightech.memoryvault.youtube.repository.YoutubeListRepository
import org.sightech.memoryvault.youtube.service.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class YoutubeIntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("memoryvault_test")
            withUsername("memoryvault")
            withPassword("memoryvault")
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired lateinit var youtubeListRepository: YoutubeListRepository
    @Autowired lateinit var videoRepository: VideoRepository
    @Autowired lateinit var videoSyncService: VideoSyncService
    @Autowired lateinit var videoService: VideoService

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `save and retrieve youtube list`() {
        val list = youtubeListRepository.save(
            YoutubeList(userId = userId, youtubeListId = "PLintegration1", url = "https://youtube.com/playlist?list=PLintegration1", name = "Integration Test")
        )

        val found = youtubeListRepository.findActiveById(list.id)
        assertNotNull(found)
        assertEquals("Integration Test", found.name)
    }

    @Test
    fun `syncList creates videos and detects removals`() {
        val list = youtubeListRepository.save(
            YoutubeList(userId = userId, youtubeListId = "PLsync1", url = "https://youtube.com/playlist?list=PLsync1")
        )

        // First sync: 3 videos
        val metadata1 = listOf(
            VideoMetadata("v1", "Video 1", "https://youtube.com/watch?v=v1", "Ch", 100, "d"),
            VideoMetadata("v2", "Video 2", "https://youtube.com/watch?v=v2", "Ch", 200, "d"),
            VideoMetadata("v3", "Video 3", "https://youtube.com/watch?v=v3", "Ch", 300, "d")
        )
        val result1 = videoSyncService.syncList(list, metadata1)
        assertEquals(3, result1.newVideos)

        // Second sync: v3 missing = removed
        val metadata2 = listOf(
            VideoMetadata("v1", "Video 1", "https://youtube.com/watch?v=v1", "Ch", 100, "d"),
            VideoMetadata("v2", "Video 2", "https://youtube.com/watch?v=v2", "Ch", 200, "d")
        )
        val result2 = videoSyncService.syncList(list, metadata2)
        assertEquals(0, result2.newVideos)
        assertEquals(1, result2.removedVideos)

        // Verify v3 is flagged
        val removed = videoRepository.findRemovedByYoutubeListId(list.id)
        assertEquals(1, removed.size)
        assertEquals("v3", removed[0].youtubeVideoId)
    }

    @Test
    fun `deduplication prevents duplicate videos`() {
        val list = youtubeListRepository.save(
            YoutubeList(userId = userId, youtubeListId = "PLdedup1", url = "https://youtube.com/playlist?list=PLdedup1")
        )

        val metadata = listOf(
            VideoMetadata("v1", "Video 1", "https://youtube.com/watch?v=v1", "Ch", 100, "d")
        )

        val first = videoSyncService.syncList(list, metadata)
        assertEquals(1, first.newVideos)

        val second = videoSyncService.syncList(list, metadata)
        assertEquals(0, second.newVideos)

        val videos = videoRepository.findByYoutubeListId(list.id)
        assertEquals(1, videos.size)
    }

    @Test
    fun `soft delete youtube list`() {
        val list = youtubeListRepository.save(
            YoutubeList(userId = userId, youtubeListId = "PLdelete1", url = "https://youtube.com/playlist?list=PLdelete1")
        )

        list.deletedAt = java.time.Instant.now()
        youtubeListRepository.save(list)

        val found = youtubeListRepository.findActiveById(list.id)
        assertTrue(found == null)
    }

    @Test
    fun `video counts are correct`() {
        val list = youtubeListRepository.save(
            YoutubeList(userId = userId, youtubeListId = "PLcounts1", url = "https://youtube.com/playlist?list=PLcounts1")
        )

        val metadata = listOf(
            VideoMetadata("vc1", "Video 1", "https://youtube.com/watch?v=vc1", "Ch", 100, "d"),
            VideoMetadata("vc2", "Video 2", "https://youtube.com/watch?v=vc2", "Ch", 200, "d"),
            VideoMetadata("vc3", "Video 3", "https://youtube.com/watch?v=vc3", "Ch", 300, "d")
        )
        videoSyncService.syncList(list, metadata)

        assertEquals(3, videoRepository.countByYoutubeListId(list.id))
        // Downloaded count depends on VideoDownloader mock — in integration it uses LocalVideoDownloader
        // which would fail without yt-dlp. So downloaded count will be 0 here.
        assertEquals(0, videoRepository.countDownloadedByYoutubeListId(list.id))
    }

    @Test
    fun `getVideoStatus returns video with details`() {
        val list = youtubeListRepository.save(
            YoutubeList(userId = userId, youtubeListId = "PLstatus1", url = "https://youtube.com/playlist?list=PLstatus1")
        )

        val metadata = listOf(
            VideoMetadata("vs1", "Status Video", "https://youtube.com/watch?v=vs1", "TestChannel", 125, "desc")
        )
        videoSyncService.syncList(list, metadata)

        val videos = videoRepository.findByYoutubeListId(list.id)
        val video = videoService.getVideoStatus(videos[0].id)

        assertNotNull(video)
        assertEquals("Status Video", video.title)
        assertEquals("TestChannel", video.channelName)
        assertEquals(125, video.durationSeconds)
    }
}
```

Note: Integration tests use the real `LocalVideoDownloader` which calls yt-dlp. Since yt-dlp won't be available in CI/test environments, downloads will fail gracefully (download count = 0). The sync and metadata logic is still fully tested.

**Step 2: Run integration tests**

Run: `./gradlew test --tests "*YoutubeIntegrationTest"`
Expected: All 6 tests pass (download failures are expected and handled gracefully)

**Step 3: Commit**

```bash
git add src/test/kotlin/org/sightech/memoryvault/youtube/YoutubeIntegrationTest.kt
git commit -m "feat: add YouTube integration tests"
```

---

### Task 13: Test Script

**Files:**
- Create: `scripts/test-youtube.sh`

**Step 1: Create test script**

Create `scripts/test-youtube.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "=== MemoryVault: YouTube tests ==="
echo ""

echo "--- Unit tests ---"
./gradlew test --tests "*YtDlpServiceTest" --tests "*VideoSyncServiceTest" --tests "*YoutubeListServiceTest" --tests "*VideoServiceTest" --tests "*YoutubeToolsTest" --tests "*LocalStorageServiceTest"

echo ""
echo "--- Integration tests ---"
./gradlew test --tests "*YoutubeIntegrationTest"

echo ""
echo "=== All YouTube tests passed ==="
```

**Step 2: Make executable**

```bash
chmod +x scripts/test-youtube.sh
```

**Step 3: Run it**

Run: `./scripts/test-youtube.sh`
Expected: All unit and integration tests pass

**Step 4: Commit**

```bash
git add scripts/test-youtube.sh
git commit -m "feat: add test-youtube.sh script"
```

---

## Summary

| Task | Component | Tests |
|------|-----------|-------|
| 1 | Config properties | — |
| 2 | YoutubeList + Video entities | — |
| 3 | Repositories | — |
| 4 | StorageService + LocalStorageService + S3 stub | 5 unit |
| 5 | YtDlpService | 4 unit |
| 6 | VideoDownloader + Local + Lambda stub | — |
| 7 | VideoSyncService | 6 unit |
| 8 | YoutubeListService + VideoService | 12 unit |
| 9 | YoutubeSyncRegistrar | — |
| 10 | YoutubeTools (6 MCP tools) | 11 unit |
| 11 | YoutubeController stub | — |
| 12 | Integration tests | 6 integration |
| 13 | Test script | — |

**Total: 13 tasks, ~44 tests**
