package org.sightech.memoryvault.feed.service

import com.prof18.rssparser.RssParser
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class RssFetchServiceTest {

    private val feedRepository = mockk<FeedRepository>()
    private val feedItemRepository = mockk<FeedItemRepository>()
    private val rssParser = RssParser()
    private val service = RssFetchService(feedRepository, feedItemRepository, rssParser)
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
