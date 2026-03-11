package org.sightech.memoryvault.feed.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FeedItemServiceTest {

    private val feedItemRepository = mockk<FeedItemRepository>()
    private val service = FeedItemService(feedItemRepository)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val feed = Feed(userId = userId, url = "https://example.com/rss")

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
    fun `getItems returns all items for feed`() {
        val items = listOf(FeedItem(feed = feed, guid = "1", title = "A"))
        every { feedItemRepository.findByFeedIdAndUserId(feed.id, userId) } returns items

        val result = service.getItems(feed.id, null, false)

        assertEquals(1, result.size)
    }

    @Test
    fun `getItems returns only unread when unreadOnly is true`() {
        val items = listOf(FeedItem(feed = feed, guid = "1", title = "A"))
        every { feedItemRepository.findUnreadByFeedIdAndUserId(feed.id, userId) } returns items

        val result = service.getItems(feed.id, null, true)

        assertEquals(1, result.size)
        verify { feedItemRepository.findUnreadByFeedIdAndUserId(feed.id, userId) }
    }

    @Test
    fun `getItems respects limit`() {
        val items = (1..10).map { FeedItem(feed = feed, guid = "$it", title = "Item $it") }
        every { feedItemRepository.findByFeedIdAndUserId(feed.id, userId) } returns items

        val result = service.getItems(feed.id, 3, false)

        assertEquals(3, result.size)
    }

    @Test
    fun `markItemRead sets readAt`() {
        val item = FeedItem(feed = feed, guid = "1", title = "A")
        every { feedItemRepository.findByIdAndUserId(item.id, userId) } returns item
        every { feedItemRepository.save(any()) } answers { firstArg() }

        val result = service.markItemRead(item.id)

        assertNotNull(result)
        assertNotNull(result.readAt)
    }

    @Test
    fun `markItemRead returns null for nonexistent item`() {
        val id = UUID.randomUUID()
        every { feedItemRepository.findByIdAndUserId(id, userId) } returns null

        val result = service.markItemRead(id)

        assertNull(result)
    }

    @Test
    fun `markItemUnread clears readAt`() {
        val item = FeedItem(feed = feed, guid = "1", title = "A").apply { readAt = Instant.now() }
        every { feedItemRepository.findByIdAndUserId(item.id, userId) } returns item
        every { feedItemRepository.save(any()) } answers { firstArg() }

        val result = service.markItemUnread(item.id)

        assertNotNull(result)
        assertNull(result.readAt)
    }

    @Test
    fun `markFeedRead returns count of marked items`() {
        every { feedItemRepository.markAllReadByFeedIdAndUserId(feed.id, userId, any()) } returns 5

        val count = service.markFeedRead(feed.id)

        assertEquals(5, count)
    }
}
