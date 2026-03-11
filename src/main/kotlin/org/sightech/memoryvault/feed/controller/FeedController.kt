package org.sightech.memoryvault.feed.controller

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.feed.service.FeedService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/feeds")
class FeedController(private val service: FeedService) {

    @GetMapping
    fun listFeeds() = service.listFeeds().map { (feed, unread) ->
        mapOf("id" to feed.id, "url" to feed.url, "title" to feed.title, "unreadCount" to unread)
    }
}
