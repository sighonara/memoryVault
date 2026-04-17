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

        val found = youtubeListRepository.findActiveByIdAndUserId(list.id, userId)
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
        val removed = videoRepository.findRemovedByYoutubeListIdAndUserId(list.id, userId)
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

        val videos = videoRepository.findByYoutubeListIdAndUserId(list.id, userId)
        assertEquals(1, videos.size)
    }

    @Test
    fun `soft delete youtube list`() {
        val list = youtubeListRepository.save(
            YoutubeList(userId = userId, youtubeListId = "PLdelete1", url = "https://youtube.com/playlist?list=PLdelete1")
        )

        list.deletedAt = java.time.Instant.now()
        youtubeListRepository.save(list)

        val found = youtubeListRepository.findActiveByIdAndUserId(list.id, userId)
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

        assertEquals(3L, videoRepository.countByYoutubeListIdAndUserId(list.id, userId))
        // AsyncVideoDownloader calls yt-dlp which isn't available in test; download fails
        // and leaves filePath null. Under the test profile the executor is synchronous so
        // the download attempt completes before this assertion runs.
        assertEquals(0L, videoRepository.countDownloadedByYoutubeListIdAndUserId(list.id, userId))
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

        val videos = videoRepository.findByYoutubeListIdAndUserId(list.id, userId)
        val video = videoService.getVideoStatus(videos[0].id)

        assertNotNull(video)
        assertEquals("Status Video", video.title)
        assertEquals("TestChannel", video.channelName)
        assertEquals(125, video.durationSeconds)
    }
}
