package org.sightech.memoryvault.feed.service

import io.mockk.*
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FeedItemServiceTest {

    private val feedItemRepository = mockk<FeedItemRepository>()
    private val service = FeedItemService(feedItemRepository)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val feed = Feed(userId = userId, url = "https://example.com/rss")

    @Test
    fun `getItems returns all items for feed`() {
        val items = listOf(FeedItem(feed = feed, guid = "1", title = "A"))
        every { feedItemRepository.findByFeedId(feed.id) } returns items

        val result = service.getItems(feed.id, null, false)

        assertEquals(1, result.size)
    }

    @Test
    fun `getItems returns only unread when unreadOnly is true`() {
        val items = listOf(FeedItem(feed = feed, guid = "1", title = "A"))
        every { feedItemRepository.findUnreadByFeedId(feed.id) } returns items

        val result = service.getItems(feed.id, null, true)

        assertEquals(1, result.size)
        verify { feedItemRepository.findUnreadByFeedId(feed.id) }
    }

    @Test
    fun `getItems respects limit`() {
        val items = (1..10).map { FeedItem(feed = feed, guid = "$it", title = "Item $it") }
        every { feedItemRepository.findByFeedId(feed.id) } returns items

        val result = service.getItems(feed.id, 3, false)

        assertEquals(3, result.size)
    }

    @Test
    fun `markItemRead sets readAt`() {
        val item = FeedItem(feed = feed, guid = "1", title = "A")
        every { feedItemRepository.findById(item.id) } returns Optional.of(item)
        every { feedItemRepository.save(any()) } answers { firstArg() }

        val result = service.markItemRead(item.id)

        assertNotNull(result)
        assertNotNull(result.readAt)
    }

    @Test
    fun `markItemRead returns null for nonexistent item`() {
        val id = UUID.randomUUID()
        every { feedItemRepository.findById(id) } returns Optional.empty()

        val result = service.markItemRead(id)

        assertNull(result)
    }

    @Test
    fun `markItemUnread clears readAt`() {
        val item = FeedItem(feed = feed, guid = "1", title = "A").apply { readAt = Instant.now() }
        every { feedItemRepository.findById(item.id) } returns Optional.of(item)
        every { feedItemRepository.save(any()) } answers { firstArg() }

        val result = service.markItemUnread(item.id)

        assertNotNull(result)
        assertNull(result.readAt)
    }

    @Test
    fun `markFeedRead returns count of marked items`() {
        every { feedItemRepository.markAllReadByFeedId(feed.id, any()) } returns 5

        val count = service.markFeedRead(feed.id)

        assertEquals(5, count)
    }
}
