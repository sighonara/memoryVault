package org.sightech.memoryvault.youtube.service

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.sightech.memoryvault.youtube.repository.YoutubeListRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

data class ListStats(
    val totalVideos: Long,
    val downloadedVideos: Long,
    val removedVideos: Long
)

@Service
class YoutubeListService(
    private val youtubeListRepository: YoutubeListRepository,
    private val videoRepository: VideoRepository,
    private val ytDlpService: YtDlpService,
    private val videoSyncService: VideoSyncService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun addList(url: String): Pair<YoutubeList, SyncResult> {
        YoutubeUrlValidator.requireYoutubeUrl(url)
        val userId = CurrentUser.userId()
        val playlistId = extractPlaylistId(url)
        val list = youtubeListRepository.save(
            YoutubeList(userId = userId, youtubeListId = playlistId, url = url)
        )

        val metadata = ytDlpService.fetchPlaylistMetadata(url)

        // TODO: yt-dlp --flat-playlist doesn't include playlist-level metadata. To get the real
        //  playlist title, use a separate `yt-dlp --dump-single-json <url>` call and read
        //  the top-level "title" field. For now we fall back to the playlist ID.
        if (list.name == null && metadata.isNotEmpty()) {
            list.name = "Playlist $playlistId"
            list.updatedAt = Instant.now()
            youtubeListRepository.save(list)
        }

        val syncResult = videoSyncService.syncList(list, metadata)
        log.info("Added YouTube list listId={}", list.id)
        return list to syncResult
    }

    fun listLists(): List<Pair<YoutubeList, ListStats>> {
        val userId = CurrentUser.userId()
        val lists = youtubeListRepository.findAllActiveByUserId(userId)
        return lists.map { list ->
            val stats = ListStats(
                totalVideos = videoRepository.countByYoutubeListIdAndUserId(list.id, userId),
                downloadedVideos = videoRepository.countDownloadedByYoutubeListIdAndUserId(list.id, userId),
                removedVideos = videoRepository.countRemovedByYoutubeListIdAndUserId(list.id, userId)
            )
            list to stats
        }
    }

    fun deleteList(listId: UUID): YoutubeList? {
        val userId = CurrentUser.userId()
        val list = youtubeListRepository.findActiveByIdAndUserId(listId, userId) ?: return null
        list.deletedAt = Instant.now()
        list.updatedAt = Instant.now()
        log.info("Soft-deleted YouTube list listId={}", listId)
        return youtubeListRepository.save(list)
    }

    fun refreshList(listId: UUID?): List<SyncResult> {
        val userId = CurrentUser.userId()
        if (listId == null) return refreshListsForUser(userId)
        val list = youtubeListRepository.findActiveByIdAndUserId(listId, userId) ?: return emptyList()
        return refreshLists(listOf(list))
    }

    fun refreshListsForUser(userId: UUID): List<SyncResult> =
        refreshLists(youtubeListRepository.findAllActiveByUserId(userId))

    private fun refreshLists(lists: List<YoutubeList>): List<SyncResult> {
        return lists.mapNotNull { list ->
            try {
                YoutubeUrlValidator.requireYoutubeUrl(list.url)
            } catch (e: IllegalArgumentException) {
                log.warn("Skipping refresh for list={}: stored url is not a valid YouTube URL", list.id)
                return@mapNotNull null
            }
            try {
                val metadata = ytDlpService.fetchPlaylistMetadata(list.url)
                videoSyncService.syncList(list, metadata)
            } catch (e: Exception) {
                log.warn("Refresh failed for list={}: {}", list.id, e.message)
                val currentList = youtubeListRepository.findById(list.id).orElse(list)
                currentList.failureCount++
                currentList.updatedAt = Instant.now()
                youtubeListRepository.save(currentList)
                SyncResult(list = currentList, newVideos = 0, removedVideos = 0, downloadSuccesses = 0, downloadFailures = 0)
            }
        }
    }

    private fun extractPlaylistId(url: String): String {
        val regex = Regex("[?&]list=([^&]+)")
        return regex.find(url)?.groupValues?.get(1) ?: url
    }
}
