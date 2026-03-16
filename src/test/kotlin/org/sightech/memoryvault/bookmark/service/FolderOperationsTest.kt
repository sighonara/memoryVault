package org.sightech.memoryvault.bookmark.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.sightech.memoryvault.bookmark.entity.Folder
import org.sightech.memoryvault.bookmark.repository.BookmarkRepository
import org.sightech.memoryvault.bookmark.repository.FolderRepository
import org.sightech.memoryvault.tag.service.TagService
import org.sightech.memoryvault.auth.CurrentUser
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.util.UUID

class FolderOperationsTest {

    private val bookmarkRepository = mockk<BookmarkRepository>(relaxed = true)
    private val folderRepository = mockk<FolderRepository>(relaxed = true)
    private val tagService = mockk<TagService>(relaxed = true)
    private lateinit var service: BookmarkService
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val auth = UsernamePasswordAuthenticationToken(userId.toString(), null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
        service = BookmarkService(bookmarkRepository, folderRepository, tagService)
    }

    @Test
    fun `createFolder creates a root folder`() {
        val folder = Folder(name = "Tech", userId = userId)
        every { folderRepository.save(any()) } returns folder

        val result = service.createFolder("Tech", null)

        assertEquals("Tech", result.name)
        assertNull(result.parentId)
        verify { folderRepository.save(match { it.name == "Tech" && it.parentId == null }) }
    }

    @Test
    fun `createFolder creates a child folder`() {
        val parentId = UUID.randomUUID()
        val parent = Folder(id = parentId, name = "Dev", userId = userId)
        every { folderRepository.findActiveByIdAndUserId(parentId, userId) } returns parent
        every { folderRepository.save(any()) } answers { firstArg() }

        val result = service.createFolder("Frontend", parentId)

        assertEquals("Frontend", result.name)
        assertEquals(parentId, result.parentId)
    }

    @Test
    fun `createFolder throws when parent not found`() {
        val parentId = UUID.randomUUID()
        every { folderRepository.findActiveByIdAndUserId(parentId, userId) } returns null

        assertThrows<IllegalArgumentException> {
            service.createFolder("Orphan", parentId)
        }
    }

    @Test
    fun `renameFolder updates name`() {
        val folderId = UUID.randomUUID()
        val folder = Folder(id = folderId, name = "Old", userId = userId)
        every { folderRepository.findActiveByIdAndUserId(folderId, userId) } returns folder
        every { folderRepository.save(any()) } answers { firstArg() }

        val result = service.renameFolder(folderId, "New")

        assertEquals("New", result.name)
    }

    @Test
    fun `moveFolder updates parentId`() {
        val folderId = UUID.randomUUID()
        val newParentId = UUID.randomUUID()
        val folder = Folder(id = folderId, name = "Mobile", userId = userId)
        val newParent = Folder(id = newParentId, name = "Dev", userId = userId)

        every { folderRepository.findActiveByIdAndUserId(folderId, userId) } returns folder
        every { folderRepository.findActiveByIdAndUserId(newParentId, userId) } returns newParent
        every { folderRepository.save(any()) } answers { firstArg() }

        val result = service.moveFolder(folderId, newParentId)

        assertEquals(newParentId, result.parentId)
    }

    @Test
    fun `moveFolder detects cycle — folder cannot be its own parent`() {
        val folderId = UUID.randomUUID()
        val folder = Folder(id = folderId, name = "Self", userId = userId)

        every { folderRepository.findActiveByIdAndUserId(folderId, userId) } returns folder

        val ex = assertThrows<IllegalArgumentException> {
            service.moveFolder(folderId, folderId)
        }
        assertTrue(ex.message!!.contains("descendant"))
    }

    @Test
    fun `moveFolder detects cycle — folder cannot move into its own descendant`() {
        val grandparentId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()

        val grandparent = Folder(id = grandparentId, name = "GP", userId = userId)
        val parent = Folder(id = parentId, name = "P", userId = userId, parentId = grandparentId)
        val child = Folder(id = childId, name = "C", userId = userId, parentId = parentId)

        every { folderRepository.findActiveByIdAndUserId(grandparentId, userId) } returns grandparent
        every { folderRepository.findActiveByIdAndUserId(childId, userId) } returns child
        // Walk ancestors of child: child -> parent -> grandparent
        every { folderRepository.findActiveByIdAndUserId(parentId, userId) } returns parent

        val ex = assertThrows<IllegalArgumentException> {
            service.moveFolder(grandparentId, childId)
        }
        assertTrue(ex.message!!.contains("descendant"))
    }

    @Test
    fun `deleteFolder soft-deletes and reparents children`() {
        val folderId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val folder = Folder(id = folderId, name = "ToDelete", userId = userId, parentId = parentId)
        val child = Folder(id = UUID.randomUUID(), name = "Child", userId = userId, parentId = folderId)

        every { folderRepository.findActiveByIdAndUserId(folderId, userId) } returns folder
        every { folderRepository.findChildrenByParentId(folderId, userId) } returns listOf(child)
        every { folderRepository.save(any()) } answers { firstArg() }

        service.deleteFolder(folderId)

        verify { folderRepository.save(match { it.id == child.id && it.parentId == parentId }) }
        verify { folderRepository.save(match { it.id == folderId && it.deletedAt != null }) }
    }
}
