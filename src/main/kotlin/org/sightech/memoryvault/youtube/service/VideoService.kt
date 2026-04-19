package org.sightech.memoryvault.youtube.service

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.youtube.entity.Video
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class VideoService(private val videoRepository: VideoRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun getVideos(listId: UUID?, query: String?, removedOnly: Boolean): List<Video> {
        val userId = CurrentUser.userId()
        if (listId == null && query == null && !removedOnly) return emptyList()

        val videos = if (listId != null) {
            if (removedOnly) {
                videoRepository.findRemovedByYoutubeListIdAndUserId(listId, userId)
            } else {
                videoRepository.findByYoutubeListIdAndUserId(listId, userId)
            }
        } else {
            // TODO: Global search filtering by userId
            if (query != null) {
                // We'd need a repository method like findByTitleContainingAndUserId
                // For now, let's just return empty or implement that filter if needed.
                emptyList()
            } else {
                emptyList()
            }
        }

        // TODO: Replace in-memory filtering with a JPQL LIKE query or full-text search
        //  when video counts grow large enough to matter.
        return if (query != null) {
            val q = query.lowercase()
            videos.filter { v ->
                (v.title?.lowercase()?.contains(q) == true) ||
                    (v.channelName?.lowercase()?.contains(q) == true)
            }
        } else {
            videos
        }
    }

    fun getVideoStatus(videoId: UUID): Video? {
        val userId = CurrentUser.userId()
        return videoRepository.findByIdAndUserId(videoId, userId)
    }
}
