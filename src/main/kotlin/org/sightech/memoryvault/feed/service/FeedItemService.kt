package org.sightech.memoryvault.feed.service

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class FeedItemService(private val feedItemRepository: FeedItemRepository) {

    fun getItems(feedId: UUID, limit: Int?, unreadOnly: Boolean): List<FeedItem> {
        val userId = CurrentUser.userId()
        val items = if (unreadOnly) {
            feedItemRepository.findUnreadByFeedIdAndUserId(feedId, userId)
        } else {
            feedItemRepository.findByFeedIdAndUserId(feedId, userId)
        }
        return if (limit != null && limit > 0) items.take(limit) else items
    }

    fun markItemRead(itemId: UUID): FeedItem? {
        val userId = CurrentUser.userId()
        val item = feedItemRepository.findByIdAndUserId(itemId, userId) ?: return null
        item.readAt = Instant.now()
        return feedItemRepository.save(item)
    }

    fun markItemUnread(itemId: UUID): FeedItem? {
        val userId = CurrentUser.userId()
        val item = feedItemRepository.findByIdAndUserId(itemId, userId) ?: return null
        item.readAt = null
        return feedItemRepository.save(item)
    }

    @Transactional
    fun markFeedRead(feedId: UUID): Int {
        val userId = CurrentUser.userId()
        return feedItemRepository.markAllReadByFeedIdAndUserId(feedId, userId, Instant.now())
    }
}
