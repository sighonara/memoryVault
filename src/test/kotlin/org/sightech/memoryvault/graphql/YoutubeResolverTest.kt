package org.sightech.memoryvault.graphql

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.youtube.entity.Video
import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.service.ListStats
import org.sightech.memoryvault.youtube.service.VideoService
import org.sightech.memoryvault.youtube.service.YoutubeListService
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class YoutubeResolverTest {

    private val youtubeListService = mockk<YoutubeListService>()
    private val videoService = mockk<VideoService>()
    private val resolver = YoutubeResolver(youtubeListService, videoService)
    private val userId = UUID.randomUUID()

    @BeforeTest
    fun setUp() {
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
    fun `youtubeLists query maps service results`() {
        val list = YoutubeList(userId = userId, youtubeListId = "PL1", url = "url1", name = "List 1")
        val stats = ListStats(totalVideos = 10, downloadedVideos = 5, removedVideos = 1)
        every { youtubeListService.listLists() } returns listOf(list to stats)

        val result = resolver.youtubeLists()
        assertEquals(1, result.size)
        assertEquals(list, result[0]["list"])
        assertEquals(10L, result[0]["totalVideos"])
    }

    @Test
    fun `videos query delegates to service`() {
        val listId = UUID.randomUUID()
        every { videoService.getVideos(listId, "query", false) } returns emptyList()

        val result = resolver.videos(listId, "query", false)
        assertEquals(0, result.size)
    }
}
