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
