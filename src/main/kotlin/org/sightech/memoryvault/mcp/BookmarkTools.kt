package org.sightech.memoryvault.mcp

import org.sightech.memoryvault.auth.CurrentUser
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import org.sightech.memoryvault.bookmark.service.BookmarkService
import java.util.UUID

@Component
class BookmarkTools(private val bookmarkService: BookmarkService) {

    private val log = LoggerFactory.getLogger(javaClass)

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

    @Tool(description = "Create a bookmark folder. Use when the user wants to organize bookmarks into folders.")
    fun createFolder(name: String, parentFolderName: String?): String {
        val parentId = if (parentFolderName != null) {
            bookmarkService.findAllFolders().find { it.name == parentFolderName }?.id
        } else null
        val folder = bookmarkService.createFolder(name, parentId)
        return "Created folder: ${folder.name}" + if (folder.parentId != null) " (inside $parentFolderName)" else ""
    }

    @Tool(description = "List all bookmark folders. Use when the user wants to see their folder structure.")
    fun listFolders(): String {
        val folders = bookmarkService.findAllFolders()
        if (folders.isEmpty()) return "No folders found."
        return folders.joinToString("\n") { "- ${it.name} (${it.id})" }
    }

    @Tool(description = "Move a bookmark to a folder. Use when the user wants to organize a bookmark into a specific folder.")
    fun moveBookmarkToFolder(bookmarkId: String, folderName: String?): String {
        val folderId = if (folderName != null) {
            bookmarkService.findAllFolders().find { it.name == folderName }?.id
                ?: return "Folder '$folderName' not found"
        } else null
        bookmarkService.moveBookmark(UUID.fromString(bookmarkId), folderId)
        return "Moved bookmark to ${folderName ?: "Unfiled"}"
    }
}
