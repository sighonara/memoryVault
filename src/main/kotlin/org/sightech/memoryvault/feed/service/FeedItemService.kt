package org.sightech.memoryvault.feed.service

import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class FeedItemService(private val feedItemRepository: FeedItemRepository) {

    fun getItems(feedId: UUID, limit: Int?, unreadOnly: Boolean): List<FeedItem> {
        val items = if (unreadOnly) {
            feedItemRepository.findUnreadByFeedId(feedId)
        } else {
            feedItemRepository.findByFeedId(feedId)
        }
        return if (limit != null && limit > 0) items.take(limit) else items
    }

    fun markItemRead(itemId: UUID): FeedItem? {
        val item = feedItemRepository.findById(itemId).orElse(null) ?: return null
        item.readAt = Instant.now()
        return feedItemRepository.save(item)
    }

    fun markItemUnread(itemId: UUID): FeedItem? {
        val item = feedItemRepository.findById(itemId).orElse(null) ?: return null
        item.readAt = null
        return feedItemRepository.save(item)
    }

    @Transactional
    fun markFeedRead(feedId: UUID): Int {
        return feedItemRepository.markAllReadByFeedId(feedId, Instant.now())
    }
}
