package org.sightech.memoryvault.search

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchServiceTest {

    private val searchRepository = mockk<SearchRepository>()
    private val service = SearchService(searchRepository)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `search returns results from all types`() {
        every { searchRepository.searchBookmarks(any(), any(), any()) } returns listOf(
            SearchResult(ContentType.BOOKMARK, UUID.randomUUID(), "Kotlin Docs", "https://kotlinlang.org", 0.8f)
        )
        every { searchRepository.searchFeedItems(any(), any(), any()) } returns listOf(
            SearchResult(ContentType.FEED_ITEM, UUID.randomUUID(), "Kotlin 2.0 Released", "https://blog.example.com", 0.6f)
        )
        every { searchRepository.searchVideos(any(), any(), any()) } returns listOf(
            SearchResult(ContentType.VIDEO, UUID.randomUUID(), "Kotlin Tutorial", "https://youtube.com/watch?v=abc", 0.5f)
        )

        val results = service.search("kotlin", null, userId, 20)
        assertEquals(3, results.size)
        assertTrue(results[0].rank >= results[1].rank)
        assertTrue(results[1].rank >= results[2].rank)
    }

    @Test
    fun `search filters by content type`() {
        every { searchRepository.searchBookmarks(any(), any(), any()) } returns listOf(
            SearchResult(ContentType.BOOKMARK, UUID.randomUUID(), "Result", "url", 0.9f)
        )

        val results = service.search("test", listOf(ContentType.BOOKMARK), userId, 20)
        assertEquals(1, results.size)
        assertEquals(ContentType.BOOKMARK, results[0].type)
    }

    @Test
    fun `search with no types queries all`() {
        every { searchRepository.searchBookmarks(any(), any(), any()) } returns emptyList()
        every { searchRepository.searchFeedItems(any(), any(), any()) } returns emptyList()
        every { searchRepository.searchVideos(any(), any(), any()) } returns emptyList()

        val results = service.search("nothing", null, userId, 20)
        assertEquals(0, results.size)
    }
}
