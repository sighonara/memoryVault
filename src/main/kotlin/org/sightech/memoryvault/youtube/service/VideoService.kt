package org.sightech.memoryvault.youtube.service

import org.sightech.memoryvault.youtube.entity.Video
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class VideoService(private val videoRepository: VideoRepository) {

    fun getVideos(listId: UUID?, query: String?, removedOnly: Boolean): List<Video> {
        if (listId == null && query == null && !removedOnly) return emptyList()

        val videos = if (listId != null) {
            if (removedOnly) {
                videoRepository.findRemovedByYoutubeListId(listId)
            } else {
                videoRepository.findByYoutubeListId(listId)
            }
        } else {
            emptyList()
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
        return videoRepository.findById(videoId).orElse(null)
    }
}
