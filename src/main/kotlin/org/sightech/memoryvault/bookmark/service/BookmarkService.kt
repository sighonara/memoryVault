package org.sightech.memoryvault.bookmark.service

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.bookmark.entity.Bookmark
import org.sightech.memoryvault.bookmark.entity.Folder
import org.sightech.memoryvault.bookmark.repository.BookmarkRepository
import org.sightech.memoryvault.bookmark.repository.FolderRepository
import org.sightech.memoryvault.tag.service.TagService
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class BookmarkService(
    private val bookmarkRepository: BookmarkRepository,
    private val folderRepository: FolderRepository,
    private val tagService: TagService
) {

    fun create(url: String, title: String?, tagNames: List<String>?): Bookmark {
        val userId = CurrentUser.userId()
        val bookmark = Bookmark(
            userId = userId,
            url = url,
            title = title ?: url
        )
        if (!tagNames.isNullOrEmpty()) {
            val tags = tagService.findOrCreateByNames(tagNames)
            bookmark.tags.addAll(tags)
        }
        return bookmarkRepository.save(bookmark)
    }

    fun findAll(query: String?, tagNames: List<String>?): List<Bookmark> {
        val userId = CurrentUser.userId()
        var bookmarks = bookmarkRepository.findAllActiveByUserId(userId)

        if (!query.isNullOrBlank()) {
            val q = query.lowercase()
            bookmarks = bookmarks.filter {
                it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
            }
        }

        if (!tagNames.isNullOrEmpty()) {
            bookmarks = bookmarks.filter { bookmark ->
                val bookmarkTagNames = bookmark.tags.map { it.name }.toSet()
                tagNames.any { it in bookmarkTagNames }
            }
        }

        return bookmarks
    }

    fun updateTags(bookmarkId: UUID, tagNames: List<String>): Bookmark? {
        val userId = CurrentUser.userId()
        val bookmark = bookmarkRepository.findActiveByIdAndUserId(bookmarkId, userId) ?: return null
        val tags = tagService.findOrCreateByNames(tagNames)
        bookmark.tags.clear()
        bookmark.tags.addAll(tags)
        bookmark.updatedAt = Instant.now()
        return bookmarkRepository.save(bookmark)
    }

    fun softDelete(bookmarkId: UUID): Bookmark? {
        val userId = CurrentUser.userId()
        val bookmark = bookmarkRepository.findActiveByIdAndUserId(bookmarkId, userId) ?: return null
        bookmark.deletedAt = Instant.now()
        bookmark.updatedAt = Instant.now()
        return bookmarkRepository.save(bookmark)
    }

    fun exportNetscapeHtml(): String {
        val userId = CurrentUser.userId()
        val bookmarks = bookmarkRepository.findAllActiveByUserId(userId)
        val sb = StringBuilder()
        sb.appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
        sb.appendLine("<!-- This is an automatically generated file. -->")
        sb.appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
        sb.appendLine("<TITLE>Bookmarks</TITLE>")
        sb.appendLine("<H1>Bookmarks</H1>")
        sb.appendLine("<DL><p>")
        for (bookmark in bookmarks) {
            val addDate = bookmark.createdAt.epochSecond
            val tagAttr = if (bookmark.tags.isNotEmpty()) {
                " TAGS=\"${bookmark.tags.joinToString(",") { it.name }}\""
            } else ""
            sb.appendLine("    <DT><A HREF=\"${bookmark.url}\" ADD_DATE=\"$addDate\"$tagAttr>${bookmark.title}</A>")
        }
        sb.appendLine("</DL><p>")
        return sb.toString()
    }

    fun createFolder(name: String, parentId: UUID?): Folder {
        val userId = CurrentUser.userId()
        if (parentId != null) {
            folderRepository.findActiveByIdAndUserId(parentId, userId)
                ?: throw IllegalArgumentException("Parent folder not found")
        }
        val folder = Folder(name = name, userId = userId, parentId = parentId)
        return folderRepository.save(folder)
    }

    fun renameFolder(id: UUID, name: String): Folder {
        val userId = CurrentUser.userId()
        val folder = folderRepository.findActiveByIdAndUserId(id, userId)
            ?: throw IllegalArgumentException("Folder not found")
        folder.name = name
        folder.updatedAt = Instant.now()
        return folderRepository.save(folder)
    }

    fun moveFolder(id: UUID, newParentId: UUID?): Folder {
        val userId = CurrentUser.userId()
        val folder = folderRepository.findActiveByIdAndUserId(id, userId)
            ?: throw IllegalArgumentException("Folder not found")

        if (newParentId != null) {
            // Cycle detection: walk ancestors of newParentId to ensure id isn't among them
            if (newParentId == id) {
                throw IllegalArgumentException("Cannot move a folder into its own descendant")
            }
            var currentId: UUID? = newParentId
            while (currentId != null) {
                val ancestor = folderRepository.findActiveByIdAndUserId(currentId, userId) ?: break
                if (ancestor.id == id) {
                    throw IllegalArgumentException("Cannot move a folder into its own descendant")
                }
                currentId = ancestor.parentId
            }
        }

        folder.parentId = newParentId
        folder.updatedAt = Instant.now()
        return folderRepository.save(folder)
    }

    fun deleteFolder(id: UUID) {
        val userId = CurrentUser.userId()
        val folder = folderRepository.findActiveByIdAndUserId(id, userId)
            ?: throw IllegalArgumentException("Folder not found")

        // Reparent children to deleted folder's parent
        val children = folderRepository.findChildrenByParentId(id, userId)
        children.forEach { child ->
            child.parentId = folder.parentId
            child.updatedAt = Instant.now()
            folderRepository.save(child)
        }

        folder.deletedAt = Instant.now()
        folder.updatedAt = Instant.now()
        folderRepository.save(folder)
    }

    fun findAllFolders(): List<Folder> {
        return folderRepository.findAllActiveByUserId(CurrentUser.userId())
    }

    fun findFolder(id: UUID): Folder? {
        return folderRepository.findActiveByIdAndUserId(id, CurrentUser.userId())
    }
}
