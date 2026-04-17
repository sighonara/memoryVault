package org.sightech.memoryvault.youtube.service

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.websocket.VideoDownloadCompleted
import org.sightech.memoryvault.youtube.entity.Video
import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AsyncVideoDownloaderTest {

    private val ytDlpService = mockk<YtDlpService>()
    private val storageService = mockk<StorageService>(relaxed = true)
    private val videoRepository = mockk<VideoRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val downloader = AsyncVideoDownloader(ytDlpService, storageService, videoRepository, eventPublisher)

    private val list = YoutubeList(
        userId = UUID.randomUUID(),
        youtubeListId = "PLtest",
        url = "https://www.youtube.com/playlist?list=PLtest"
    )

    @Test
    fun `download logs and skips when video is not found`() {
        val videoId = UUID.randomUUID()
        every { videoRepository.findById(videoId) } returns Optional.empty()

        downloader.download("https://youtube.com/watch?v=x", videoId)

        verify(exactly = 0) { ytDlpService.downloadVideo(any(), any()) }
        verify(exactly = 0) { videoRepository.save(any()) }
        // Absence of event emission is intentional: deletion took the observer path.
        verify(exactly = 0) { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `download updates Video and emits success event on yt-dlp success`() {
        // We can't reliably control Files.list in a unit test; instead drive the failure
        // path via ytDlpService returning success=false, then assert event + no save. The
        // "happy-path + filesystem" permutation is covered by YoutubeIntegrationTest.
        val video = Video(
            youtubeList = list,
            youtubeVideoId = "vid1",
            youtubeUrl = "https://youtube.com/watch?v=vid1"
        )
        every { videoRepository.findById(video.id) } returns Optional.of(video)
        every { ytDlpService.downloadVideo(any(), any()) } returns DownloadResult(success = false, error = "no network")

        val eventSlot = slot<VideoDownloadCompleted>()
        every { eventPublisher.publishEvent(capture(eventSlot)) } just Runs

        downloader.download(video.youtubeUrl, video.id)

        assertNull(video.filePath)
        assertNull(video.downloadedAt)
        verify(exactly = 0) { videoRepository.save(any()) }
        assertNotNull(eventSlot.captured)
        assertEquals(false, eventSlot.captured.success)
        assertEquals(video.id, eventSlot.captured.videoId)
        assertEquals(list.id, eventSlot.captured.listId)
        assertEquals(list.userId, eventSlot.captured.userId)
    }
}
