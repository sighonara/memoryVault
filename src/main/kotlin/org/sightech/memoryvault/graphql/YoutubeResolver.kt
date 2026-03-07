package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.youtube.entity.Video
import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.service.SyncResult
import org.sightech.memoryvault.youtube.service.VideoService
import org.sightech.memoryvault.youtube.service.YoutubeListService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class YoutubeResolver(
    private val youtubeListService: YoutubeListService,
    private val videoService: VideoService
) {

    @QueryMapping
    fun youtubeLists(): List<Map<String, Any?>> {
        val userId = CurrentUser.userId()
        return youtubeListService.listLists().map { (list, stats) ->
            mapOf(
                "list" to list,
                "totalVideos" to stats.totalVideos,
                "downloadedVideos" to stats.downloadedVideos,
                "removedVideos" to stats.removedVideos
            )
        }
    }

    @QueryMapping
    fun videos(
        @Argument listId: UUID?,
        @Argument query: String?,
        @Argument removedOnly: Boolean?
    ): List<Video> {
        return videoService.getVideos(listId, query, removedOnly ?: false)
    }

    @QueryMapping
    fun videoStatus(@Argument videoId: UUID): Video? {
        return videoService.getVideoStatus(videoId)
    }

    @MutationMapping
    fun addYoutubeList(@Argument url: String): Map<String, Any?> {
        val (list, syncResult) = youtubeListService.addList(url)
        return mapOf(
            "list" to list,
            "newVideos" to syncResult.newVideos
        )
    }

    @MutationMapping
    fun deleteYoutubeList(@Argument listId: UUID): YoutubeList? {
        return youtubeListService.deleteList(listId)
    }

    @MutationMapping
    fun refreshYoutubeList(@Argument listId: UUID?): List<SyncResult> {
        return youtubeListService.refreshList(listId)
    }
}
