package org.sightech.memoryvault.mcp

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.youtube.service.VideoService
import org.sightech.memoryvault.youtube.service.YoutubeListService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class YoutubeTools(
    private val youtubeListService: YoutubeListService,
    private val videoService: VideoService
) {

    @Tool(description = "Subscribe to a YouTube playlist for archival. Immediately fetches metadata and queues all videos for download. Use when the user wants to archive or track a YouTube playlist.")
    fun addYoutubeList(url: String): String {
        val (list, syncResult) = youtubeListService.addList(CurrentUser.userId(), url)
        return "Added playlist: \"${list.name ?: list.url}\" — ${syncResult.newVideos} video(s) found, " +
            "${syncResult.downloadSuccesses} downloaded, ${syncResult.downloadFailures} failed — id: ${list.id}"
    }

    @Tool(description = "List all tracked YouTube playlists with video counts and download progress. Use when the user wants to see their archived playlists.")
    fun listYoutubeLists(): String {
        val lists = youtubeListService.listLists(CurrentUser.userId())
        if (lists.isEmpty()) return "No playlists tracked."

        val lines = lists.map { (list, stats) ->
            "- ${list.name ?: list.url} — ${stats.downloadedVideos}/${stats.totalVideos} downloaded" +
                (if (stats.removedVideos > 0) ", ${stats.removedVideos} removed" else "") +
                " — id: ${list.id}"
        }
        return "${lists.size} playlist(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Browse archived videos. Filter by playlist, search by title or channel, or show only videos removed from YouTube. Use when the user wants to see their archived videos.")
    fun listArchivedVideos(listId: String?, query: String?, removedOnly: Boolean?): String {
        val videos = videoService.getVideos(
            listId?.let { UUID.fromString(it) },
            query,
            removedOnly ?: false
        )
        if (videos.isEmpty()) return "No videos found."

        val lines = videos.map { v ->
            val status = when {
                v.removedFromYoutube -> "[REMOVED]"
                v.downloadedAt != null -> "[downloaded]"
                else -> "[pending]"
            }
            val tagStr = if (v.tags.isNotEmpty()) " [${v.tags.joinToString(", ") { it.name }}]" else ""
            "- $status ${v.title ?: "(no title)"} — ${v.channelName ?: "unknown"}$tagStr — id: ${v.id}"
        }
        return "${videos.size} video(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Get detailed status for a single archived video. Shows download status, file path, and whether it was removed from YouTube. Use when the user asks about a specific video.")
    fun getVideoStatus(videoId: String): String {
        val video = videoService.getVideoStatus(UUID.fromString(videoId))
            ?: return "Video not found."

        val lines = mutableListOf<String>()
        lines.add("Title: ${video.title ?: "(no title)"}")
        lines.add("Channel: ${video.channelName ?: "unknown"}")
        lines.add("YouTube URL: ${video.youtubeUrl}")
        lines.add("Duration: ${video.durationSeconds?.let { "${it / 60}m ${it % 60}s" } ?: "unknown"}")

        if (video.downloadedAt != null) {
            lines.add("Status: Downloaded")
            lines.add("File: ${video.filePath}")
            lines.add("Downloaded at: ${video.downloadedAt}")
        } else {
            lines.add("Status: Pending download")
        }

        if (video.removedFromYoutube) {
            lines.add("REMOVED from YouTube (detected: ${video.removedDetectedAt})")
        }

        if (video.tags.isNotEmpty()) {
            lines.add("Tags: ${video.tags.joinToString(", ") { it.name }}")
        }

        return lines.joinToString("\n")
    }

    @Tool(description = "Re-sync one or all YouTube playlists. Fetches latest metadata, detects removed videos, and downloads new ones. Pass a listId to refresh one playlist, or omit to refresh all.")
    fun refreshYoutubeList(listId: String?): String {
        val results = youtubeListService.refreshList(CurrentUser.userId(), listId?.let { UUID.fromString(it) })
        if (results.isEmpty()) return "No playlists to refresh."

        val lines = results.map { r ->
            "- ${r.list.name ?: r.list.url}: ${r.newVideos} new, ${r.removedVideos} removed, " +
                "${r.downloadSuccesses} downloaded, ${r.downloadFailures} failed"
        }
        return "Refreshed ${results.size} playlist(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Remove a YouTube playlist from tracking. This is a soft delete — the playlist and its videos can be recovered. Use when the user wants to stop archiving a playlist.")
    fun deleteYoutubeList(listId: String): String {
        val list = youtubeListService.deleteList(UUID.fromString(listId))
            ?: return "Playlist not found."
        return "Deleted playlist: \"${list.name ?: list.url}\""
    }
}
