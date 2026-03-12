package org.sightech.memoryvault.bookmark.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.sightech.memoryvault.bookmark.entity.*
import org.sightech.memoryvault.bookmark.repository.*
import org.sightech.memoryvault.auth.CurrentUser
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.time.Instant
import java.util.UUID

class IngestServiceTest {

    private val bookmarkRepository = mockk<BookmarkRepository>(relaxed = true)
    private val folderRepository = mockk<FolderRepository>(relaxed = true)
    private val bookmarkService = mockk<BookmarkService>(relaxed = true)
    private val ingestPreviewRepository = mockk<IngestPreviewRepository>(relaxed = true)
    private val objectMapper = mockk<tools.jackson.databind.ObjectMapper>(relaxed = true)
    private lateinit var service: IngestService
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val auth = UsernamePasswordAuthenticationToken(userId.toString(), null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
        service = IngestService(bookmarkRepository, folderRepository, bookmarkService, ingestPreviewRepository, objectMapper)
    }

    @Test
    fun `new bookmark is marked as NEW`() {
        every { bookmarkRepository.findAllActiveByUserId(userId) } returns emptyList()
        every { bookmarkRepository.findByNormalizedUrlAndUserId(any(), userId) } returns null
        every { bookmarkRepository.findByNormalizedUrlIncludingDeleted(any(), userId) } returns null
        every { ingestPreviewRepository.save(any()) } answers { firstArg() }

        val input = listOf(IngestBookmarkInput("https://new.com", "New Site", "Tech"))
        val preview = service.generatePreview(input)

        assertEquals(1, preview.summary.newCount)
        assertEquals(IngestStatus.NEW, preview.items.first().status)
    }

    @Test
    fun `existing bookmark with same URL is UNCHANGED`() {
        val existing = Bookmark(url = "https://example.com", title = "Example", userId = userId, normalizedUrl = "https://example.com")
        every { bookmarkRepository.findAllActiveByUserId(userId) } returns listOf(existing)
        every { bookmarkRepository.findByNormalizedUrlAndUserId("https://example.com", userId) } returns existing
        every { ingestPreviewRepository.save(any()) } answers { firstArg() }

        val input = listOf(IngestBookmarkInput("https://example.com", "Example", null))
        val preview = service.generatePreview(input)

        assertEquals(1, preview.summary.unchangedCount)
    }

    @Test
    fun `existing bookmark with different title is TITLE_CHANGED`() {
        val existing = Bookmark(url = "https://example.com", title = "Old Title", userId = userId, normalizedUrl = "https://example.com")
        every { bookmarkRepository.findByNormalizedUrlAndUserId("https://example.com", userId) } returns existing
        every { ingestPreviewRepository.save(any()) } answers { firstArg() }

        val input = listOf(IngestBookmarkInput("https://example.com", "New Title", null))
        val preview = service.generatePreview(input)

        assertEquals(1, preview.summary.titleChangedCount)
    }

    @Test
    fun `existing bookmark in different folder is MOVED`() {
        val folderId = UUID.randomUUID()
        val existing = Bookmark(url = "https://example.com", title = "Example", userId = userId, normalizedUrl = "https://example.com", folderId = folderId)
        val targetFolder = Folder(id = UUID.randomUUID(), name = "Other", userId = userId)

        every { bookmarkRepository.findByNormalizedUrlAndUserId("https://example.com", userId) } returns existing
        every { folderRepository.findAllActiveByUserId(userId) } returns listOf(targetFolder)
        every { ingestPreviewRepository.save(any()) } answers { firstArg() }

        val input = listOf(IngestBookmarkInput("https://example.com", "Example", "Other"))
        val preview = service.generatePreview(input)

        assertEquals(1, preview.summary.movedCount)
    }

    @Test
    fun `soft-deleted bookmark is PREVIOUSLY_DELETED`() {
        val deleted = Bookmark(url = "https://deleted.com", title = "Deleted", userId = userId, normalizedUrl = "https://deleted.com", deletedAt = Instant.now())
        every { bookmarkRepository.findByNormalizedUrlAndUserId("https://deleted.com", userId) } returns null
        every { bookmarkRepository.findByNormalizedUrlIncludingDeleted("https://deleted.com", userId) } returns deleted
        every { ingestPreviewRepository.save(any()) } answers { firstArg() }

        val input = listOf(IngestBookmarkInput("https://deleted.com", "Deleted", null))
        val preview = service.generatePreview(input)

        assertEquals(1, preview.summary.previouslyDeletedCount)
    }
}
