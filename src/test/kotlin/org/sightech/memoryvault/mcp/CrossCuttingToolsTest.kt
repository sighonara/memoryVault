package org.sightech.memoryvault.mcp

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.logging.LogEntry
import org.sightech.memoryvault.logging.LogService
import org.sightech.memoryvault.scheduling.entity.JobStatus
import org.sightech.memoryvault.scheduling.entity.JobType
import org.sightech.memoryvault.scheduling.entity.SyncJob
import org.sightech.memoryvault.scheduling.entity.TriggerSource
import org.sightech.memoryvault.scheduling.service.SyncJobService
import org.sightech.memoryvault.search.ContentType
import org.sightech.memoryvault.search.SearchResult
import org.sightech.memoryvault.search.SearchService
import org.sightech.memoryvault.stats.StatsService
import org.sightech.memoryvault.stats.SystemStats
import java.time.Instant
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CrossCuttingToolsTest {

    private val searchService = mockk<SearchService>()
    private val statsService = mockk<StatsService>()
    private val syncJobService = mockk<SyncJobService>()
    private val logService = mockk<LogService>()

    private val tools = CrossCuttingTools(searchService, statsService, syncJobService, logService)

    @Test
    fun `search returns formatted results`() {
        every { searchService.search("kotlin", null, any(), 20) } returns listOf(
            SearchResult(ContentType.BOOKMARK, UUID.randomUUID(), "Kotlin Docs", "https://kotlinlang.org", 0.8f),
            SearchResult(ContentType.FEED_ITEM, UUID.randomUUID(), "Kotlin 2.0", "https://blog.example.com", 0.6f)
        )

        val result = tools.search("kotlin", null)
        assertContains(result, "2 result(s)")
        assertContains(result, "[BOOKMARK]")
        assertContains(result, "Kotlin Docs")
        assertContains(result, "[FEED_ITEM]")
    }

    @Test
    fun `search returns no results message`() {
        every { searchService.search("nonexistent", null, any(), 20) } returns emptyList()
        assertEquals("No results found.", tools.search("nonexistent", null))
    }

    @Test
    fun `search with type filter`() {
        every { searchService.search("test", listOf(ContentType.VIDEO), any(), 20) } returns listOf(
            SearchResult(ContentType.VIDEO, UUID.randomUUID(), "Test Video", "url", 0.5f)
        )

        val result = tools.search("test", "VIDEO")
        assertContains(result, "[VIDEO]")
    }

    @Test
    fun `getStats returns formatted summary`() {
        every { statsService.getStats(any()) } returns SystemStats(
            bookmarkCount = 42,
            feedCount = 5,
            feedItemCount = 1200,
            unreadFeedItemCount = 87,
            youtubeListCount = 3,
            videoCount = 150,
            downloadedVideoCount = 140,
            removedVideoCount = 8,
            tagCount = 15,
            storageUsedBytes = 5_368_709_120,
            lastFeedSync = Instant.parse("2026-03-05T10:00:00Z"),
            lastYoutubeSync = Instant.parse("2026-03-05T08:00:00Z"),
            feedsWithFailures = 1,
            youtubeListsWithFailures = 0
        )

        val result = tools.getStats()
        assertContains(result, "42 bookmarks")
        assertContains(result, "87 unread")
        assertContains(result, "140/150 downloaded")
        assertContains(result, "8 removed")
        assertContains(result, "5.0 GB")
        assertContains(result, "15 tags")
    }

    @Test
    fun `listJobs returns formatted history`() {
        every { syncJobService.listJobs(any(), null, 10) } returns listOf(
            SyncJob(userId = UUID.randomUUID(), type = JobType.RSS_FETCH, triggeredBy = TriggerSource.SCHEDULED).apply {
                status = JobStatus.SUCCESS
                completedAt = Instant.parse("2026-03-05T10:00:00Z")
                metadata = """{"newItems": 12}"""
            },
            SyncJob(userId = UUID.randomUUID(), type = JobType.YT_SYNC, triggeredBy = TriggerSource.MANUAL).apply {
                status = JobStatus.FAILED
                completedAt = Instant.parse("2026-03-05T09:00:00Z")
                errorMessage = "Connection timeout"
            }
        )

        val result = tools.listJobs(null, null)
        assertContains(result, "RSS_FETCH")
        assertContains(result, "SUCCESS")
        assertContains(result, "FAILED")
        assertContains(result, "Connection timeout")
    }

    @Test
    fun `listJobs returns no jobs message`() {
        every { syncJobService.listJobs(any(), any(), any()) } returns emptyList()
        assertEquals("No job history found.", tools.listJobs(null, null))
    }

    @Test
    fun `getLogs returns formatted entries`() {
        every { logService.getLogs("ERROR", null, 10) } returns listOf(
            LogEntry(
                timestamp = Instant.parse("2026-03-05T10:00:01Z"),
                level = "ERROR",
                logger = "org.sightech.memoryvault.youtube.YtDlpService",
                message = "Download failed: connection refused",
                thread = "scheduler-1"
            )
        )

        val result = tools.getLogs("ERROR", null, 10)
        assertContains(result, "ERROR")
        assertContains(result, "YtDlpService")
        assertContains(result, "Download failed")
    }

    @Test
    fun `getLogs returns no logs message`() {
        every { logService.getLogs(null, null, 50) } returns emptyList()
        assertEquals("No log entries found.", tools.getLogs(null, null, null))
    }
}
