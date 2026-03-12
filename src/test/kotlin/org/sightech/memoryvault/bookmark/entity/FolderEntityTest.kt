package org.sightech.memoryvault.bookmark.entity

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class FolderEntityTest {

    @Test
    fun `folder can be created with required fields`() {
        val userId = UUID.randomUUID()
        val folder = Folder(name = "Tech", userId = userId)

        assertEquals("Tech", folder.name)
        assertEquals(userId, folder.userId)
        assertNull(folder.parentId)
        assertEquals(0, folder.sortOrder)
        assertNull(folder.deletedAt)
    }

    @Test
    fun `folder can have a parent`() {
        val userId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val folder = Folder(name = "Frontend", userId = userId, parentId = parentId)

        assertEquals(parentId, folder.parentId)
    }
}
