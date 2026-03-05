package org.sightech.memoryvault.feed.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FeedServiceTest {

    private val feedRepository = mockk<FeedRepository>()
    private val feedItemRepository = mockk<FeedItemRepository>()
    private val rssFetchService = mockk<RssFetchService>()
    private val service = FeedService(feedRepository, feedItemRepository, rssFetchService)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `addFeed creates feed and triggers initial fetch`() {
        every { feedRepository.save(any()) } answers { firstArg() }
        coEvery { rssFetchService.fetchAndStore(any()) } returns 5

        val result = runBlocking { service.addFeed("https://example.com/rss") }

        assertNotNull(result)
        assertEquals("https://example.com/rss", result.url)
        verify { feedRepository.save(any()) }
        coVerify { rssFetchService.fetchAndStore(any()) }
    }

    @Test
    fun `listFeeds returns feeds with unread counts`() {
        val feed1 = Feed(userId = userId, url = "https://a.com/rss").apply { title = "Feed A" }
        val feed2 = Feed(userId = userId, url = "https://b.com/rss").apply { title = "Feed B" }
        every { feedRepository.findAllActiveByUserId(userId) } returns listOf(feed1, feed2)
        every { feedItemRepository.countUnreadByFeedId(feed1.id) } returns 3
        every { feedItemRepository.countUnreadByFeedId(feed2.id) } returns 0

        val result = service.listFeeds()

        assertEquals(2, result.size)
        assertEquals(3L, result[0].second)
        assertEquals(0L, result[1].second)
    }

    @Test
    fun `deleteFeed soft deletes`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss")
        every { feedRepository.findActiveById(feed.id) } returns feed
        every { feedRepository.save(any()) } answers { firstArg() }

        val result = service.deleteFeed(feed.id)

        assertNotNull(result)
        assertNotNull(result.deletedAt)
    }

    @Test
    fun `deleteFeed returns null for nonexistent feed`() {
        val id = UUID.randomUUID()
        every { feedRepository.findActiveById(id) } returns null

        val result = service.deleteFeed(id)

        assertNull(result)
    }

    @Test
    fun `refreshFeed refreshes single feed`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss")
        every { feedRepository.findActiveById(feed.id) } returns feed
        coEvery { rssFetchService.fetchAndStore(feed) } returns 3

        val result = runBlocking { service.refreshFeed(feed.id) }

        assertEquals(1, result.size)
        assertEquals(3, result[0].second)
    }

    @Test
    fun `refreshFeed with null refreshes all feeds`() {
        val feed1 = Feed(userId = userId, url = "https://a.com/rss")
        val feed2 = Feed(userId = userId, url = "https://b.com/rss")
        every { feedRepository.findAllActiveByUserId(userId) } returns listOf(feed1, feed2)
        coEvery { rssFetchService.fetchAndStore(any()) } returns 2

        val result = runBlocking { service.refreshFeed(null) }

        assertEquals(2, result.size)
    }
}
