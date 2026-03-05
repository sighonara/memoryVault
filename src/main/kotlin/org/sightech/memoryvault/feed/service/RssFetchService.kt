package org.sightech.memoryvault.feed.service

import com.prof18.rssparser.RssParser
import com.prof18.rssparser.model.RssItem
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
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

    suspend fun fetchAndStore(feed: Feed): Int {
        val channel = rssParser.getRssChannel(feed.url)
        return processChannel(feed, channel)
    }

    suspend fun fetchAndStoreFromXml(feed: Feed, xml: String): Int {
        val channel = rssParser.parse(xml)
        return processChannel(feed, channel)
    }

    private fun processChannel(feed: Feed, channel: com.prof18.rssparser.model.RssChannel): Int {
        feed.title = channel.title
        feed.description = channel.description
        feed.siteUrl = channel.link
        feed.lastFetchedAt = Instant.now()
        feed.updatedAt = Instant.now()
        feedRepository.save(feed)

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
            try {
                Instant.parse(dateStr)
            } catch (e2: Exception) {
                null
            }
        }
    }
}
