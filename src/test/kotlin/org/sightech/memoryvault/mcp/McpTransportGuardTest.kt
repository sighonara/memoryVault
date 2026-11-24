package org.sightech.memoryvault.mcp

import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.service.JwtService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpMethod
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// MCP is intentionally stdio-only: no HTTP MCP endpoint may be served by the web app.
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class McpTransportGuardTest {

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
    lateinit var jwtService: JwtService

    @LocalServerPort
    var localPort: Int = 0

    private val endpoints = listOf(
        HttpMethod.GET to "/sse",
        HttpMethod.POST to "/mcp",
        HttpMethod.POST to "/mcp/message",
        HttpMethod.GET to "/mcp"
    )

    private fun client() = WebTestClient.bindToServer().baseUrl("http://localhost:$localPort").build()

    private fun token() = jwtService.generateToken(
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        "system@memoryvault.local",
        "OWNER"
    )

    private fun assertNotServed(authenticated: Boolean) {
        for ((method, path) in endpoints) {
            val spec = client().method(method).uri(path)
                .header("Accept", "text/event-stream, application/json")
                .header("Content-Type", "application/json")
            val request = if (authenticated) spec.header("Authorization", "Bearer ${token()}") else spec
            val result = request.bodyValue("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}").exchange().returnResult(String::class.java)
            val status = result.status.value()
            assertTrue(status in listOf(401, 403, 404), "$method $path (authenticated=$authenticated) returned $status")
            val contentType = result.responseHeaders.contentType?.toString() ?: ""
            assertFalse(contentType.contains("text/event-stream"), "$method $path returned an event stream")
        }
    }

    @Test
    fun `no MCP HTTP endpoints are served without auth`() = assertNotServed(authenticated = false)

    @Test
    fun `no MCP HTTP endpoints are served with an owner token`() = assertNotServed(authenticated = true)
}
