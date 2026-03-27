package org.sightech.memoryvault.mcp

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.service.FeedItemService
import org.sightech.memoryvault.feed.service.FeedService
import org.sightech.memoryvault.feed.service.OpmlService
import java.util.UUID
import kotlin.test.assertContains

class FeedToolsTest {

    private val feedService = mockk<FeedService>()
    private val feedItemService = mockk<FeedItemService>()
    private val opmlService = mockk<OpmlService>()
    private val tools = FeedTools(feedService, feedItemService, opmlService)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `addFeed returns confirmation`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss").apply { title = "Example Feed" }
        coEvery { feedService.addFeed(eq("https://example.com/rss"), isNull()) } returns feed

        val result = runBlocking { tools.addFeed("https://example.com/rss", null) }

        assertContains(result, "Example Feed")
        assertContains(result, "https://example.com/rss")
    }

    @Test
    fun `listFeeds returns formatted list with unread counts`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss").apply { title = "Example" }
        every { feedService.listFeeds() } returns listOf(feed to 5L)

        val result = tools.listFeeds()

        assertContains(result, "Example")
        assertContains(result, "5 unread")
    }

    @Test
    fun `listFeeds returns message when empty`() {
        every { feedService.listFeeds() } returns emptyList()

        val result = tools.listFeeds()

        assertContains(result, "No feeds")
    }

    @Test
    fun `getFeedItems returns formatted items`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss")
        val item = FeedItem(feed = feed, guid = "1", title = "Post Title", url = "https://example.com/post")
        every { feedItemService.getItems(any(), any(), any(), any()) } returns listOf(item)

        val result = tools.getFeedItems(feed.id.toString(), null, null, null)

        assertContains(result, "Post Title")
    }

    @Test
    fun `markItemRead returns confirmation`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss")
        val item = FeedItem(feed = feed, guid = "1", title = "Post Title")
        every { feedItemService.markItemRead(item.id) } returns item

        val result = tools.markItemRead(item.id.toString())

        assertContains(result, "Post Title")
        assertContains(result, "read")
    }

    @Test
    fun `markItemRead returns not found`() {
        val id = UUID.randomUUID()
        every { feedItemService.markItemRead(id) } returns null

        val result = tools.markItemRead(id.toString())

        assertContains(result, "not found")
    }

    @Test
    fun `markItemUnread returns confirmation`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss")
        val item = FeedItem(feed = feed, guid = "1", title = "Post Title")
        every { feedItemService.markItemUnread(item.id) } returns item

        val result = tools.markItemUnread(item.id.toString())

        assertContains(result, "Post Title")
        assertContains(result, "unread")
    }

    @Test
    fun `markFeedRead returns count`() {
        every { feedItemService.markFeedRead(any()) } returns 10

        val result = tools.markFeedRead(UUID.randomUUID().toString())

        assertContains(result, "10")
        assertContains(result, "read")
    }

    @Test
    fun `refreshFeed returns summary`() {
        val feed = Feed(userId = userId, url = "https://a.com/rss").apply { title = "Feed A" }
        coEvery { feedService.refreshFeed(eq(feed.id)) } returns listOf(feed to 3)

        val result = runBlocking { tools.refreshFeed(feed.id.toString()) }

        assertContains(result, "Feed A")
        assertContains(result, "3")
    }

    @Test
    fun `refreshFeed with null refreshes all`() {
        val feed = Feed(userId = userId, url = "https://a.com/rss").apply { title = "A" }
        coEvery { feedService.refreshFeed(isNull()) } returns listOf(feed to 2)

        val result = runBlocking { tools.refreshFeed(null) }

        assertContains(result, "A")
    }
}
