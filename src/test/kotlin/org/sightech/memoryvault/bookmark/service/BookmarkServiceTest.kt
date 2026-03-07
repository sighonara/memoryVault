package org.sightech.memoryvault.bookmark.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.bookmark.entity.Bookmark
import org.sightech.memoryvault.bookmark.repository.BookmarkRepository
import org.sightech.memoryvault.tag.entity.Tag
import org.sightech.memoryvault.tag.service.TagService
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BookmarkServiceTest {

    private val bookmarkRepository = mockk<BookmarkRepository>()
    private val tagService = mockk<TagService>()
    private val service = BookmarkService(bookmarkRepository, tagService)
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
    fun `create saves bookmark without tags`() {
        every { bookmarkRepository.save(any()) } answers { firstArg() }

        val result = service.create("https://example.com", "Example", null)

        assertNotNull(result)
        assertEquals("https://example.com", result.url)
        assertEquals("Example", result.title)
        verify { bookmarkRepository.save(any()) }
    }

    @Test
    fun `create saves bookmark with tags`() {
        val tags = listOf(Tag(userId = userId, name = "kotlin"), Tag(userId = userId, name = "spring"))
        every { tagService.findOrCreateByNames(listOf("kotlin", "spring")) } returns tags
        every { bookmarkRepository.save(any()) } answers { firstArg() }

        val result = service.create("https://example.com", "Example", listOf("kotlin", "spring"))

        assertEquals(2, result.tags.size)
        verify { tagService.findOrCreateByNames(listOf("kotlin", "spring")) }
    }

    @Test
    fun `create uses URL as title when title is null`() {
        every { bookmarkRepository.save(any()) } answers { firstArg() }

        val result = service.create("https://example.com", null, null)

        assertEquals("https://example.com", result.title)
    }

    @Test
    fun `findAll returns active bookmarks`() {
        val bookmarks = listOf(Bookmark(userId = userId, url = "https://a.com", title = "A"))
        every { bookmarkRepository.findAllActiveByUserId(userId) } returns bookmarks

        val result = service.findAll(null, null)

        assertEquals(1, result.size)
    }

    @Test
    fun `findAll filters by query`() {
        val bookmarks = listOf(
            Bookmark(userId = userId, url = "https://kotlin.dev", title = "Kotlin"),
            Bookmark(userId = userId, url = "https://spring.io", title = "Spring")
        )
        every { bookmarkRepository.findAllActiveByUserId(userId) } returns bookmarks

        val result = service.findAll("kotlin", null)

        assertEquals(1, result.size)
        assertEquals("Kotlin", result[0].title)
    }

    @Test
    fun `findAll filters by tags`() {
        val kotlinTag = Tag(userId = userId, name = "kotlin")
        val b1 = Bookmark(userId = userId, url = "https://a.com", title = "A").apply { tags.add(kotlinTag) }
        val b2 = Bookmark(userId = userId, url = "https://b.com", title = "B")
        every { bookmarkRepository.findAllActiveByUserId(userId) } returns listOf(b1, b2)

        val result = service.findAll(null, listOf("kotlin"))

        assertEquals(1, result.size)
        assertEquals("A", result[0].title)
    }

    @Test
    fun `updateTags replaces tags on bookmark`() {
        val bookmark = Bookmark(userId = userId, url = "https://a.com", title = "A")
        val newTags = listOf(Tag(userId = userId, name = "new-tag"))
        every { bookmarkRepository.findActiveByIdAndUserId(bookmark.id, userId) } returns bookmark
        every { tagService.findOrCreateByNames(listOf("new-tag")) } returns newTags
        every { bookmarkRepository.save(any()) } answers { firstArg() }

        val result = service.updateTags(bookmark.id, listOf("new-tag"))

        assertNotNull(result)
        assertEquals(1, result.tags.size)
    }

    @Test
    fun `updateTags returns null for nonexistent bookmark`() {
        val id = UUID.randomUUID()
        every { bookmarkRepository.findActiveByIdAndUserId(id, userId) } returns null

        val result = service.updateTags(id, listOf("tag"))

        assertNull(result)
    }

    @Test
    fun `softDelete sets deletedAt`() {
        val bookmark = Bookmark(userId = userId, url = "https://a.com", title = "A")
        every { bookmarkRepository.findActiveByIdAndUserId(bookmark.id, userId) } returns bookmark
        every { bookmarkRepository.save(any()) } answers { firstArg() }

        val result = service.softDelete(bookmark.id)

        assertNotNull(result)
        assertNotNull(result.deletedAt)
    }

    @Test
    fun `softDelete returns null for nonexistent bookmark`() {
        val id = UUID.randomUUID()
        every { bookmarkRepository.findActiveByIdAndUserId(id, userId) } returns null

        val result = service.softDelete(id)

        assertNull(result)
    }

    @Test
    fun `exportNetscapeHtml produces valid Netscape format`() {
        val bookmark = Bookmark(userId = userId, url = "https://example.com", title = "Example")
        every { bookmarkRepository.findAllActiveByUserId(userId) } returns listOf(bookmark)

        val result = service.exportNetscapeHtml()

        assert(result.contains("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
        assert(result.contains("HREF=\"https://example.com\""))
        assert(result.contains("Example"))
    }
}
