package org.sightech.memoryvault.youtube.service

import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.sightech.memoryvault.youtube.repository.YoutubeListRepository
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

    companion object {
        val SYSTEM_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    fun addList(url: String): Pair<YoutubeList, SyncResult> {
        val playlistId = extractPlaylistId(url)
        val list = youtubeListRepository.save(
            YoutubeList(userId = SYSTEM_USER_ID, youtubeListId = playlistId, url = url)
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
        return list to syncResult
    }

    fun listLists(): List<Pair<YoutubeList, ListStats>> {
        val lists = youtubeListRepository.findAllActiveByUserId(SYSTEM_USER_ID)
        return lists.map { list ->
            val stats = ListStats(
                totalVideos = videoRepository.countByYoutubeListId(list.id),
                downloadedVideos = videoRepository.countDownloadedByYoutubeListId(list.id),
                removedVideos = videoRepository.countRemovedByYoutubeListId(list.id)
            )
            list to stats
        }
    }

    fun deleteList(listId: UUID): YoutubeList? {
        val list = youtubeListRepository.findActiveById(listId) ?: return null
        list.deletedAt = Instant.now()
        list.updatedAt = Instant.now()
        return youtubeListRepository.save(list)
    }

    fun refreshList(listId: UUID?): List<SyncResult> {
        val lists = if (listId != null) {
            val list = youtubeListRepository.findActiveById(listId) ?: return emptyList()
            listOf(list)
        } else {
            youtubeListRepository.findAllActiveByUserId(SYSTEM_USER_ID)
        }

        return lists.map { list ->
            try {
                val metadata = ytDlpService.fetchPlaylistMetadata(list.url)
                videoSyncService.syncList(list, metadata)
            } catch (e: Exception) {
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
