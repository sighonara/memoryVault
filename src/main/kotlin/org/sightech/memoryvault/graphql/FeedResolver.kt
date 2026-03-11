package org.sightech.memoryvault.graphql

import kotlinx.coroutines.runBlocking
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.service.FeedItemService
import org.sightech.memoryvault.feed.service.FeedService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class FeedResolver(
    private val feedService: FeedService,
    private val feedItemService: FeedItemService
) {

    @QueryMapping
    fun feeds(): List<Map<String, Any?>> {
        return feedService.listFeeds().map { (feed, unreadCount) ->
            mapOf("feed" to feed, "unreadCount" to unreadCount)
        }
    }

    @QueryMapping
    fun feedItems(
        @Argument feedId: UUID,
        @Argument limit: Int?,
        @Argument unreadOnly: Boolean?
    ): List<FeedItem> {
        return feedItemService.getItems(feedId, limit, unreadOnly ?: false)
    }

    @MutationMapping
    fun addFeed(@Argument url: String): Feed {
        return runBlocking { feedService.addFeed(url) }
    }

    @MutationMapping
    fun deleteFeed(@Argument feedId: UUID): Feed? {
        return feedService.deleteFeed(feedId)
    }

    @MutationMapping
    fun markItemRead(@Argument itemId: UUID): FeedItem? {
        return feedItemService.markItemRead(itemId)
    }

    @MutationMapping
    fun markItemUnread(@Argument itemId: UUID): FeedItem? {
        return feedItemService.markItemUnread(itemId)
    }

    @MutationMapping
    fun markFeedRead(@Argument feedId: UUID): Int {
        return feedItemService.markFeedRead(feedId)
    }

    @MutationMapping
    fun refreshFeed(@Argument feedId: UUID?): List<Map<String, Any?>> {
        val results = runBlocking { feedService.refreshFeed(feedId) }
        return results.map { (feed, newItems) ->
            mapOf("feedId" to feed.id, "feedTitle" to feed.title, "newItems" to newItems)
        }
    }
}
