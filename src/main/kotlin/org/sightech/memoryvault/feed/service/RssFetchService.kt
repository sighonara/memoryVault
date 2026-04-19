package org.sightech.memoryvault.feed.service

import com.prof18.rssparser.RssParser
import com.prof18.rssparser.model.RssItem
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Service
class RssFetchService(
    private val feedRepository: FeedRepository,
    private val feedItemRepository: FeedItemRepository,
    private val rssParser: RssParser
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun fetchAndStore(feed: Feed): Int {
        val channel = try {
            rssParser.getRssChannel(feed.url)
        } catch (e: Exception) {
            log.warn("Failed to fetch RSS feed url={}: {}", feed.url, e.message)
            return 0
        }
        return processChannel(feed, channel)
    }

    suspend fun fetchAndStoreFromXml(feed: Feed, xml: String): Int {
        val channel = try {
            rssParser.parse(xml)
        } catch (e: Exception) {
            log.warn("Failed to parse RSS XML for feed={}: {}", feed.id, e.message)
            return 0
        }
        return processChannel(feed, channel)
    }

    private fun processChannel(feed: Feed, channel: com.prof18.rssparser.model.RssChannel): Int {
        // Re-fetch to avoid optimistic locking conflicts on repeated calls with a stale entity
        val currentFeed = feedRepository.findById(feed.id).orElse(feed)
        currentFeed.title = channel.title
        currentFeed.description = channel.description
        currentFeed.siteUrl = channel.link
        currentFeed.lastFetchedAt = Instant.now()
        currentFeed.updatedAt = Instant.now()
        feedRepository.save(currentFeed)

        var newCount = 0
        for (item in channel.items) {
            val guid = resolveGuid(item)
            if (feedItemRepository.existsByFeedIdAndGuid(feed.id, guid)) continue

            val feedItem = FeedItem(
                feed = feed,
                guid = guid,
                title = item.title,
                url = item.link,
                content = item.description,
                author = item.author,
                imageUrl = item.image,
                publishedAt = parseDate(item.pubDate)
            )
            feedItemRepository.save(feedItem)
            newCount++
        }

        log.info("Processed feed feedId={}, newItems={}", feed.id, newCount)
        return newCount
    }

    private fun resolveGuid(item: RssItem): String {
        return item.guid
            ?: item.link
            ?: hashFallback(item.title, item.pubDate)
    }

    private fun hashFallback(title: String?, pubDate: String?): String {
        val input = "${title.orEmpty()}|${pubDate.orEmpty()}"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun parseDate(dateStr: String?): Instant? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            ZonedDateTime.parse(dateStr, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        } catch (e: Exception) {
            log.debug("RFC-1123 date parse failed for '{}': {}", dateStr, e.message)
            try {
                Instant.parse(dateStr)
            } catch (e2: Exception) {
                log.debug("ISO date parse failed for '{}': {}", dateStr, e2.message)
                null
            }
        }
    }
}
