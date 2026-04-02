package org.sightech.memoryvault.feed.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.springframework.context.ApplicationEventPublisher
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
    private val feedRepository = mockk<FeedRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = FeedItemService(feedItemRepository, feedRepository, eventPublisher)
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

    @Test
    fun `getItems with OLDEST_FIRST uses ascending query`() {
        val items = listOf(FeedItem(feed = feed, guid = "1", title = "A"))
        every { feedItemRepository.findByFeedIdAndUserIdAsc(feed.id, userId) } returns items

        val result = service.getItems(feed.id, null, false, "OLDEST_FIRST")

        assertEquals(1, result.size)
        verify { feedItemRepository.findByFeedIdAndUserIdAsc(feed.id, userId) }
    }

    @Test
    fun `getItems with OLDEST_FIRST and unreadOnly uses ascending unread query`() {
        val items = listOf(FeedItem(feed = feed, guid = "1", title = "A"))
        every { feedItemRepository.findUnreadByFeedIdAndUserIdAsc(feed.id, userId) } returns items

        val result = service.getItems(feed.id, null, true, "OLDEST_FIRST")

        assertEquals(1, result.size)
        verify { feedItemRepository.findUnreadByFeedIdAndUserIdAsc(feed.id, userId) }
    }

    @Test
    fun `getItemsByFeedIds returns items across multiple feeds`() {
        val feed2 = Feed(userId = userId, url = "https://b.com/rss")
        val feedIds = listOf(feed.id, feed2.id)
        val items = listOf(
            FeedItem(feed = feed, guid = "1", title = "A"),
            FeedItem(feed = feed2, guid = "2", title = "B")
        )
        every { feedItemRepository.findByFeedIdsAndUserId(feedIds, userId) } returns items

        val result = service.getItemsByFeedIds(feedIds, null, false)

        assertEquals(2, result.size)
    }

    @Test
    fun `getItemsByFeedIds returns empty for empty feedIds`() {
        val result = service.getItemsByFeedIds(emptyList(), null, false)

        assertEquals(0, result.size)
    }

    @Test
    fun `getItemsByFeedIds with OLDEST_FIRST and unreadOnly`() {
        val feedIds = listOf(feed.id)
        val items = listOf(FeedItem(feed = feed, guid = "1", title = "A"))
        every { feedItemRepository.findUnreadByFeedIdsAndUserIdAsc(feedIds, userId) } returns items

        val result = service.getItemsByFeedIds(feedIds, null, true, "OLDEST_FIRST")

        assertEquals(1, result.size)
        verify { feedItemRepository.findUnreadByFeedIdsAndUserIdAsc(feedIds, userId) }
    }

    @Test
    fun `markCategoryRead marks all feeds in category as read`() {
        val categoryId = UUID.randomUUID()
        val feed2 = Feed(userId = userId, url = "https://b.com/rss")
        every { feedRepository.findAllActiveByCategoryId(userId, categoryId) } returns listOf(feed, feed2)
        every { feedItemRepository.markAllReadByFeedIdAndUserId(feed.id, userId, any()) } returns 3
        every { feedItemRepository.markAllReadByFeedIdAndUserId(feed2.id, userId, any()) } returns 2

        val count = service.markCategoryRead(categoryId)

        assertEquals(5, count)
    }
}
