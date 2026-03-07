package org.sightech.memoryvault.tag.service

import io.mockk.*
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.tag.entity.Tag
import org.sightech.memoryvault.tag.repository.TagRepository
import java.util.UUID
import kotlin.test.assertEquals

class TagServiceTest {

    private val repository = mockk<TagRepository>()
    private val service = TagService(repository)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `findOrCreateByName returns existing tag`() {
        val existing = Tag(userId = userId, name = "kotlin")
        every { repository.findByUserIdAndName(userId, "kotlin") } returns existing

        val result = service.findOrCreateByName(userId, "kotlin")

        assertEquals(existing, result)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `findOrCreateByName creates new tag when not found`() {
        every { repository.findByUserIdAndName(userId, "new-tag") } returns null
        every { repository.save(any()) } answers { firstArg() }

        val result = service.findOrCreateByName(userId, "new-tag")

        assertEquals("new-tag", result.name)
        assertEquals(userId, result.userId)
        verify { repository.save(any()) }
    }

    @Test
    fun `findOrCreateByNames returns mix of existing and new tags`() {
        val existing = Tag(userId = userId, name = "kotlin")
        every { repository.findByUserIdAndNameIn(userId, listOf("kotlin", "spring")) } returns listOf(existing)
        every { repository.save(any()) } answers { firstArg() }

        val result = service.findOrCreateByNames(userId, listOf("kotlin", "spring"))

        assertEquals(2, result.size)
        verify(exactly = 1) { repository.save(any()) }
    }
}
