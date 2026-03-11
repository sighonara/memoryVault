package org.sightech.memoryvault.youtube.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.sightech.memoryvault.youtube.repository.YoutubeListRepository
import org.sightech.memoryvault.auth.CurrentUser
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional
import java.util.UUID
import kotlin.test.AfterTest
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
        MockKAnnotations.init(this)
        every { youtubeListRepository.save(any()) } answers { firstArg() }
        
        // Mock SecurityContext to return our test userId
        val securityContext = mockk<SecurityContext>()
        val authentication = mockk<Authentication>()
        every { securityContext.authentication } returns authentication
        every { authentication.principal } returns userId.toString()
        SecurityContextHolder.setContext(securityContext)
    }

    @AfterTest
    fun tearDown() {
        SecurityContextHolder.clearContext()
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
        every { videoRepository.countByYoutubeListIdAndUserId(list.id, userId) } returns 10
        every { videoRepository.countDownloadedByYoutubeListIdAndUserId(list.id, userId) } returns 7
        every { videoRepository.countRemovedByYoutubeListIdAndUserId(list.id, userId) } returns 2

        val result = service.listLists()

        assertEquals(1, result.size)
        assertEquals(10, result[0].second.totalVideos)
        assertEquals(7, result[0].second.downloadedVideos)
        assertEquals(2, result[0].second.removedVideos)
    }

    @Test
    fun `deleteList soft deletes`() {
        val list = YoutubeList(userId = userId, youtubeListId = "PLtest", url = "https://youtube.com/playlist?list=PLtest")
        every { youtubeListRepository.findActiveByIdAndUserId(list.id, userId) } returns list

        val result = service.deleteList(list.id)

        assertNotNull(result?.deletedAt)
    }

    @Test
    fun `deleteList returns null for missing list`() {
        every { youtubeListRepository.findActiveByIdAndUserId(any(), userId) } returns null
        assertNull(service.deleteList(UUID.randomUUID()))
    }

    @Test
    fun `refreshList handles fetch failure by incrementing failureCount`() {
        val list = YoutubeList(userId = userId, youtubeListId = "PLtest", url = "https://youtube.com/playlist?list=PLtest")
        every { youtubeListRepository.findActiveByIdAndUserId(list.id, userId) } returns list
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
