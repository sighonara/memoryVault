package org.sightech.memoryvault.feed.service

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

    suspend fun addFeed(userId: UUID, url: String): Feed {
        val feed = feedRepository.save(Feed(userId = userId, url = url))
        rssFetchService.fetchAndStore(feed)
        return feed
    }

    fun listFeeds(userId: UUID): List<Pair<Feed, Long>> {
        val feeds = feedRepository.findAllActiveByUserId(userId)
        return feeds.map { feed ->
            val unreadCount = feedItemRepository.countUnreadByFeedId(feed.id)
            feed to unreadCount
        }
    }

    fun deleteFeed(feedId: UUID): Feed? {
        val feed = feedRepository.findActiveById(feedId) ?: return null
        feed.deletedAt = Instant.now()
        feed.updatedAt = Instant.now()
        return feedRepository.save(feed)
    }

    suspend fun refreshFeed(userId: UUID, feedId: UUID?): List<Pair<Feed, Int>> {
        val feeds = if (feedId != null) {
            val feed = feedRepository.findActiveById(feedId) ?: return emptyList()
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
