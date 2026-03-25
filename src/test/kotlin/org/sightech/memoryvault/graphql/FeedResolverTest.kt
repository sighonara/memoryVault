package org.sightech.memoryvault.graphql

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.service.FeedItemService
import org.sightech.memoryvault.feed.service.FeedService
import java.util.UUID
import kotlin.test.assertEquals

class FeedResolverTest {

    private val feedService = mockk<FeedService>()
    private val feedItemService = mockk<FeedItemService>()
    private val resolver = FeedResolver(feedService, feedItemService)

    @Test
    fun `feeds query delegates to service`() {
        val feed = Feed(userId = UUID.randomUUID(), url = "https://example.com/rss")
        every { feedService.listFeeds() } returns listOf(feed to 5L)

        val result = resolver.feeds()
        assertEquals(1, result.size)
        assertEquals(feed, result[0]["feed"])
        assertEquals(5L, result[0]["unreadCount"])
    }

    @Test
    fun `feedItems query delegates to service`() {
        val feedId = UUID.randomUUID()
        every { feedItemService.getItems(feedId, 10, true, "NEWEST_FIRST") } returns emptyList()

        val result = resolver.feedItems(feedId, 10, true, null)
        assertEquals(0, result.size)
        verify { feedItemService.getItems(feedId, 10, true, "NEWEST_FIRST") }
    }

    @Test
    fun `addFeed mutation delegates to service`() {
        val feed = Feed(userId = UUID.randomUUID(), url = "https://example.com/rss")
        coEvery { feedService.addFeed("https://example.com/rss", null) } returns feed

        val result = resolver.addFeed("https://example.com/rss", null)
        assertEquals("https://example.com/rss", result.url)
    }
}
