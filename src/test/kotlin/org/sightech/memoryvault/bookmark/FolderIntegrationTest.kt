package org.sightech.memoryvault.bookmark

import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.service.JwtService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
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
class FolderIntegrationTest {

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

    private val graphQlTester: HttpGraphQlTester by lazy {
        HttpGraphQlTester.create(
            WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:${localPort}/graphql")
                .build()
        )
    }

    private fun authedTester() = graphQlTester
        .mutate()
        .header("Authorization", "Bearer ${generateTestToken()}")
        .build()

    private fun generateTestToken(): String = jwtService.generateToken(
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        "system@memoryvault.local",
        "OWNER"
    )

    @Test
    fun `create root folder`() {
        authedTester()
            .document("""mutation { createFolder(name: "IntegRoot") { id name parentId } }""")
            .execute()
            .path("createFolder.name").entity(String::class.java).isEqualTo("IntegRoot")
            .path("createFolder.parentId").valueIsNull()
    }

    @Test
    fun `create nested folder`() {
        val parentId = authedTester()
            .document("""mutation { createFolder(name: "Parent") { id } }""")
            .execute()
            .path("createFolder.id").entity(String::class.java).get()

        authedTester()
            .document("""mutation { createFolder(name: "Child", parentId: "$parentId") { id name parentId } }""")
            .execute()
            .path("createFolder.name").entity(String::class.java).isEqualTo("Child")
            .path("createFolder.parentId").entity(String::class.java).isEqualTo(parentId)
    }

    @Test
    fun `rename folder`() {
        val id = authedTester()
            .document("""mutation { createFolder(name: "OldName") { id } }""")
            .execute()
            .path("createFolder.id").entity(String::class.java).get()

        authedTester()
            .document("""mutation { renameFolder(id: "$id", name: "NewName") { id name } }""")
            .execute()
            .path("renameFolder.name").entity(String::class.java).isEqualTo("NewName")
    }

    @Test
    fun `move folder to new parent`() {
        val folderId = authedTester()
            .document("""mutation { createFolder(name: "Movable") { id } }""")
            .execute()
            .path("createFolder.id").entity(String::class.java).get()

        val newParentId = authedTester()
            .document("""mutation { createFolder(name: "NewParent") { id } }""")
            .execute()
            .path("createFolder.id").entity(String::class.java).get()

        authedTester()
            .document("""mutation { moveFolder(id: "$folderId", newParentId: "$newParentId") { id parentId } }""")
            .execute()
            .path("moveFolder.parentId").entity(String::class.java).isEqualTo(newParentId)
    }

    @Test
    fun `move folder cycle detection returns error`() {
        val parentId = authedTester()
            .document("""mutation { createFolder(name: "CycleA") { id } }""")
            .execute()
            .path("createFolder.id").entity(String::class.java).get()

        val childId = authedTester()
            .document("""mutation { createFolder(name: "CycleB", parentId: "$parentId") { id } }""")
            .execute()
            .path("createFolder.id").entity(String::class.java).get()

        authedTester()
            .document("""mutation { moveFolder(id: "$parentId", newParentId: "$childId") { id } }""")
            .execute()
            .errors()
            .satisfy { errors -> assert(errors.isNotEmpty()) }
    }

    @Test
    fun `delete folder`() {
        val id = authedTester()
            .document("""mutation { createFolder(name: "ToDelete") { id } }""")
            .execute()
            .path("createFolder.id").entity(String::class.java).get()

        authedTester()
            .document("""mutation { deleteFolder(id: "$id") }""")
            .execute()
            .path("deleteFolder").entity(Boolean::class.java).isEqualTo(true)
    }

    @Test
    fun `query all folders`() {
        authedTester()
            .document("""mutation { createFolder(name: "QueryTest") { id } }""")
            .execute()

        authedTester()
            .document("""{ folders { id name parentId sortOrder } }""")
            .execute()
            .path("folders").entityList(Map::class.java).hasSizeGreaterThan(0)
    }

    @Test
    fun `move bookmark to folder`() {
        val folderId = authedTester()
            .document("""mutation { createFolder(name: "BookmarkFolder") { id } }""")
            .execute()
            .path("createFolder.id").entity(String::class.java).get()

        val bookmarkId = authedTester()
            .document("""mutation { addBookmark(url: "https://folder-move-test.com", title: "Folder Move Test") { id } }""")
            .execute()
            .path("addBookmark.id").entity(String::class.java).get()

        authedTester()
            .document("""mutation { moveBookmark(id: "$bookmarkId", folderId: "$folderId") { id folderId } }""")
            .execute()
            .path("moveBookmark.folderId").entity(String::class.java).isEqualTo(folderId)
    }

    @Test
    fun `export with folder structure`() {
        val folderId = authedTester()
            .document("""mutation { createFolder(name: "ExportFolder") { id } }""")
            .execute()
            .path("createFolder.id").entity(String::class.java).get()

        val bookmarkId = authedTester()
            .document("""mutation { addBookmark(url: "https://export-folder-test.com", title: "Export Folder Test") { id } }""")
            .execute()
            .path("addBookmark.id").entity(String::class.java).get()

        authedTester()
            .document("""mutation { moveBookmark(id: "$bookmarkId", folderId: "$folderId") { id } }""")
            .execute()

        authedTester()
            .document("""{ exportBookmarks }""")
            .execute()
            .path("exportBookmarks").entity(String::class.java).satisfies { html ->
                assert(html.contains("ExportFolder")) { "Export should contain folder name" }
                assert(html.contains("https://export-folder-test.com")) { "Export should contain bookmark URL" }
            }
    }
}
