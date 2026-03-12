package org.sightech.memoryvault.bookmark.controller

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.sightech.memoryvault.bookmark.entity.*
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
}
