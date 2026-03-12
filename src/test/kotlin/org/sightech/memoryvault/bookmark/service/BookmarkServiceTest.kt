package org.sightech.memoryvault.bookmark.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.sightech.memoryvault.bookmark.entity.Bookmark
import org.sightech.memoryvault.bookmark.entity.Folder
import org.sightech.memoryvault.bookmark.repository.BookmarkRepository
import org.sightech.memoryvault.bookmark.repository.FolderRepository
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
    private val folderRepository = mockk<FolderRepository>(relaxed = true)
    private val tagService = mockk<TagService>()
    private val service = BookmarkService(bookmarkRepository, folderRepository, tagService)
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
        every { folderRepository.findAllActiveByUserId(userId) } returns emptyList()
        every { bookmarkRepository.findAllActiveByUserId(userId) } returns listOf(bookmark)

        val result = service.exportNetscapeHtml()

        assert(result.contains("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
        assert(result.contains("HREF=\"https://example.com\""))
        assert(result.contains("Example"))
    }

    @Test
    fun `moveBookmark updates folderId`() {
        val bookmarkId = UUID.randomUUID()
        val folderId = UUID.randomUUID()
        val bookmark = Bookmark(id = bookmarkId, url = "https://example.com", title = "Ex", userId = userId)
        val folder = Folder(id = folderId, name = "Tech", userId = userId)

        every { bookmarkRepository.findActiveByIdAndUserId(bookmarkId, userId) } returns bookmark
        every { folderRepository.findActiveByIdAndUserId(folderId, userId) } returns folder
        every { bookmarkRepository.save(any()) } answers { firstArg() }

        val result = service.moveBookmark(bookmarkId, folderId)

        assertEquals(folderId, result.folderId)
    }

    @Test
    fun `moveBookmark to null unfiles the bookmark`() {
        val bookmarkId = UUID.randomUUID()
        val bookmark = Bookmark(id = bookmarkId, url = "https://example.com", title = "Ex", userId = userId, folderId = UUID.randomUUID())

        every { bookmarkRepository.findActiveByIdAndUserId(bookmarkId, userId) } returns bookmark
        every { bookmarkRepository.save(any()) } answers { firstArg() }

        val result = service.moveBookmark(bookmarkId, null)

        assertNull(result.folderId)
    }

    @Test
    fun `reorderBookmarks updates sortOrder for all bookmarks in folder`() {
        val folderId = UUID.randomUUID()
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val id3 = UUID.randomUUID()
        val b1 = Bookmark(id = id1, url = "https://a.com", title = "A", userId = userId, folderId = folderId, sortOrder = 0)
        val b2 = Bookmark(id = id2, url = "https://b.com", title = "B", userId = userId, folderId = folderId, sortOrder = 1)
        val b3 = Bookmark(id = id3, url = "https://c.com", title = "C", userId = userId, folderId = folderId, sortOrder = 2)

        every { bookmarkRepository.findByFolderIdAndUserId(folderId, userId) } returns listOf(b1, b2, b3)
        every { bookmarkRepository.save(any()) } answers { firstArg() }

        // Reorder: C, A, B
        val result = service.reorderBookmarks(folderId, listOf(id3, id1, id2))

        verify { bookmarkRepository.save(match { it.id == id3 && it.sortOrder == 0 }) }
        verify { bookmarkRepository.save(match { it.id == id1 && it.sortOrder == 1 }) }
        verify { bookmarkRepository.save(match { it.id == id2 && it.sortOrder == 2 }) }
    }

    @Test
    fun `exportNetscapeHtml includes folder hierarchy`() {
        val folderId = UUID.randomUUID()
        val folder = Folder(id = folderId, name = "Tech", userId = userId)
        val bookmark = Bookmark(id = UUID.randomUUID(), url = "https://example.com", title = "Example", userId = userId, folderId = folderId)

        every { folderRepository.findAllActiveByUserId(userId) } returns listOf(folder)
        every { bookmarkRepository.findAllActiveByUserId(userId) } returns listOf(bookmark)

        val html = service.exportNetscapeHtml()

        assertTrue(html.contains("<H3>Tech</H3>"))
        assertTrue(html.contains("<A HREF=\"https://example.com\">Example</A>"))
    }
}
