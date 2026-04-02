package org.sightech.memoryvault.websocket

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.service.JwtService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ApplicationEventPublisher
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import org.springframework.web.socket.sockjs.client.SockJsClient
import org.springframework.web.socket.sockjs.client.WebSocketTransport
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.lang.reflect.Type
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketIntegrationTest {

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
            registry.add("memoryvault.websocket.allowed-origins") { "*" }
        }
    }

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var jwtService: JwtService

    @Autowired
    private lateinit var eventPublisher: ApplicationEventPublisher

    private lateinit var stompClient: WebSocketStompClient
    private var session: StompSession? = null

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setup() {
        val sockJsClient = SockJsClient(listOf(WebSocketTransport(StandardWebSocketClient())))
        stompClient = WebSocketStompClient(sockJsClient)
        stompClient.messageConverter = MappingJackson2MessageConverter()
    }

    @AfterEach
    fun teardown() {
        session?.disconnect()
        stompClient.stop()
    }

    private fun connectWithJwt(): StompSession {
        val token = jwtService.generateToken(userId, "system@memoryvault.local", "OWNER")
        val headers = StompHeaders()
        headers.add("Authorization", "Bearer $token")

        val future = stompClient.connectAsync(
            "ws://localhost:$port/ws",
            null,
            headers,
            object : StompSessionHandlerAdapter() {}
        )
        return future.get(5, TimeUnit.SECONDS).also { session = it }
    }

    @Test
    fun `receives FeedSyncCompleted signal on feeds topic`() {
        val stompSession = connectWithJwt()
        val received = CompletableFuture<Map<String, Any>>()

        stompSession.subscribe("/topic/user/$userId/feeds", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                @Suppress("UNCHECKED_CAST")
                received.complete(payload as Map<String, Any>)
            }
        })

        Thread.sleep(500)

        eventPublisher.publishEvent(FeedSyncCompleted(
            userId = userId,
            timestamp = Instant.now(),
            feedId = UUID.randomUUID(),
            newItemCount = 3,
            feedsRefreshed = 1
        ))

        val signal = received.get(5, TimeUnit.SECONDS)
        assertEquals("FEED_SYNC_COMPLETED", signal["eventType"])
        assertEquals(3, signal["newItemCount"])
    }

    @Test
    fun `receives JobStatusChanged signal on jobs topic`() {
        val stompSession = connectWithJwt()
        val received = CompletableFuture<Map<String, Any>>()

        stompSession.subscribe("/topic/user/$userId/jobs", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                @Suppress("UNCHECKED_CAST")
                received.complete(payload as Map<String, Any>)
            }
        })

        Thread.sleep(500)

        eventPublisher.publishEvent(JobStatusChanged(
            userId = userId,
            timestamp = Instant.now(),
            jobId = UUID.randomUUID(),
            jobType = "RSS_FETCH",
            oldStatus = "PENDING",
            newStatus = "RUNNING"
        ))

        val signal = received.get(5, TimeUnit.SECONDS)
        assertEquals("JOB_STATUS_CHANGED", signal["eventType"])
        assertEquals("RUNNING", signal["newStatus"])
    }

    @Test
    fun `receives ContentMutated signal on sync topic`() {
        val stompSession = connectWithJwt()
        val received = CompletableFuture<Map<String, Any>>()

        stompSession.subscribe("/topic/user/$userId/sync", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                @Suppress("UNCHECKED_CAST")
                received.complete(payload as Map<String, Any>)
            }
        })

        Thread.sleep(500)

        eventPublisher.publishEvent(ContentMutated(
            userId = userId,
            timestamp = Instant.now(),
            contentType = ContentType.BOOKMARK,
            mutationType = MutationType.CREATED,
            entityId = UUID.randomUUID()
        ))

        val signal = received.get(5, TimeUnit.SECONDS)
        assertEquals("CONTENT_MUTATED", signal["eventType"])
        assertEquals("BOOKMARK", signal["contentType"])
        assertEquals("CREATED", signal["mutationType"])
    }

    @Test
    fun `receives VideoDownloadCompleted signal on videos topic`() {
        val stompSession = connectWithJwt()
        val received = CompletableFuture<Map<String, Any>>()

        stompSession.subscribe("/topic/user/$userId/videos", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                @Suppress("UNCHECKED_CAST")
                received.complete(payload as Map<String, Any>)
            }
        })

        Thread.sleep(500)

        eventPublisher.publishEvent(VideoDownloadCompleted(
            userId = userId,
            timestamp = Instant.now(),
            videoId = UUID.randomUUID(),
            listId = UUID.randomUUID(),
            success = true
        ))

        val signal = received.get(5, TimeUnit.SECONDS)
        assertEquals("VIDEO_DOWNLOAD_COMPLETED", signal["eventType"])
        assertEquals(true, signal["success"])
    }

    @Test
    fun `receives IngestReady signal on ingests topic`() {
        val stompSession = connectWithJwt()
        val received = CompletableFuture<Map<String, Any>>()

        stompSession.subscribe("/topic/user/$userId/ingests", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                @Suppress("UNCHECKED_CAST")
                received.complete(payload as Map<String, Any>)
            }
        })

        Thread.sleep(500)

        eventPublisher.publishEvent(IngestReady(
            userId = userId,
            timestamp = Instant.now(),
            previewId = UUID.randomUUID(),
            itemCount = 10
        ))

        val signal = received.get(5, TimeUnit.SECONDS)
        assertEquals("INGEST_READY", signal["eventType"])
        assertEquals(10, signal["itemCount"])
    }

    @Test
    fun `rejects connection without JWT`() {
        val sockJsClient = SockJsClient(listOf(WebSocketTransport(StandardWebSocketClient())))
        val client = WebSocketStompClient(sockJsClient)
        client.messageConverter = MappingJackson2MessageConverter()

        val future = client.connectAsync(
            "ws://localhost:$port/ws",
            object : StompSessionHandlerAdapter() {}
        )

        try {
            future.get(3, TimeUnit.SECONDS)
            assertTrue(false, "Expected connection to be rejected without JWT")
        } catch (e: Exception) {
            assertTrue(true)
        } finally {
            client.stop()
        }
    }
}
