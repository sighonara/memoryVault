package org.sightech.memoryvault.graphql

import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.service.JwtService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.graphql.execution.ErrorType
import org.springframework.graphql.test.tester.HttpGraphQlTester
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminAuthorizationIntegrationTest {

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

    private fun testerFor(token: String): HttpGraphQlTester =
        HttpGraphQlTester.create(
            WebTestClient.bindToServer().baseUrl("http://localhost:$localPort/graphql").build()
        ).mutate().header("Authorization", "Bearer $token").build()

    private fun viewer() = testerFor(jwtService.generateToken(UUID.randomUUID(), "viewer@test.local", "VIEWER"))

    private fun owner() = testerFor(
        jwtService.generateToken(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "system@memoryvault.local",
            "OWNER"
        )
    )

    @Test
    fun `viewer is forbidden from logs`() {
        viewer().document("{ logs(limit: 5) { message } }").execute()
            .errors().satisfy { assert(it[0].errorType == ErrorType.FORBIDDEN) }
    }

    @Test
    fun `viewer is forbidden from costs`() {
        viewer().document("{ costs { current { id } } }").execute()
            .errors().satisfy { assert(it[0].errorType == ErrorType.FORBIDDEN) }
    }

    @Test
    fun `viewer is forbidden from refreshCosts`() {
        viewer().document("mutation { refreshCosts { id } }").execute()
            .errors().satisfy { assert(it[0].errorType == ErrorType.FORBIDDEN) }
    }

    @Test
    fun `owner can read logs`() {
        owner().document("{ logs(limit: 5) { message } }").execute()
            .errors().verify()
    }

    @Test
    fun `viewer stats hide global storage`() {
        val response = viewer().document("{ stats { bookmarkCount storageUsedBytes } }").execute()
        response.errors().verify()
        response.path("stats.bookmarkCount").hasValue()
        response.path("stats.storageUsedBytes").valueIsNull()
    }

    @Test
    fun `owner stats include global storage`() {
        val response = owner().document("{ stats { bookmarkCount storageUsedBytes } }").execute()
        response.errors().verify()
        response.path("stats.storageUsedBytes").hasValue()
    }

    @Test
    fun `viewer can still use own data`() {
        viewer().document("{ bookmarks { id } }").execute().errors().verify()
        viewer().document("{ jobs(limit: 5) { id } }").execute().errors().verify()
    }
}
