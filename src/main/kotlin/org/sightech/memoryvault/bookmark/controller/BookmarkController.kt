package org.sightech.memoryvault.bookmark.controller

import org.sightech.memoryvault.bookmark.entity.Bookmark
import org.sightech.memoryvault.bookmark.service.BookmarkService
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/bookmarks")
class BookmarkController(private val service: BookmarkService) {

    @GetMapping
    fun findAll(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) tags: List<String>?
    ): List<Bookmark> = service.findAll(query, tags)
}
