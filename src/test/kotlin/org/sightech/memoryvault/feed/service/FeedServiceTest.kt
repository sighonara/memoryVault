package org.sightech.memoryvault.feed.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedCategory
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FeedServiceTest {

    private val feedRepository = mockk<FeedRepository>()
    private val feedItemRepository = mockk<FeedItemRepository>()
    private val rssFetchService = mockk<RssFetchService>()
    private val feedCategoryService = mockk<FeedCategoryService>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = FeedService(feedRepository, feedItemRepository, rssFetchService, feedCategoryService, eventPublisher)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val defaultCategory = FeedCategory(userId = userId, name = "Subscribed")

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        // Mock SecurityContext to return our test userId
        val securityContext = mockk<SecurityContext>()
        val authentication = mockk<Authentication>()
        every { securityContext.authentication } returns authentication
        every { authentication.principal } returns userId.toString()
        SecurityContextHolder.setContext(securityContext)
    }

    @AfterTest
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `addFeed creates feed with default category and triggers initial fetch`() {
        every { feedCategoryService.getSubscribedCategory() } returns defaultCategory
        every { feedRepository.save(any()) } answers { firstArg() }
        coEvery { rssFetchService.fetchAndStore(any()) } returns 5

        val result = runBlocking { service.addFeed("https://example.com/rss") }

        assertNotNull(result)
        assertEquals("https://example.com/rss", result.url)
        assertEquals(defaultCategory, result.category)
        verify { feedRepository.save(any()) }
        coVerify { rssFetchService.fetchAndStore(any()) }
    }

    @Test
    fun `addFeed with categoryId uses specified category`() {
        val customCategory = FeedCategory(userId = userId, name = "Tech")
        every { feedCategoryService.getCategoryById(customCategory.id) } returns customCategory
        every { feedRepository.save(any()) } answers { firstArg() }
        coEvery { rssFetchService.fetchAndStore(any()) } returns 3

        val result = runBlocking { service.addFeed("https://example.com/rss", customCategory.id) }

        assertNotNull(result)
        assertEquals(customCategory, result.category)
        verify(exactly = 0) { feedCategoryService.getSubscribedCategory() }
    }

    @Test
    fun `addFeed with invalid categoryId falls back to subscribed`() {
        val badId = UUID.randomUUID()
        every { feedCategoryService.getCategoryById(badId) } returns null
        every { feedCategoryService.getSubscribedCategory() } returns defaultCategory
        every { feedRepository.save(any()) } answers { firstArg() }
        coEvery { rssFetchService.fetchAndStore(any()) } returns 0

        val result = runBlocking { service.addFeed("https://example.com/rss", badId) }

        assertNotNull(result)
        assertEquals(defaultCategory, result.category)
    }

    @Test
    fun `listFeeds returns feeds with unread counts`() {
        val feed1 = Feed(userId = userId, url = "https://a.com/rss").apply { title = "Feed A" }
        val feed2 = Feed(userId = userId, url = "https://b.com/rss").apply { title = "Feed B" }
        every { feedRepository.findAllActiveByUserId(userId) } returns listOf(feed1, feed2)
        every { feedItemRepository.countUnreadByFeedIdAndUserId(feed1.id, userId) } returns 3
        every { feedItemRepository.countUnreadByFeedIdAndUserId(feed2.id, userId) } returns 0

        val result = service.listFeeds()

        assertEquals(2, result.size)
        assertEquals(3L, result[0].second)
        assertEquals(0L, result[1].second)
    }

    @Test
    fun `deleteFeed soft deletes`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss")
        every { feedRepository.findActiveByIdAndUserId(feed.id, userId) } returns feed
        every { feedRepository.save(any()) } answers { firstArg() }

        val result = service.deleteFeed(feed.id)

        assertNotNull(result)
        assertNotNull(result.deletedAt)
    }

    @Test
    fun `deleteFeed returns null for nonexistent feed`() {
        val id = UUID.randomUUID()
        every { feedRepository.findActiveByIdAndUserId(id, userId) } returns null

        val result = service.deleteFeed(id)

        assertNull(result)
    }

    @Test
    fun `refreshFeed refreshes single feed`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss")
        every { feedRepository.findActiveByIdAndUserId(feed.id, userId) } returns feed
        coEvery { rssFetchService.fetchAndStore(feed) } returns 3

        val result = runBlocking { service.refreshFeed(feed.id) }

        assertEquals(1, result.size)
        assertEquals(3, result[0].second)
    }

    @Test
    fun `refreshFeed with null refreshes all feeds`() {
        val feed1 = Feed(userId = userId, url = "https://a.com/rss")
        val feed2 = Feed(userId = userId, url = "https://b.com/rss")
        every { feedRepository.findAllActiveByUserId(userId) } returns listOf(feed1, feed2)
        coEvery { rssFetchService.fetchAndStore(any()) } returns 2

        val result = runBlocking { service.refreshFeed(null) }

        assertEquals(2, result.size)
    }

    @Test
    fun `listFeedsByCategory returns feeds in category with unread counts`() {
        val feed1 = Feed(userId = userId, url = "https://a.com/rss", category = defaultCategory)
        val feed2 = Feed(userId = userId, url = "https://b.com/rss", category = defaultCategory)
        every { feedRepository.findAllActiveByCategoryId(userId, defaultCategory.id) } returns listOf(feed1, feed2)
        every { feedItemRepository.countUnreadByFeedIdAndUserId(feed1.id, userId) } returns 5
        every { feedItemRepository.countUnreadByFeedIdAndUserId(feed2.id, userId) } returns 0

        val result = service.listFeedsByCategory(defaultCategory.id)

        assertEquals(2, result.size)
        assertEquals(5L, result[0].second)
        assertEquals(0L, result[1].second)
    }

    @Test
    fun `moveFeedToCategory moves feed to new category`() {
        val newCategory = FeedCategory(userId = userId, name = "Tech")
        val feed = Feed(userId = userId, url = "https://example.com/rss", category = defaultCategory)
        every { feedRepository.findActiveByIdAndUserId(feed.id, userId) } returns feed
        every { feedCategoryService.getCategoryById(newCategory.id) } returns newCategory
        every { feedRepository.save(any()) } answers { firstArg() }

        val result = service.moveFeedToCategory(feed.id, newCategory.id)

        assertNotNull(result)
        assertEquals(newCategory, result.category)
    }

    @Test
    fun `moveFeedToCategory returns null for nonexistent feed`() {
        val id = UUID.randomUUID()
        every { feedRepository.findActiveByIdAndUserId(id, userId) } returns null

        val result = service.moveFeedToCategory(id, defaultCategory.id)

        assertNull(result)
    }

    @Test
    fun `moveFeedToCategory returns null for nonexistent category`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss")
        val badCategoryId = UUID.randomUUID()
        every { feedRepository.findActiveByIdAndUserId(feed.id, userId) } returns feed
        every { feedCategoryService.getCategoryById(badCategoryId) } returns null

        val result = service.moveFeedToCategory(feed.id, badCategoryId)

        assertNull(result)
    }
}
