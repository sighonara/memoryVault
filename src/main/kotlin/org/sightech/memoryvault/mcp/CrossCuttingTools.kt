package org.sightech.memoryvault.mcp

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.logging.LogService
import org.sightech.memoryvault.scheduling.entity.JobType
import org.sightech.memoryvault.scheduling.service.SyncJobService
import org.sightech.memoryvault.search.ContentType
import org.sightech.memoryvault.search.SearchService
import org.sightech.memoryvault.stats.StatsService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CrossCuttingTools(
    private val searchService: SearchService,
    private val statsService: StatsService,
    private val syncJobService: SyncJobService,
    private val logService: LogService
) {

    @Tool(description = "Search across all content — bookmarks, feed items, and videos. Returns ranked results. Optionally filter by type: BOOKMARK, FEED_ITEM, VIDEO (comma-separated for multiple).")
    fun search(query: String, types: String?): String {
        val typeList = types?.split(",")?.map { it.trim().uppercase() }?.map { ContentType.valueOf(it) }

        val results = searchService.search(query, typeList, CurrentUser.userId(), 20)
        if (results.isEmpty()) return "No results found."

        val lines = results.map { r ->
            "- [${r.type}] ${r.title ?: "(no title)"} — ${r.url ?: "no url"} (rank: ${"%.2f".format(r.rank)})"
        }
        return "${results.size} result(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Get system statistics — content counts, storage usage, sync health, and failure counts. Use when the user wants an overview of their MemoryVault.")
    fun getStats(): String {
        val stats = statsService.getStats(CurrentUser.userId())

        val storageStr = formatBytes(stats.storageUsedBytes)

        val lines = mutableListOf<String>()
        lines.add("Content:")
        lines.add("  ${stats.bookmarkCount} bookmarks")
        lines.add("  ${stats.feedCount} feeds, ${stats.feedItemCount} items (${stats.unreadFeedItemCount} unread)")
        lines.add("  ${stats.youtubeListCount} playlists, ${stats.downloadedVideoCount}/${stats.videoCount} downloaded, ${stats.removedVideoCount} removed")
        lines.add("  ${stats.tagCount} tags")
        lines.add("")
        lines.add("Storage: $storageStr")
        lines.add("")
        lines.add("Sync health:")
        lines.add("  Last feed sync: ${stats.lastFeedSync ?: "never"}")
        lines.add("  Last YouTube sync: ${stats.lastYoutubeSync ?: "never"}")
        if (stats.feedsWithFailures > 0) lines.add("  ${stats.feedsWithFailures} feed(s) with failures")
        if (stats.youtubeListsWithFailures > 0) lines.add("  ${stats.youtubeListsWithFailures} playlist(s) with failures")

        return lines.joinToString("\n")
    }

    @Tool(description = "View job execution history. Shows recent sync job runs with status, timing, and metadata. Optionally filter by type (RSS_FETCH, YT_SYNC, BOOKMARK_ARCHIVE) and limit results.")
    fun listJobs(type: String?, limit: Int?): String {
        val effectiveLimit = limit ?: 10
        val jobType = type?.let { JobType.valueOf(it.trim().uppercase()) }
        val jobs = syncJobService.listJobs(CurrentUser.userId(), jobType, effectiveLimit)
        if (jobs.isEmpty()) return "No job history found."

        val lines = jobs.map { job ->
            val duration = if (job.completedAt != null) {
                val secs = java.time.Duration.between(job.startedAt, job.completedAt).seconds
                " (${secs}s)"
            } else ""
            val error = if (job.errorMessage != null) " — ${job.errorMessage}" else ""
            val meta = if (job.metadata != null) " ${job.metadata}" else ""
            "- [${job.status}] ${job.type} (${job.triggeredBy}) at ${job.startedAt}$duration$error$meta"
        }
        return "${jobs.size} job(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Retrieve application logs. Filter by level (INFO, WARN, ERROR), logger/service name, and limit. Use when diagnosing issues or checking system activity.")
    fun getLogs(level: String?, service: String?, limit: Int?): String {
        val logs = logService.getLogs(level, service, limit ?: 50)
        if (logs.isEmpty()) return "No log entries found."

        val lines = logs.map { entry ->
            val shortLogger = entry.logger.substringAfterLast(".")
            "${entry.timestamp} [${entry.level}] $shortLogger — ${entry.message}"
        }
        return "${logs.size} log entries:\n${lines.joinToString("\n")}"
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "${"%.1f".format(bytes / 1_073_741_824.0)} GB"
            bytes >= 1_048_576 -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
            bytes >= 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            else -> "$bytes bytes"
        }
    }
}
