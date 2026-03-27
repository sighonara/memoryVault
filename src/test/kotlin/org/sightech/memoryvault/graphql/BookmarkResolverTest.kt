package org.sightech.memoryvault.graphql

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.bookmark.entity.Bookmark
import org.sightech.memoryvault.bookmark.service.BookmarkService
import org.sightech.memoryvault.bookmark.service.IngestService
import java.util.UUID
import kotlin.test.assertEquals

class BookmarkResolverTest {

    private val bookmarkService = mockk<BookmarkService>()
    private val ingestService = mockk<IngestService>()
    private val resolver = BookmarkResolver(bookmarkService, ingestService)

    @Test
    fun `bookmarks query delegates to service`() {
        every { bookmarkService.findAll(any(), any()) } returns emptyList()

        val result = resolver.bookmarks(null, null)
        assertEquals(0, result.size)
        verify { bookmarkService.findAll(null, null) }
    }

    @Test
    fun `addBookmark mutation delegates to service`() {
        val bookmark = Bookmark(userId = UUID.randomUUID(), url = "https://example.com", title = "Example")
        every { bookmarkService.create(any(), any(), any(), any()) } returns bookmark

        val result = resolver.addBookmark("https://example.com", "Example", null, null)
        assertEquals("https://example.com", result.url)
        assertEquals("Example", result.title)
    }
}
