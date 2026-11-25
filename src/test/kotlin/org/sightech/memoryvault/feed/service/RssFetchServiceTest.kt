package org.sightech.memoryvault.feed.service

import com.prof18.rssparser.RssParser
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.sightech.memoryvault.security.UnsafeUrlException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class RssFetchServiceTest {

    private val feedRepository = mockk<FeedRepository>()
    private val feedItemRepository = mockk<FeedItemRepository>()
    private val rssParser = RssParser()
    private val feedHttpFetcher = mockk<FeedHttpFetcher>()
    private val service = RssFetchService(feedRepository, feedItemRepository, rssParser, feedHttpFetcher)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val feed = Feed(userId = userId, url = "https://example.com/rss")

    private val sampleXml = this::class.java.classLoader.getResource("fixtures/sample-rss.xml")!!.readText()

    @BeforeEach
    fun setUp() {
        every { feedRepository.findById(feed.id) } returns Optional.of(feed)
    }

    @Test
    fun `fetchAndStore parses items and saves new ones`() {
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, any()) } returns false
        every { feedItemRepository.save(any()) } answers { firstArg() }
        every { feedRepository.save(any()) } answers { firstArg() }

        val result = runBlocking { service.fetchAndStoreFromXml(feed, sampleXml) }

        assertEquals(3, result)
        verify(exactly = 3) { feedItemRepository.save(any()) }
        verify { feedRepository.save(match { it.lastFetchedAt != null }) }
    }

    @Test
    fun `fetchAndStore fetches through the safe fetcher and stores items`() {
        every { feedHttpFetcher.fetch(feed.url) } returns sampleXml
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, any()) } returns false
        every { feedItemRepository.save(any()) } answers { firstArg() }
        every { feedRepository.save(any()) } answers { firstArg() }

        assertEquals(3, runBlocking { service.fetchAndStore(feed) })
    }

    @Test
    fun `fetchAndStore returns 0 when the fetcher refuses the url`() {
        every { feedHttpFetcher.fetch(feed.url) } throws UnsafeUrlException("blocked")

        assertEquals(0, runBlocking { service.fetchAndStore(feed) })
        verify(exactly = 0) { feedItemRepository.save(any()) }
    }

    @Test
    fun `fetchAndStore stores at most 500 items per fetch`() {
        val items = (1..600).joinToString("") { "<item><title>t$it</title><guid>g$it</guid></item>" }
        val xml = "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel><title>Big</title><link>https://example.com</link><description>d</description>$items</channel></rss>"
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, any()) } returns false
        every { feedItemRepository.save(any()) } answers { firstArg() }
        every { feedRepository.save(any()) } answers { firstArg() }

        assertEquals(500, runBlocking { service.fetchAndStoreFromXml(feed, xml) })
    }

    @Test
    fun `fetchAndStore skips existing items by guid`() {
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, "post-1") } returns true
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, "post-2") } returns true
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, "https://example.com/post-3") } returns false
        every { feedItemRepository.save(any()) } answers { firstArg() }
        every { feedRepository.save(any()) } answers { firstArg() }

        val result = runBlocking { service.fetchAndStoreFromXml(feed, sampleXml) }

        assertEquals(1, result)
        verify(exactly = 1) { feedItemRepository.save(any()) }
    }

    @Test
    fun `fetchAndStore uses link as guid fallback when guid is null`() {
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, "post-1") } returns true
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, "post-2") } returns true
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, "https://example.com/post-3") } returns true
        every { feedRepository.save(any()) } answers { firstArg() }

        val result = runBlocking { service.fetchAndStoreFromXml(feed, sampleXml) }

        assertEquals(0, result)
    }

    @Test
    fun `fetchAndStore updates feed metadata from channel`() {
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, any()) } returns false
        every { feedItemRepository.save(any()) } answers { firstArg() }
        every { feedRepository.save(any()) } answers { firstArg() }

        runBlocking { service.fetchAndStoreFromXml(feed, sampleXml) }

        verify { feedRepository.save(match { it.title == "Test Feed" && it.description == "A test RSS feed" && it.siteUrl == "https://example.com" }) }
    }
}
