package org.sightech.memoryvault.bookmark

import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.bookmark.service.BookmarkService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class BookmarkIntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("memoryvault_test")
            withUsername("memoryvault")
            withPassword("memoryvault")
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var bookmarkService: BookmarkService

    private val userId = CurrentUser.SYSTEM_USER_ID

    @Test
    fun `create and retrieve bookmark`() {
        val bookmark = bookmarkService.create(userId, "https://example.com", "Example Site", null)
        assertNotNull(bookmark.id)
        assertEquals("Example Site", bookmark.title)

        val found = bookmarkService.findAll(userId, null, null)
        assert(found.any { it.id == bookmark.id })
    }

    @Test
    fun `create bookmark with tags`() {
        val bookmark = bookmarkService.create(userId, "https://kotlin.dev", "Kotlin", listOf("lang", "jvm"))
        assertEquals(2, bookmark.tags.size)

        val found = bookmarkService.findAll(userId, null, listOf("lang"))
        assert(found.any { it.id == bookmark.id })
    }

    @Test
    fun `update tags on bookmark`() {
        val bookmark = bookmarkService.create(userId, "https://spring.io", "Spring", listOf("java"))
        val updated = bookmarkService.updateTags(userId, bookmark.id, listOf("kotlin", "framework"))

        assertNotNull(updated)
        assertEquals(2, updated.tags.size)
        assert(updated.tags.any { it.name == "kotlin" })
        assert(updated.tags.none { it.name == "java" })
    }

    @Test
    fun `soft delete bookmark`() {
        val bookmark = bookmarkService.create(userId, "https://delete-me.com", "Delete Me", null)
        val deleted = bookmarkService.softDelete(bookmark.id)

        assertNotNull(deleted)
        assertNotNull(deleted.deletedAt)

        val found = bookmarkService.findAll(userId, null, null)
        assert(found.none { it.id == bookmark.id })
    }

    @Test
    fun `export bookmarks as Netscape HTML`() {
        bookmarkService.create(userId, "https://export-test.com", "Export Test", listOf("test"))
        val html = bookmarkService.exportNetscapeHtml(userId)

        assert(html.contains("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
        assert(html.contains("https://export-test.com"))
        assert(html.contains("Export Test"))
        assert(html.contains("TAGS=\"test\""))
    }

    @Test
    fun `tags are reused across bookmarks`() {
        bookmarkService.create(userId, "https://a.com", "A", listOf("shared-tag"))
        bookmarkService.create(userId, "https://b.com", "B", listOf("shared-tag"))

        val results = bookmarkService.findAll(userId, null, listOf("shared-tag"))
        assert(results.size >= 2)
    }
}
