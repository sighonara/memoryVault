package org.sightech.memoryvault.mcp

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.bookmark.entity.Bookmark
import org.sightech.memoryvault.bookmark.service.BookmarkService
import org.sightech.memoryvault.tag.entity.Tag
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.assertContains
import kotlin.test.assertEquals

class BookmarkToolsTest {

    private val bookmarkService = mockk<BookmarkService>()
    private val tools = BookmarkTools(bookmarkService)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

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
    fun `addBookmark returns confirmation with title`() {
        val bookmark = Bookmark(userId = userId, url = "https://example.com", title = "Example")
        every { bookmarkService.create(eq("https://example.com"), eq("Example"), isNull()) } returns bookmark

        val result = tools.addBookmark("https://example.com", "Example", null)

        assertContains(result, "Example")
        assertContains(result, "https://example.com")
    }

    @Test
    fun `addBookmark with tags includes tag names`() {
        val tag = Tag(userId = userId, name = "dev")
        val bookmark = Bookmark(userId = userId, url = "https://example.com", title = "Example").apply { tags.add(tag) }
        every { bookmarkService.create(eq("https://example.com"), eq("Example"), eq(listOf("dev"))) } returns bookmark

        val result = tools.addBookmark("https://example.com", "Example", listOf("dev"))

        assertContains(result, "dev")
    }

    @Test
    fun `listBookmarks returns formatted list`() {
        val b1 = Bookmark(userId = userId, url = "https://a.com", title = "A")
        val b2 = Bookmark(userId = userId, url = "https://b.com", title = "B")
        every { bookmarkService.findAll(isNull(), isNull()) } returns listOf(b1, b2)

        val result = tools.listBookmarks(null, null)

        assertContains(result, "A")
        assertContains(result, "B")
        assertContains(result, "2 bookmark")
    }

    @Test
    fun `listBookmarks returns message when empty`() {
        every { bookmarkService.findAll(isNull(), isNull()) } returns emptyList()

        val result = tools.listBookmarks(null, null)

        assertContains(result, "No bookmarks found")
    }

    @Test
    fun `tagBookmark returns updated bookmark info`() {
        val tag = Tag(userId = userId, name = "kotlin")
        val bookmark = Bookmark(userId = userId, url = "https://a.com", title = "A").apply { tags.add(tag) }
        every { bookmarkService.updateTags(eq(bookmark.id), eq(listOf("kotlin"))) } returns bookmark

        val result = tools.tagBookmark(bookmark.id.toString(), listOf("kotlin"))

        assertContains(result, "kotlin")
        assertContains(result, "A")
    }

    @Test
    fun `tagBookmark returns not found message`() {
        val id = UUID.randomUUID()
        every { bookmarkService.updateTags(eq(id), eq(listOf("tag"))) } returns null

        val result = tools.tagBookmark(id.toString(), listOf("tag"))

        assertContains(result, "not found")
    }

    @Test
    fun `deleteBookmark returns confirmation`() {
        val bookmark = Bookmark(userId = userId, url = "https://a.com", title = "A")
        every { bookmarkService.softDelete(bookmark.id) } returns bookmark

        val result = tools.deleteBookmark(bookmark.id.toString())

        assertContains(result, "Deleted")
        assertContains(result, "A")
    }

    @Test
    fun `deleteBookmark returns not found message`() {
        val id = UUID.randomUUID()
        every { bookmarkService.softDelete(id) } returns null

        val result = tools.deleteBookmark(id.toString())

        assertContains(result, "not found")
    }

    @Test
    fun `exportBookmarks returns HTML content`() {
        every { bookmarkService.exportNetscapeHtml() } returns "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<H1>Bookmarks</H1>"

        val result = tools.exportBookmarks(null)

        assertContains(result, "NETSCAPE")
    }
}
