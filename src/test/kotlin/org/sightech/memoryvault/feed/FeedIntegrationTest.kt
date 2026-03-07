package org.sightech.memoryvault.feed

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.sightech.memoryvault.feed.service.FeedItemService
import org.sightech.memoryvault.feed.service.FeedService
import org.sightech.memoryvault.feed.service.RssFetchService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class FeedIntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("memoryvault_test")
            withUsername("memoryvault")
            withPassword("memoryvault")
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired lateinit var feedService: FeedService
    @Autowired lateinit var feedItemService: FeedItemService
    @Autowired lateinit var rssFetchService: RssFetchService
    @Autowired lateinit var feedRepository: FeedRepository

    private val sampleXml = this::class.java.classLoader.getResource("fixtures/sample-rss.xml")!!.readText()
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `create feed and fetch items from XML`() {
        val feed = feedRepository.save(
            org.sightech.memoryvault.feed.entity.Feed(userId = userId, url = "https://test-feed.com/rss")
        )

        val newCount = runBlocking { rssFetchService.fetchAndStoreFromXml(feed, sampleXml) }

        assertEquals(3, newCount)

        val items = feedItemService.getItems(feed.id, null, false)
        assertEquals(3, items.size)
    }

    @Test
    fun `deduplication prevents duplicate items`() {
        val feed = feedRepository.save(
            org.sightech.memoryvault.feed.entity.Feed(userId = userId, url = "https://dedup-test.com/rss")
        )

        val first = runBlocking { rssFetchService.fetchAndStoreFromXml(feed, sampleXml) }
        assertEquals(3, first)

        val second = runBlocking { rssFetchService.fetchAndStoreFromXml(feed, sampleXml) }
        assertEquals(0, second)

        val items = feedItemService.getItems(feed.id, null, false)
        assertEquals(3, items.size)
    }

    @Test
    fun `mark item read and unread`() {
        val feed = feedRepository.save(
            org.sightech.memoryvault.feed.entity.Feed(userId = userId, url = "https://read-test.com/rss")
        )
        runBlocking { rssFetchService.fetchAndStoreFromXml(feed, sampleXml) }

        val items = feedItemService.getItems(feed.id, null, false)
        val item = items.first()

        val read = feedItemService.markItemRead(item.id)
        assertNotNull(read?.readAt)

        val unreadItems = feedItemService.getItems(feed.id, null, true)
        assertEquals(items.size - 1, unreadItems.size)

        val unread = feedItemService.markItemUnread(item.id)
        assertNull(unread?.readAt)
    }

    @Test
    fun `mark all items in feed as read`() {
        val feed = feedRepository.save(
            org.sightech.memoryvault.feed.entity.Feed(userId = userId, url = "https://markall-test.com/rss")
        )
        runBlocking { rssFetchService.fetchAndStoreFromXml(feed, sampleXml) }

        val count = feedItemService.markFeedRead(feed.id)
        assertEquals(3, count)

        val unread = feedItemService.getItems(feed.id, null, true)
        assertTrue(unread.isEmpty())
    }

    @Test
    fun `list feeds returns unread counts`() {
        val feed = feedRepository.save(
            org.sightech.memoryvault.feed.entity.Feed(userId = userId, url = "https://list-test.com/rss")
        )
        runBlocking { rssFetchService.fetchAndStoreFromXml(feed, sampleXml) }

        val feeds = feedService.listFeeds()
        val match = feeds.find { it.first.id == feed.id }
        assertNotNull(match)
        assertEquals(3L, match.second)
    }

    @Test
    fun `soft delete feed`() {
        val feed = feedRepository.save(
            org.sightech.memoryvault.feed.entity.Feed(userId = userId, url = "https://delete-test.com/rss")
        )

        val deleted = feedService.deleteFeed(feed.id)
        assertNotNull(deleted?.deletedAt)

        val feeds = feedService.listFeeds()
        assertTrue(feeds.none { it.first.id == feed.id })
    }

    @Test
    fun `feed metadata updated after fetch`() {
        val feed = feedRepository.save(
            org.sightech.memoryvault.feed.entity.Feed(userId = userId, url = "https://meta-test.com/rss")
        )
        runBlocking { rssFetchService.fetchAndStoreFromXml(feed, sampleXml) }

        val updated = feedRepository.findActiveByIdAndUserId(feed.id, userId)
        assertNotNull(updated)
        assertEquals("Test Feed", updated.title)
        assertEquals("A test RSS feed", updated.description)
        assertEquals("https://example.com", updated.siteUrl)
        assertNotNull(updated.lastFetchedAt)
    }
}
