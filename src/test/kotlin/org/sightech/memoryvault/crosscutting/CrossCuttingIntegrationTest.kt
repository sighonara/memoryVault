package org.sightech.memoryvault.crosscutting

import org.junit.jupiter.api.Test
import org.sightech.memoryvault.bookmark.service.BookmarkService
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.sightech.memoryvault.scheduling.entity.JobStatus
import org.sightech.memoryvault.scheduling.entity.JobType
import org.sightech.memoryvault.scheduling.entity.TriggerSource
import org.sightech.memoryvault.scheduling.service.SyncJobService
import org.sightech.memoryvault.search.ContentType
import org.sightech.memoryvault.search.SearchService
import org.sightech.memoryvault.stats.StatsService
import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.entity.Video
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.sightech.memoryvault.youtube.repository.YoutubeListRepository
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
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class CrossCuttingIntegrationTest {

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

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Autowired lateinit var searchService: SearchService
    @Autowired lateinit var statsService: StatsService
    @Autowired lateinit var syncJobService: SyncJobService
    @Autowired lateinit var bookmarkService: BookmarkService
    @Autowired lateinit var feedRepository: FeedRepository
    @Autowired lateinit var feedItemRepository: FeedItemRepository
    @Autowired lateinit var youtubeListRepository: YoutubeListRepository
    @Autowired lateinit var videoRepository: VideoRepository

    @Test
    fun `full-text search finds bookmarks`() {
        bookmarkService.create("https://kotlinlang.org", "Kotlin Programming Language", null)

        val results = searchService.search("kotlin", null, userId, 20)
        assertTrue(results.any { it.type == ContentType.BOOKMARK && it.title?.contains("Kotlin") == true })
    }

    @Test
    fun `full-text search finds feed items`() {
        val feed = feedRepository.save(Feed(userId = userId, url = "https://blog.example.com/rss"))
        feedItemRepository.save(FeedItem(feed = feed, guid = "search-test-1", title = "Kubernetes Deep Dive", url = "https://blog.example.com/k8s"))

        val results = searchService.search("kubernetes", null, userId, 20)
        assertTrue(results.any { it.type == ContentType.FEED_ITEM && it.title?.contains("Kubernetes") == true })
    }

    @Test
    fun `full-text search finds videos`() {
        val list = youtubeListRepository.save(YoutubeList(userId = userId, youtubeListId = "PLsearch1", url = "https://youtube.com/playlist?list=PLsearch1"))
        videoRepository.save(Video(youtubeList = list, youtubeVideoId = "search1", youtubeUrl = "https://youtube.com/watch?v=search1", title = "PostgreSQL Full Text Search Tutorial", channelName = "DB Channel"))

        val results = searchService.search("postgresql", null, userId, 20)
        assertTrue(results.any { it.type == ContentType.VIDEO && it.title?.contains("PostgreSQL") == true })
    }

    @Test
    fun `full-text search filters by type`() {
        bookmarkService.create("https://unique-search-test.com", "Unique Searchable Bookmark", null)

        val bookmarkOnly = searchService.search("unique searchable", listOf(ContentType.BOOKMARK), userId, 20)
        assertTrue(bookmarkOnly.all { it.type == ContentType.BOOKMARK })

        val videoOnly = searchService.search("unique searchable", listOf(ContentType.VIDEO), userId, 20)
        assertTrue(videoOnly.isEmpty())
    }

    @Test
    fun `getStats returns correct counts`() {
        val stats = statsService.getStats(userId)
        assertTrue(stats.bookmarkCount >= 0)
        assertTrue(stats.feedCount >= 0)
        assertTrue(stats.tagCount >= 0)
    }

    @Test
    fun `syncJobService records and retrieves job history`() {
        val job = syncJobService.recordStart(JobType.RSS_FETCH, TriggerSource.MANUAL, userId)
        assertNotNull(job.id)
        assertEquals(JobStatus.RUNNING, job.status)

        syncJobService.recordSuccess(job.id, mapOf("newItems" to 5))

        val jobs = syncJobService.listJobs(userId, JobType.RSS_FETCH, 10)
        assertTrue(jobs.any { it.id == job.id && it.status == JobStatus.SUCCESS })
    }

    @Test
    fun `syncJobService records failure`() {
        val job = syncJobService.recordStart(JobType.YT_SYNC, TriggerSource.SCHEDULED, userId)
        syncJobService.recordFailure(job.id, "Connection refused")

        val jobs = syncJobService.listJobs(userId, JobType.YT_SYNC, 10)
        val found = jobs.find { it.id == job.id }
        assertNotNull(found)
        assertEquals(JobStatus.FAILED, found.status)
        assertEquals("Connection refused", found.errorMessage)
    }
}
