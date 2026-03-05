package org.sightech.memoryvault.mcp

import kotlinx.coroutines.runBlocking
import org.sightech.memoryvault.feed.service.FeedItemService
import org.sightech.memoryvault.feed.service.FeedService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class FeedTools(
    private val feedService: FeedService,
    private val feedItemService: FeedItemService
) {

    @Tool(description = "Subscribe to an RSS feed. Use when the user wants to add, follow, or subscribe to an RSS or Atom feed by URL.")
    fun addFeed(url: String): String {
        val feed = runBlocking { feedService.addFeed(url) }
        return "Subscribed to feed: \"${feed.title ?: feed.url}\" (${feed.url}) — id: ${feed.id}"
    }

    @Tool(description = "List all subscribed RSS feeds with unread item counts. Use when the user wants to see their feeds or check for unread items.")
    fun listFeeds(): String {
        val feeds = feedService.listFeeds()
        if (feeds.isEmpty()) return "No feeds subscribed."

        val lines = feeds.map { (feed, unread) ->
            "- ${feed.title ?: feed.url} — $unread unread — id: ${feed.id}"
        }
        return "${feeds.size} feed(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Browse items from an RSS feed. Use when the user wants to read or see articles from a specific feed. Set unreadOnly to true to see only unread items.")
    fun getFeedItems(feedId: String, limit: Int?, unreadOnly: Boolean?): String {
        val items = feedItemService.getItems(UUID.fromString(feedId), limit, unreadOnly ?: false)
        if (items.isEmpty()) return "No items found."

        val lines = items.map { item ->
            val status = if (item.readAt != null) "[read]" else "[unread]"
            val tagStr = if (item.tags.isNotEmpty()) " [${item.tags.joinToString(", ") { it.name }}]" else ""
            "- $status ${item.title ?: "(no title)"} — ${item.url ?: "(no url)"}$tagStr — id: ${item.id}"
        }
        return "${items.size} item(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Mark a single feed item as read. Use when the user has read an article or wants to dismiss it.")
    fun markItemRead(itemId: String): String {
        val item = feedItemService.markItemRead(UUID.fromString(itemId))
            ?: return "Feed item not found."
        return "Marked as read: \"${item.title ?: "(no title)"}\""
    }

    @Tool(description = "Mark a single feed item as unread. Use when the user wants to mark an article as unread again.")
    fun markItemUnread(itemId: String): String {
        val item = feedItemService.markItemUnread(UUID.fromString(itemId))
            ?: return "Feed item not found."
        return "Marked as unread: \"${item.title ?: "(no title)"}\""
    }

    @Tool(description = "Mark all items in a feed as read. Use when the user wants to clear all unread items in a feed.")
    fun markFeedRead(feedId: String): String {
        val count = feedItemService.markFeedRead(UUID.fromString(feedId))
        return "Marked $count item(s) as read."
    }

    @Tool(description = "Refresh one or all RSS feeds to fetch new items. Use when the user wants to check for new articles. Pass a feedId to refresh one feed, or omit it to refresh all.")
    fun refreshFeed(feedId: String?): String {
        val results = runBlocking {
            feedService.refreshFeed(feedId?.let { UUID.fromString(it) })
        }
        if (results.isEmpty()) return "No feeds to refresh."

        val lines = results.map { (feed, newCount) ->
            "- ${feed.title ?: feed.url}: $newCount new item(s)"
        }
        return "Refreshed ${results.size} feed(s):\n${lines.joinToString("\n")}"
    }
}
