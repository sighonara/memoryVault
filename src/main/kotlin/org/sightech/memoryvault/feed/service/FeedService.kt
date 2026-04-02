package org.sightech.memoryvault.feed.service

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.sightech.memoryvault.websocket.FeedSyncCompleted
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class FeedService(
    private val feedRepository: FeedRepository,
    private val feedItemRepository: FeedItemRepository,
    private val rssFetchService: RssFetchService,
    private val feedCategoryService: FeedCategoryService,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun addFeed(url: String, categoryId: UUID? = null): Feed {
        val userId = CurrentUser.userId()
        val category = if (categoryId != null) {
            feedCategoryService.getCategoryById(categoryId)
                ?: feedCategoryService.getSubscribedCategory()
        } else {
            feedCategoryService.getSubscribedCategory()
        }
        val feed = feedRepository.save(Feed(userId = userId, url = url, category = category))
        rssFetchService.fetchAndStore(feed)
        log.info("Added feed url={} categoryId={} feedId={}", url, category.id, feed.id)
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

    fun listFeedsByCategory(categoryId: UUID): List<Pair<Feed, Long>> {
        val userId = CurrentUser.userId()
        val feeds = feedRepository.findAllActiveByCategoryId(userId, categoryId)
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

        val results = feeds.map { feed ->
            val newCount = rssFetchService.fetchAndStore(feed)
            feed to newCount
        }

        val totalNew = results.sumOf { it.second }
        eventPublisher.publishEvent(FeedSyncCompleted(
            userId = userId, timestamp = java.time.Instant.now(),
            feedId = feedId, newItemCount = totalNew, feedsRefreshed = results.size
        ))

        return results
    }

    fun moveFeedToCategory(feedId: UUID, categoryId: UUID): Feed? {
        val userId = CurrentUser.userId()
        val feed = feedRepository.findActiveByIdAndUserId(feedId, userId) ?: return null
        val category = feedCategoryService.getCategoryById(categoryId) ?: return null
        feed.category = category
        feed.updatedAt = Instant.now()
        log.info("Moved feed feedId={} to categoryId={}", feedId, categoryId)
        return feedRepository.save(feed)
    }
}
