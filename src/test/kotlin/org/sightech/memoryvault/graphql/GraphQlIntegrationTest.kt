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

    @Autowired
    lateinit var feedRepository: org.sightech.memoryvault.feed.repository.FeedRepository

    @Autowired
    lateinit var feedItemRepository: org.sightech.memoryvault.feed.repository.FeedItemRepository

    @Autowired
    lateinit var feedCategoryRepository: org.sightech.memoryvault.feed.repository.FeedCategoryRepository

    @Test
    fun `feedItemsAll resolves feedId from the item's feed`() {
        val seedUserId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val category = feedCategoryRepository.save(
            org.sightech.memoryvault.feed.entity.FeedCategory(userId = seedUserId, name = "feedid-test")
        )
        val feed = feedRepository.save(
            org.sightech.memoryvault.feed.entity.Feed(
                userId = seedUserId,
                url = "https://example.com/feedid-test.xml",
                category = category
            )
        )
        feedItemRepository.save(
            org.sightech.memoryvault.feed.entity.FeedItem(feed = feed, guid = "feedid-test-guid", title = "Item")
        )

        val feedIds = graphQlTester
            .mutate()
            .header("Authorization", "Bearer ${generateTestToken()}")
            .build()
            .document("{ feedItemsAll(limit: 50) { id feedId } }")
            .execute()
            .errors().verify()
            .path("feedItemsAll[*].feedId")
            .entityList(String::class.java)
            .get()

        org.junit.jupiter.api.Assertions.assertTrue(feedIds.isNotEmpty(), "expected at least one feed item")
        org.junit.jupiter.api.Assertions.assertTrue(feedIds.contains(feed.id.toString()), "feedId should be ${feed.id}, got $feedIds")

        val categoryIds = graphQlTester
            .mutate()
            .header("Authorization", "Bearer ${generateTestToken()}")
            .build()
            .document("{ feeds { feed { id categoryId } } }")
            .execute()
            .errors().verify()
            .path("feeds[?(@.feed.id == '${feed.id}')].feed.categoryId")
            .entityList(String::class.java)
            .get()

        org.junit.jupiter.api.Assertions.assertEquals(listOf(category.id.toString()), categoryIds)
    }

    private fun generateTestToken(): String {
        return jwtService.generateToken(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "system@memoryvault.local",
            "OWNER"
        )
    }
}
