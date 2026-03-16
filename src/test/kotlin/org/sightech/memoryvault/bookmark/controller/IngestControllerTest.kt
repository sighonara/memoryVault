package org.sightech.memoryvault.bookmark.controller

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.sightech.memoryvault.bookmark.entity.*
import org.sightech.memoryvault.bookmark.service.BookmarkService
import org.sightech.memoryvault.bookmark.service.IngestService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class IngestControllerTest {

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
    private lateinit var controller: IngestController

    @Autowired
    private lateinit var bookmarkService: BookmarkService

    @Test
    fun `POST ingest returns preview with previewId`() {
        val request = IngestController.IngestRequest(
            bookmarks = listOf(IngestBookmarkInput("https://controller-test.com", "Controller Test", "Tech"))
        )
        val response = controller.ingest(request)

        assertEquals(HttpStatus.OK, response.statusCode)
        val preview = response.body!!
        assertNotNull(preview.previewId)
        assertEquals(1, preview.summary.newCount)
        assertEquals(IngestStatus.NEW, preview.items.first().status)
    }

    @Test
    fun `GET ingest preview returns 404 for unknown previewId`() {
        val response = controller.getPreview(UUID.randomUUID())

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `POST ingest and GET preview round-trip`() {
        val request = IngestController.IngestRequest(
            bookmarks = listOf(IngestBookmarkInput("https://roundtrip-test.com", "Roundtrip", null))
        )
        val ingestResponse = controller.ingest(request)
        val previewId = ingestResponse.body!!.previewId

        val getResponse = controller.getPreview(previewId)
        assertEquals(HttpStatus.OK, getResponse.statusCode)
        assertEquals(1, getResponse.body!!.items.size)
        assertEquals("https://roundtrip-test.com", getResponse.body!!.items.first().url)
    }

    @Test
    fun `commit ingest creates bookmarks`() {
        val request = IngestController.IngestRequest(
            bookmarks = listOf(IngestBookmarkInput("https://commit-test-${UUID.randomUUID()}.com", "Commit Test", null))
        )
        val ingestResponse = controller.ingest(request)
        val previewId = ingestResponse.body!!.previewId

        val commitRequest = IngestController.CommitRequest(
            resolutions = listOf(IngestResolution(url = ingestResponse.body!!.items.first().url, action = IngestAction.ACCEPT))
        )
        val commitResponse = controller.commit(previewId, commitRequest)

        assertEquals(HttpStatus.OK, commitResponse.statusCode)
        assertEquals(1, commitResponse.body!!.accepted)
        assertEquals(0, commitResponse.body!!.skipped)
    }

    @Test
    fun `commit ingest with SKIP does not create bookmark`() {
        val url = "https://skip-test-${UUID.randomUUID()}.com"
        val request = IngestController.IngestRequest(
            bookmarks = listOf(IngestBookmarkInput(url, "Skip Test", null))
        )
        val ingestResponse = controller.ingest(request)
        val previewId = ingestResponse.body!!.previewId

        val commitRequest = IngestController.CommitRequest(
            resolutions = listOf(IngestResolution(url = url, action = IngestAction.SKIP))
        )
        val commitResponse = controller.commit(previewId, commitRequest)

        assertEquals(HttpStatus.OK, commitResponse.statusCode)
        assertEquals(0, commitResponse.body!!.accepted)
        assertEquals(1, commitResponse.body!!.skipped)
    }

    @Test
    fun `already committed preview returns 404`() {
        val request = IngestController.IngestRequest(
            bookmarks = listOf(IngestBookmarkInput("https://double-commit-${UUID.randomUUID()}.com", "Double Commit", null))
        )
        val ingestResponse = controller.ingest(request)
        val previewId = ingestResponse.body!!.previewId

        // First commit
        val commitRequest = IngestController.CommitRequest(
            resolutions = listOf(IngestResolution(url = ingestResponse.body!!.items.first().url, action = IngestAction.ACCEPT))
        )
        controller.commit(previewId, commitRequest)

        // Second attempt to get preview should return 404
        val getResponse = controller.getPreview(previewId)
        assertEquals(HttpStatus.NOT_FOUND, getResponse.statusCode)
    }
}
