package org.sightech.memoryvault.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["server.forward-headers-strategy=framework"]
)
@ActiveProfiles("test")
class SecurityHeadersIntegrationTest {

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

        private const val EXPECTED_CSP =
            "default-src 'self'; script-src 'self'; " +
                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                "img-src 'self' data: https:; " +
                "font-src 'self' data: https://fonts.gstatic.com; " +
                "connect-src 'self' https://cognito-idp.us-east-1.amazonaws.com; " +
                "frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'"
    }

    @LocalServerPort
    var port: Int = 0

    private fun health(extra: Map<String, String> = emptyMap()): HttpHeaders {
        val client = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
        return client.get().uri("/actuator/health")
            .headers { h -> extra.forEach { (k, v) -> h.set(k, v) } }
            .exchange()
            .returnResult(String::class.java)
            .responseHeaders
    }

    @Test
    fun `responses carry the baseline security headers exactly once`() {
        val headers = health()

        assertEquals(listOf("nosniff"), headers["X-Content-Type-Options"])
        assertEquals(listOf("DENY"), headers["X-Frame-Options"])
        assertEquals(listOf("strict-origin-when-cross-origin"), headers["Referrer-Policy"])
        assertEquals(listOf(EXPECTED_CSP), headers["Content-Security-Policy-Report-Only"])
        assertFalse(headers.containsHeader("Content-Security-Policy"), "CSP must be report-only for now")
    }

    @Test
    fun `HSTS is sent for requests forwarded as https`() {
        val headers = health(mapOf("X-Forwarded-Proto" to "https"))

        assertEquals(listOf("max-age=31536000"), headers["Strict-Transport-Security"])
    }

    @Test
    fun `HSTS is not sent for plain http requests`() {
        assertFalse(health().containsHeader("Strict-Transport-Security"))
    }
}
