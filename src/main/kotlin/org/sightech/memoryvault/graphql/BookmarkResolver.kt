package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.bookmark.entity.*
import org.sightech.memoryvault.bookmark.service.BookmarkService
import org.sightech.memoryvault.bookmark.service.IngestService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.util.UUID

data class IngestInput(val bookmarks: List<IngestBookmarkInputGql>)
data class IngestBookmarkInputGql(val url: String, val title: String, val browserFolder: String?)
data class IngestResolutionInput(val url: String, val action: IngestAction)

@Controller
class BookmarkResolver(
    private val bookmarkService: BookmarkService,
    private val ingestService: IngestService
) {

    @QueryMapping
    fun bookmarks(
        @Argument query: String?,
        @Argument tags: List<String>?
    ): List<Bookmark> {
        return bookmarkService.findAll(query, tags)
    }

    @QueryMapping
    fun pendingIngests(): List<IngestPreviewResult> {
        return ingestService.getPendingPreviews()
    }

    @QueryMapping
    fun exportBookmarks(): String {
        return bookmarkService.exportNetscapeHtml()
    }

    @MutationMapping
    fun addBookmark(
        @Argument url: String,
        @Argument title: String?,
        @Argument tags: List<String>?,
        @Argument folderId: UUID?
    ): Bookmark {
        return bookmarkService.create(url, title, tags, folderId)
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
    fun moveBookmark(@Argument id: UUID, @Argument folderId: UUID?): Bookmark =
        bookmarkService.moveBookmark(id, folderId)

    @MutationMapping
    fun reorderBookmarks(@Argument folderId: UUID?, @Argument bookmarkIds: List<UUID>): List<Bookmark> =
        bookmarkService.reorderBookmarks(folderId, bookmarkIds)

    @MutationMapping
    fun ingestBookmarks(@Argument input: IngestInput): IngestPreviewResult {
        return ingestService.generatePreview(input.bookmarks.map {
            IngestBookmarkInput(url = it.url, title = it.title, browserFolder = it.browserFolder)
        })
    }

    @MutationMapping
    fun commitIngest(@Argument previewId: UUID, @Argument resolutions: List<IngestResolutionInput>): CommitResult {
        return ingestService.commitResolutions(previewId, resolutions.map {
            IngestResolution(url = it.url, action = it.action)
        })
    }
}
