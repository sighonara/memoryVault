package org.sightech.memoryvault.bookmark.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.sightech.memoryvault.bookmark.entity.*
import org.sightech.memoryvault.bookmark.repository.*
import org.sightech.memoryvault.auth.CurrentUser
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.time.Instant
import java.util.UUID

class IngestServiceTest {

    private val bookmarkRepository = mockk<BookmarkRepository>(relaxed = true)
    private val folderRepository = mockk<FolderRepository>(relaxed = true)
    private val bookmarkService = mockk<BookmarkService>(relaxed = true)
    private val ingestPreviewRepository = mockk<IngestPreviewRepository>(relaxed = true)
    private val objectMapper = tools.jackson.databind.json.JsonMapper.builder().build()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private lateinit var service: IngestService
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val auth = UsernamePasswordAuthenticationToken(userId.toString(), null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
        service = IngestService(bookmarkRepository, folderRepository, bookmarkService, ingestPreviewRepository, objectMapper, eventPublisher)
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

    @Test
    fun `commitResolutions accepts NEW bookmarks`() {
        val previewId = UUID.randomUUID()
        val previewData = """{"items":[{"url":"https://new.com","title":"New","status":"NEW","browserFolder":"Tech"}],"summary":{"newCount":1}}"""
        val previewEntity = IngestPreviewEntity(id = previewId, userId = userId, previewData = previewData)

        every { ingestPreviewRepository.findActiveByIdAndUserId(previewId, userId) } returns previewEntity
        every { bookmarkService.create(any(), any(), any()) } returns mockk(relaxed = true)
        every { ingestPreviewRepository.save(any()) } answers { firstArg() }

        val resolutions = listOf(IngestResolution("https://new.com", IngestAction.ACCEPT))
        val result = service.commitResolutions(previewId, resolutions)

        assertEquals(1, result.accepted)
        verify { bookmarkService.create("https://new.com", "New", emptyList()) }
    }

    @Test
    fun `commitResolutions skips SKIP actions`() {
        val previewId = UUID.randomUUID()
        val previewData = """{"items":[{"url":"https://skip.com","title":"Skip","status":"NEW"}],"summary":{"newCount":1}}"""
        val previewEntity = IngestPreviewEntity(id = previewId, userId = userId, previewData = previewData)

        every { ingestPreviewRepository.findActiveByIdAndUserId(previewId, userId) } returns previewEntity
        every { ingestPreviewRepository.save(any()) } answers { firstArg() }

        val resolutions = listOf(IngestResolution("https://skip.com", IngestAction.SKIP))
        val result = service.commitResolutions(previewId, resolutions)

        assertEquals(1, result.skipped)
        verify(exactly = 0) { bookmarkService.create(any(), any(), any()) }
    }

    @Test
    fun `commitResolutions undeletes PREVIOUSLY_DELETED bookmarks`() {
        val previewId = UUID.randomUUID()
        val bookmarkId = UUID.randomUUID()
        val previewData = """{"items":[{"url":"https://deleted.com","title":"Deleted","status":"PREVIOUSLY_DELETED","existingBookmarkId":"$bookmarkId"}],"summary":{"previouslyDeletedCount":1}}"""
        val previewEntity = IngestPreviewEntity(id = previewId, userId = userId, previewData = previewData)
        val deletedBookmark = Bookmark(id = bookmarkId, url = "https://deleted.com", title = "Deleted", userId = userId, deletedAt = Instant.now())

        every { ingestPreviewRepository.findActiveByIdAndUserId(previewId, userId) } returns previewEntity
        every { bookmarkRepository.findById(bookmarkId) } returns java.util.Optional.of(deletedBookmark)
        every { bookmarkRepository.save(any()) } answers { firstArg() }
        every { ingestPreviewRepository.save(any()) } answers { firstArg() }

        val resolutions = listOf(IngestResolution("https://deleted.com", IngestAction.UNDELETE))
        val result = service.commitResolutions(previewId, resolutions)

        assertEquals(1, result.undeleted)
        verify { bookmarkRepository.save(match { it.id == bookmarkId && it.deletedAt == null }) }
    }

    @Test
    fun `commitResolutions marks preview as committed`() {
        val previewId = UUID.randomUUID()
        val previewData = """{"items":[],"summary":{}}"""
        val previewEntity = IngestPreviewEntity(id = previewId, userId = userId, previewData = previewData)

        every { ingestPreviewRepository.findActiveByIdAndUserId(previewId, userId) } returns previewEntity
        every { ingestPreviewRepository.save(any()) } answers { firstArg() }

        service.commitResolutions(previewId, emptyList())

        verify { ingestPreviewRepository.save(match { it.committed }) }
    }
}
