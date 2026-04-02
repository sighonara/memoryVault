package org.sightech.memoryvault.youtube.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.youtube.entity.Video
import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.sightech.memoryvault.youtube.repository.YoutubeListRepository
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class VideoSyncServiceTest {

    private val ytDlpService = mockk<YtDlpService>()
    private val videoRepository = mockk<VideoRepository>()
    private val youtubeListRepository = mockk<YoutubeListRepository>()
    private val videoDownloader = mockk<VideoDownloader>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val service = VideoSyncService(ytDlpService, videoRepository, youtubeListRepository, videoDownloader, eventPublisher)

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
        every { videoRepository.findByYoutubeListIdInAndYoutubeVideoIdIn(any(), any()) } returns emptyList()
        every { videoRepository.findByYoutubeListIdAndUserId(list.id, list.userId) } returns emptyList()
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
        every { videoRepository.findByYoutubeListIdInAndYoutubeVideoIdIn(any(), any()) } returns listOf(existingVideo)
        every { videoRepository.findByYoutubeListIdAndUserId(list.id, list.userId) } returns listOf(existingVideo)

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
        every { videoRepository.findByYoutubeListIdInAndYoutubeVideoIdIn(any(), any()) } returns listOf(existingVideo)
        every { videoRepository.findByYoutubeListIdAndUserId(list.id, list.userId) } returns listOf(existingVideo)

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
        every { videoRepository.findByYoutubeListIdInAndYoutubeVideoIdIn(any(), any()) } returns listOf(alreadyRemoved)
        every { videoRepository.findByYoutubeListIdAndUserId(list.id, list.userId) } returns listOf(alreadyRemoved)

        val result = service.syncList(list, emptyList())

        assertEquals(0, result.removedVideos) // already flagged, don't count again
    }

    @Test
    fun `syncList handles download failures gracefully`() {
        every { videoRepository.findByYoutubeListIdInAndYoutubeVideoIdIn(any(), any()) } returns emptyList()
        every { videoRepository.findByYoutubeListIdAndUserId(list.id, list.userId) } returns emptyList()
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
        every { videoRepository.findByYoutubeListIdInAndYoutubeVideoIdIn(any(), any()) } returns emptyList()
        every { videoRepository.findByYoutubeListIdAndUserId(list.id, list.userId) } returns emptyList()

        val metadata = listOf(
            VideoMetadata("vid1", "Video 1", "https://youtube.com/watch?v=vid1", "Channel", 100, "desc")
        )
        every { videoDownloader.download(any(), any()) } returns DownloadResult(success = true, filePath = "videos/test.mp4")

        service.syncList(list, metadata)

        verify { youtubeListRepository.save(match { it.lastSyncedAt != null }) }
    }
}
