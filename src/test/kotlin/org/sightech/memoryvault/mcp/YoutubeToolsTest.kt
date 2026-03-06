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
