package org.sightech.memoryvault.mcp

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import org.sightech.memoryvault.bookmark.service.BookmarkService
import java.util.UUID

@Component
class BookmarkTools(private val bookmarkService: BookmarkService) {

    @Tool(description = "Save a URL as a bookmark. Use when the user wants to save, bookmark, or remember a web page. Optionally provide a title and tags.")
    fun addBookmark(url: String, title: String?, tags: List<String>?): String {
        val bookmark = bookmarkService.create(url, title, tags)
        val tagStr = if (bookmark.tags.isNotEmpty()) " [${bookmark.tags.joinToString(", ") { it.name }}]" else ""
        return "Saved bookmark: \"${bookmark.title}\" (${bookmark.url})$tagStr — id: ${bookmark.id}"
    }

    @Tool(description = "List and search bookmarks. Use when the user wants to see their bookmarks, search by text, or filter by tags. Both query and tags are optional filters.")
    fun listBookmarks(query: String?, tags: List<String>?): String {
        val bookmarks = bookmarkService.findAll(query, tags)
        if (bookmarks.isEmpty()) return "No bookmarks found."

        val lines = bookmarks.map { b ->
            val tagStr = if (b.tags.isNotEmpty()) " [${b.tags.joinToString(", ") { it.name }}]" else ""
            "- ${b.title} (${b.url})$tagStr — id: ${b.id}"
        }
        return "${bookmarks.size} bookmark(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Update the tags on a bookmark. Use when the user wants to tag, retag, categorize, or label a bookmark. Replaces all existing tags.")
    fun tagBookmark(bookmarkId: String, tags: List<String>): String {
        val bookmark = bookmarkService.updateTags(UUID.fromString(bookmarkId), tags)
            ?: return "Bookmark not found."
        val tagStr = bookmark.tags.joinToString(", ") { it.name }
        return "Updated tags on \"${bookmark.title}\": [$tagStr]"
    }

    @Tool(description = "Delete a bookmark. Use when the user wants to remove or delete a saved bookmark. This is a soft delete — the bookmark can be recovered.")
    fun deleteBookmark(bookmarkId: String): String {
        val bookmark = bookmarkService.softDelete(UUID.fromString(bookmarkId))
            ?: return "Bookmark not found."
        return "Deleted bookmark: \"${bookmark.title}\" (${bookmark.url})"
    }

    @Tool(description = "Export all bookmarks as a Netscape HTML file. Use when the user wants to export their bookmarks for import into a web browser.")
    fun exportBookmarks(format: String?): String {
        return bookmarkService.exportNetscapeHtml()
    }
}
