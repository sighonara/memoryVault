package org.sightech.memoryvault.graphql

import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.service.JwtService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.graphql.test.tester.HttpGraphQlTester
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GraphQlIntegrationTest {

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

    private val graphQlTester: HttpGraphQlTester by lazy {
        HttpGraphQlTester.create(
            org.springframework.test.web.reactive.server.WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:${localPort}/graphql")
                .build()
        )
    }

    @org.springframework.boot.test.web.server.LocalServerPort
    var localPort: Int = 0

    @Test
    fun `bookmarks query returns empty list`() {
        graphQlTester
            .mutate()
            .header("Authorization", "Bearer ${generateTestToken()}")
            .build()
            .document("{ bookmarks { id url title } }")
            .execute()
            .path("bookmarks")
            .entityList(Map::class.java)
    }

    @Test
    fun `stats query returns system stats`() {
        graphQlTester
            .mutate()
            .header("Authorization", "Bearer ${generateTestToken()}")
            .build()
            .document("{ stats { bookmarkCount feedCount tagCount } }")
            .execute()
            .path("stats.bookmarkCount")
            .entity(Int::class.java)
    }

    private fun generateTestToken(): String {
        return jwtService.generateToken(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "system@memoryvault.local",
            "OWNER"
        )
    }
}
