package org.sightech.memoryvault.feed.service

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.sightech.memoryvault.websocket.ContentMutated
import org.sightech.memoryvault.websocket.ContentType
import org.sightech.memoryvault.websocket.MutationType
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class FeedItemService(
    private val feedItemRepository: FeedItemRepository,
    private val feedRepository: FeedRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun getItems(feedId: UUID, limit: Int?, unreadOnly: Boolean, sortOrder: String = "NEWEST_FIRST"): List<FeedItem> {
        val userId = CurrentUser.userId()
        val ascending = sortOrder == "OLDEST_FIRST"
        val items = if (unreadOnly) {
            if (ascending) feedItemRepository.findUnreadByFeedIdAndUserIdAsc(feedId, userId)
            else feedItemRepository.findUnreadByFeedIdAndUserId(feedId, userId)
        } else {
            if (ascending) feedItemRepository.findByFeedIdAndUserIdAsc(feedId, userId)
            else feedItemRepository.findByFeedIdAndUserId(feedId, userId)
        }
        return if (limit != null && limit > 0) items.take(limit) else items
    }

    fun getItemsByFeedIds(feedIds: List<UUID>, limit: Int?, unreadOnly: Boolean, sortOrder: String = "NEWEST_FIRST"): List<FeedItem> {
        if (feedIds.isEmpty()) return emptyList()
        val userId = CurrentUser.userId()
        val ascending = sortOrder == "OLDEST_FIRST"
        val items = if (unreadOnly) {
            if (ascending) feedItemRepository.findUnreadByFeedIdsAndUserIdAsc(feedIds, userId)
            else feedItemRepository.findUnreadByFeedIdsAndUserId(feedIds, userId)
        } else {
            if (ascending) feedItemRepository.findByFeedIdsAndUserIdAsc(feedIds, userId)
            else feedItemRepository.findByFeedIdsAndUserId(feedIds, userId)
        }
        return if (limit != null && limit > 0) items.take(limit) else items
    }

    fun markItemRead(itemId: UUID): FeedItem? {
        val userId = CurrentUser.userId()
        val item = feedItemRepository.findByIdAndUserId(itemId, userId) ?: return null
        item.readAt = Instant.now()
        log.info("Marked item read itemId={}", itemId)
        val saved = feedItemRepository.save(item)
        eventPublisher.publishEvent(ContentMutated(
            userId = userId, timestamp = Instant.now(),
            contentType = ContentType.FEED_ITEM, mutationType = MutationType.UPDATED,
            entityId = itemId
        ))
        return saved
    }

    fun markItemUnread(itemId: UUID): FeedItem? {
        val userId = CurrentUser.userId()
        val item = feedItemRepository.findByIdAndUserId(itemId, userId) ?: return null
        item.readAt = null
        log.info("Marked item unread itemId={}", itemId)
        val saved = feedItemRepository.save(item)
        eventPublisher.publishEvent(ContentMutated(
            userId = userId, timestamp = Instant.now(),
            contentType = ContentType.FEED_ITEM, mutationType = MutationType.UPDATED,
            entityId = itemId
        ))
        return saved
    }

    @Transactional
    fun markFeedRead(feedId: UUID): Int {
        val userId = CurrentUser.userId()
        val count = feedItemRepository.markAllReadByFeedIdAndUserId(feedId, userId, Instant.now())
        if (count > 0) {
            eventPublisher.publishEvent(ContentMutated(
                userId = userId, timestamp = Instant.now(),
                contentType = ContentType.FEED_ITEM, mutationType = MutationType.UPDATED
            ))
        }
        return count
    }

    @Transactional
    fun markCategoryRead(categoryId: UUID): Int {
        val userId = CurrentUser.userId()
        val feeds = feedRepository.findAllActiveByCategoryId(userId, categoryId)
        val now = Instant.now()
        val count = feeds.sumOf { feed ->
            feedItemRepository.markAllReadByFeedIdAndUserId(feed.id, userId, now)
        }
        log.info("Marked category read categoryId={} count={}", categoryId, count)
        if (count > 0) {
            eventPublisher.publishEvent(ContentMutated(
                userId = userId, timestamp = Instant.now(),
                contentType = ContentType.FEED_ITEM, mutationType = MutationType.UPDATED
            ))
        }
        return count
    }

    // TODO: Phase 7 stub — starred articles
    // fun starItem(itemId: UUID): FeedItem? {
    //     val userId = CurrentUser.userId()
    //     val item = feedItemRepository.findByIdAndUserId(itemId, userId) ?: return null
    //     item.starredAt = Instant.now()
    //     return feedItemRepository.save(item)
    // }

    // fun unstarItem(itemId: UUID): FeedItem? {
    //     val userId = CurrentUser.userId()
    //     val item = feedItemRepository.findByIdAndUserId(itemId, userId) ?: return null
    //     item.starredAt = null
    //     return feedItemRepository.save(item)
    // }
}
