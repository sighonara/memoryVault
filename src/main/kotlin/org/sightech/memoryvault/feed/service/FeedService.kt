package org.sightech.memoryvault.feed.service

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class FeedService(
    private val feedRepository: FeedRepository,
    private val feedItemRepository: FeedItemRepository,
    private val rssFetchService: RssFetchService
) {

    suspend fun addFeed(url: String): Feed {
        val userId = CurrentUser.userId()
        val feed = feedRepository.save(Feed(userId = userId, url = url))
        rssFetchService.fetchAndStore(feed)
        return feed
    }

    fun listFeeds(): List<Pair<Feed, Long>> {
        val userId = CurrentUser.userId()
        val feeds = feedRepository.findAllActiveByUserId(userId)
        return feeds.map { feed ->
            val unreadCount = feedItemRepository.countUnreadByFeedIdAndUserId(feed.id, userId)
            feed to unreadCount
        }
    }

    fun deleteFeed(feedId: UUID): Feed? {
        val userId = CurrentUser.userId()
        val feed = feedRepository.findActiveByIdAndUserId(feedId, userId) ?: return null
        feed.deletedAt = Instant.now()
        feed.updatedAt = Instant.now()
        return feedRepository.save(feed)
    }

    suspend fun refreshFeed(feedId: UUID?): List<Pair<Feed, Int>> {
        val userId = CurrentUser.userId()
        val feeds = if (feedId != null) {
            val feed = feedRepository.findActiveByIdAndUserId(feedId, userId) ?: return emptyList()
            listOf(feed)
        } else {
            feedRepository.findAllActiveByUserId(userId)
        }

        return feeds.map { feed ->
            val newCount = rssFetchService.fetchAndStore(feed)
            feed to newCount
        }
    }
}
