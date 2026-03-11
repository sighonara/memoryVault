package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.bookmark.entity.Bookmark
import org.sightech.memoryvault.bookmark.service.BookmarkService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class BookmarkResolver(private val bookmarkService: BookmarkService) {

    @QueryMapping
    fun bookmarks(
        @Argument query: String?,
        @Argument tags: List<String>?
    ): List<Bookmark> {
        return bookmarkService.findAll(query, tags)
    }

    @MutationMapping
    fun addBookmark(
        @Argument url: String,
        @Argument title: String?,
        @Argument tags: List<String>?
    ): Bookmark {
        return bookmarkService.create(url, title, tags)
    }

    @MutationMapping
    fun tagBookmark(
        @Argument id: UUID,
        @Argument tags: List<String>
    ): Bookmark? {
        return bookmarkService.updateTags(id, tags)
    }

    @MutationMapping
    fun deleteBookmark(@Argument id: UUID): Bookmark? {
        return bookmarkService.softDelete(id)
    }

    @MutationMapping
    fun exportBookmarks(): String {
        return bookmarkService.exportNetscapeHtml()
    }
}
