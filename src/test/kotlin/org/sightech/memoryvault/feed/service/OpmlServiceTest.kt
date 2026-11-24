package org.sightech.memoryvault.feed.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedCategory
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import kotlin.test.AfterTest
import org.sightech.memoryvault.security.UnsafeUrlException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpmlServiceTest {

    private val feedRepository = mockk<FeedRepository>()
    private val feedService = mockk<FeedService>()
    private val feedCategoryService = mockk<FeedCategoryService>()
    private val service = OpmlService(feedRepository, feedService, feedCategoryService)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
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
    fun `exportOpml produces valid OPML with categories`() {
        val category = FeedCategory(userId = userId, name = "Tech", sortOrder = 1)
        val feed = Feed(userId = userId, url = "https://example.com/rss", category = category).apply {
            title = "Example Blog"
            siteUrl = "https://example.com"
        }
        every { feedCategoryService.listCategories() } returns listOf(category)
        every { feedRepository.findAllActiveByUserId(userId) } returns listOf(feed)

        val result = service.exportOpml()

        assertTrue(result.contains("opml"))
        assertTrue(result.contains("Tech"))
        assertTrue(result.contains("https://example.com/rss"))
        assertTrue(result.contains("Example Blog"))
    }

    @Test
    fun `importOpml skips existing feeds and creates new ones`() {
        val existingFeed = Feed(userId = userId, url = "https://existing.com/rss")
        every { feedRepository.findAllActiveByUserId(userId) } returns listOf(existingFeed)
        val newFeed = Feed(userId = userId, url = "https://new.com/rss")
        coEvery { feedService.addFeed("https://new.com/rss", any()) } returns newFeed
        val category = FeedCategory(userId = userId, name = "News")
        every { feedCategoryService.findOrCreateByName("News") } returns (category to true)

        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>Test</title></head>
              <body>
                <outline text="News" title="News">
                  <outline type="rss" text="Existing" xmlUrl="https://existing.com/rss" />
                  <outline type="rss" text="New Feed" xmlUrl="https://new.com/rss" />
                </outline>
              </body>
            </opml>
        """.trimIndent()

        val result = runBlocking { service.importOpml(opml) }

        assertEquals(1, result.feedsAdded)
        assertEquals(1, result.feedsSkipped)
    }

    @Test
    fun `importOpml rejects content over 2 MiB`() {
        every { feedRepository.findAllActiveByUserId(userId) } returns emptyList()
        val huge = "<opml><body>" + "x".repeat(2 * 1024 * 1024) + "</body></opml>"

        assertFailsWith<OpmlTooLargeException> { runBlocking { service.importOpml(huge) } }
    }

    @Test
    fun `importOpml stops after 1000 entries and dedupes urls within the file`() {
        every { feedRepository.findAllActiveByUserId(userId) } returns emptyList()
        coEvery { feedService.addFeed(any(), any()) } answers { Feed(userId = userId, url = firstArg()) }
        val entries = (1..1200).joinToString("") { """<outline type="rss" xmlUrl="https://example.com/f$it" />""" }
        val opml = """<opml version="2.0"><body>$entries<outline type="rss" xmlUrl="https://example.com/f1" /></body></opml>"""

        val result = runBlocking { service.importOpml(opml) }

        assertEquals(1000, result.feedsAdded)
        coVerify(exactly = 1000) { feedService.addFeed(any(), any()) }
    }

    @Test
    fun `importOpml dedupes repeated urls and skips unsafe ones`() {
        every { feedRepository.findAllActiveByUserId(userId) } returns emptyList()
        coEvery { feedService.addFeed("https://a.com/rss", any()) } returns Feed(userId = userId, url = "https://a.com/rss")
        coEvery { feedService.addFeed("http://169.254.169.254/", any()) } throws UnsafeUrlException("blocked")
        val opml = """<opml version="2.0"><body>
            <outline xmlUrl="https://a.com/rss" /><outline xmlUrl="https://A.com/rss" /><outline xmlUrl="http://169.254.169.254/" />
            </body></opml>"""

        val result = runBlocking { service.importOpml(opml) }

        assertEquals(1, result.feedsAdded)
        assertEquals(2, result.feedsSkipped)
    }

    @Test
    fun `importOpml handles top-level feeds without category`() {
        every { feedRepository.findAllActiveByUserId(userId) } returns emptyList()
        val newFeed = Feed(userId = userId, url = "https://example.com/rss")
        coEvery { feedService.addFeed("https://example.com/rss") } returns newFeed

        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>Test</title></head>
              <body>
                <outline type="rss" text="Example" xmlUrl="https://example.com/rss" />
              </body>
            </opml>
        """.trimIndent()

        val result = runBlocking { service.importOpml(opml) }

        assertEquals(1, result.feedsAdded)
        assertEquals(0, result.feedsSkipped)
    }
}
