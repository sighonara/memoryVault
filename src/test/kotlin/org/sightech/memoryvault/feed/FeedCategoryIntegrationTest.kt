package org.sightech.memoryvault.feed

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedCategory
import org.sightech.memoryvault.feed.repository.FeedCategoryRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.sightech.memoryvault.feed.service.FeedCategoryService
import org.sightech.memoryvault.feed.service.FeedService
import org.sightech.memoryvault.feed.service.OpmlService
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
class FeedCategoryIntegrationTest {

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

    @Autowired lateinit var feedCategoryService: FeedCategoryService
    @Autowired lateinit var feedService: FeedService
    @Autowired lateinit var opmlService: OpmlService
    @Autowired lateinit var feedRepository: FeedRepository
    @Autowired lateinit var feedCategoryRepository: FeedCategoryRepository

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private fun defaultCategory(): FeedCategory =
        feedCategoryRepository.findActiveByUserIdAndNameIgnoreCase(userId, FeedCategory.SUBSCRIBED_NAME)
            ?: feedCategoryRepository.save(FeedCategory(userId = userId, name = FeedCategory.SUBSCRIBED_NAME))

    @Test
    fun `create category and verify it appears in list`() {
        val category = feedCategoryService.createCategory("Tech News")
        assertNotNull(category.id)
        assertEquals("Tech News", category.name)

        val categories = feedCategoryService.listCategories()
        assertTrue(categories.any { it.name == "Tech News" })
    }

    @Test
    fun `create feed in category`() {
        val category = feedCategoryService.createCategory("Science")
        val feed = feedRepository.save(
            Feed(userId = userId, url = "https://science-feed.example.com/rss", category = category)
        )

        val feedsByCategory = feedService.listFeedsByCategory(category.id)
        assertTrue(feedsByCategory.any { it.first.id == feed.id })
    }

    @Test
    fun `move feed between categories`() {
        val sourceCategory = feedCategoryService.createCategory("Source Cat")
        val targetCategory = feedCategoryService.createCategory("Target Cat")
        val feed = feedRepository.save(
            Feed(userId = userId, url = "https://move-test.example.com/rss", category = sourceCategory)
        )

        val moved = feedService.moveFeedToCategory(feed.id, targetCategory.id)
        assertNotNull(moved)
        assertEquals(targetCategory.id, moved.category?.id)

        val sourceFeeds = feedService.listFeedsByCategory(sourceCategory.id)
        assertTrue(sourceFeeds.none { it.first.id == feed.id })

        val targetFeeds = feedService.listFeedsByCategory(targetCategory.id)
        assertTrue(targetFeeds.any { it.first.id == feed.id })
    }

    @Test
    fun `delete category moves feeds to Subscribed`() {
        val subscribed = defaultCategory()
        val category = feedCategoryService.createCategory("Doomed Category")
        val feed = feedRepository.save(
            Feed(userId = userId, url = "https://doomed-feed.example.com/rss", category = category)
        )

        val deleted = feedCategoryService.deleteCategory(category.id)
        assertTrue(deleted)

        // Feed should now be in Subscribed
        val subscribedFeeds = feedService.listFeedsByCategory(subscribed.id)
        assertTrue(subscribedFeeds.any { it.first.id == feed.id })

        // Deleted category should not appear in list
        val categories = feedCategoryService.listCategories()
        assertTrue(categories.none { it.id == category.id })
    }

    @Test
    fun `rename category`() {
        val category = feedCategoryService.createCategory("Old Name")
        val renamed = feedCategoryService.renameCategory(category.id, "New Name")
        assertNotNull(renamed)
        assertEquals("New Name", renamed.name)
    }

    @Test
    fun `cannot rename Subscribed category`() {
        defaultCategory()
        val subscribed = feedCategoryService.getSubscribedCategory()
        try {
            feedCategoryService.renameCategory(subscribed.id, "Something Else")
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Subscribed"))
        }
    }

    @Test
    fun `cannot delete Subscribed category`() {
        defaultCategory()
        val subscribed = feedCategoryService.getSubscribedCategory()
        try {
            feedCategoryService.deleteCategory(subscribed.id)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Subscribed"))
        }
    }

    @Test
    fun `reorder categories`() {
        val cat1 = feedCategoryService.createCategory("Reorder A")
        val cat2 = feedCategoryService.createCategory("Reorder B")
        val cat3 = feedCategoryService.createCategory("Reorder C")

        // Reverse the order
        val reordered = feedCategoryService.reorderCategories(listOf(cat3.id, cat2.id, cat1.id))
        val cat3Updated = reordered.find { it.id == cat3.id }
        val cat1Updated = reordered.find { it.id == cat1.id }
        assertNotNull(cat3Updated)
        assertNotNull(cat1Updated)
        assertTrue(cat3Updated.sortOrder < cat1Updated.sortOrder)
    }

    @Test
    fun `OPML export and import round-trip`() {
        // Create a category with a feed
        val category = feedCategoryService.createCategory("OPML Test")
        feedRepository.save(
            Feed(userId = userId, url = "https://opml-test.example.com/rss", title = "OPML Feed", category = category)
        )

        // Export
        val opml = opmlService.exportOpml()
        assertTrue(opml.contains("OPML Test"))
        assertTrue(opml.contains("https://opml-test.example.com/rss"))

        // Import the same OPML — existing feed should be skipped
        val result = runBlocking { opmlService.importOpml(opml) }
        assertEquals(0, result.feedsAdded, "Existing feeds should be skipped on re-import")
        assertTrue(result.feedsSkipped > 0)
    }

    @Test
    fun `OPML import skips existing feeds and creates categories`() {
        // Pre-create a feed with a known URL
        val uniqueUrl = "https://import-skip-test-${UUID.randomUUID()}.example.com/rss"
        feedRepository.save(Feed(userId = userId, url = uniqueUrl, category = defaultCategory()))

        // Build OPML that includes the existing feed plus a reference to it under a new category
        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>Test</title></head>
              <body>
                <outline text="Imported Category">
                  <outline type="rss" text="Existing Feed" xmlUrl="$uniqueUrl" />
                </outline>
              </body>
            </opml>
        """.trimIndent()

        // The existing feed should be skipped (no network fetch), and category should NOT be created
        // because there are no new feeds to add to it (lazy creation)
        val result = runBlocking { opmlService.importOpml(opml) }
        assertEquals(0, result.feedsAdded)
        assertEquals(1, result.feedsSkipped)
        assertEquals(0, result.categoriesCreated)
    }

    @Test
    fun `findOrCreateByName returns existing category`() {
        val created = feedCategoryService.createCategory("Find Me")
        val (found, wasCreated) = feedCategoryService.findOrCreateByName("Find Me")
        assertEquals(created.id, found.id)
        assertEquals(false, wasCreated)
    }

    @Test
    fun `findOrCreateByName creates new category`() {
        val (created, wasCreated) = feedCategoryService.findOrCreateByName("Brand New ${UUID.randomUUID()}")
        assertNotNull(created.id)
        assertEquals(true, wasCreated)
    }
}
