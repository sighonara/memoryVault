package org.sightech.memoryvault.youtube.controller

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.youtube.service.YoutubeListService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/youtube")
class YoutubeController(private val youtubeListService: YoutubeListService) {

    @GetMapping("/lists")
    fun listLists() = youtubeListService.listLists().map { (list, stats) ->
        mapOf(
            "id" to list.id,
            "name" to list.name,
            "url" to list.url,
            "totalVideos" to stats.totalVideos,
            "downloadedVideos" to stats.downloadedVideos,
            "removedVideos" to stats.removedVideos
        )
    }
}
