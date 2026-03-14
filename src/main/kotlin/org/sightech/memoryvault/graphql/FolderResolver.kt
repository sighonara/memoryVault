package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.bookmark.entity.Bookmark
import org.sightech.memoryvault.bookmark.entity.Folder
import org.sightech.memoryvault.bookmark.repository.BookmarkRepository
import org.sightech.memoryvault.bookmark.service.BookmarkService
import org.springframework.graphql.data.method.annotation.*
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class FolderResolver(
    private val bookmarkService: BookmarkService,
    private val bookmarkRepository: BookmarkRepository
) {
    @QueryMapping
    fun folders(): List<Folder> = bookmarkService.findAllFolders()

    @QueryMapping
    fun folder(@Argument id: UUID): Folder? = bookmarkService.findFolder(id)

    @MutationMapping
    fun createFolder(@Argument name: String, @Argument parentId: UUID?): Folder =
        bookmarkService.createFolder(name, parentId)

    @MutationMapping
    fun renameFolder(@Argument id: UUID, @Argument name: String): Folder =
        bookmarkService.renameFolder(id, name)

    @MutationMapping
    fun moveFolder(@Argument id: UUID, @Argument newParentId: UUID?): Folder =
        bookmarkService.moveFolder(id, newParentId)

    @MutationMapping
    fun deleteFolder(@Argument id: UUID): Boolean {
        bookmarkService.deleteFolder(id)
        return true
    }

    @BatchMapping(typeName = "Folder", field = "children")
    fun children(folders: List<Folder>): Map<Folder, List<Folder>> {
        val allFolders = bookmarkService.findAllFolders()
        val childrenByParent = allFolders.groupBy { it.parentId }
        return folders.associateWith { folder -> childrenByParent[folder.id] ?: emptyList() }
    }

    @BatchMapping(typeName = "Folder", field = "bookmarks")
    fun bookmarks(folders: List<Folder>): Map<Folder, List<Bookmark>> {
        val userId = CurrentUser.userId()
        val allBookmarks = bookmarkRepository.findAllActiveByUserId(userId)
        val bookmarksByFolder = allBookmarks.groupBy { it.folderId }
        return folders.associateWith { folder -> bookmarksByFolder[folder.id] ?: emptyList() }
    }

    @BatchMapping(typeName = "Folder", field = "bookmarkCount")
    fun bookmarkCount(folders: List<Folder>): Map<Folder, Int> {
        val userId = CurrentUser.userId()
        val allBookmarks = bookmarkRepository.findAllActiveByUserId(userId)
        val countByFolder = allBookmarks.groupBy { it.folderId }.mapValues { it.value.size }
        return folders.associateWith { folder -> countByFolder[folder.id] ?: 0 }
    }
}
