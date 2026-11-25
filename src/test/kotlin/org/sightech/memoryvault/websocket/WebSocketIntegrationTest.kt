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
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.lang.reflect.Type
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

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
        stompClient = WebSocketStompClient(StandardWebSocketClient())
        stompClient.messageConverter = MappingJackson2MessageConverter()
    }

    @AfterEach
    fun teardown() {
        session?.takeIf { it.isConnected }?.disconnect()
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

    private fun subscribeTo(
        stompSession: StompSession,
        destination: String,
        received: CompletableFuture<Map<String, Any>>
    ) {
        stompSession.subscribe(destination, object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                @Suppress("UNCHECKED_CAST")
                received.complete(payload as Map<String, Any>)
            }
        })
    }

    /**
     * The broker does not acknowledge subscriptions, so readiness is detected by publishing until
     * the first delivery arrives. The wait ends as soon as the subscription is live and fails
     * after a fixed deadline.
     */
    private fun publishUntilReceived(
        received: CompletableFuture<Map<String, Any>>,
        event: () -> Any
    ): Map<String, Any> {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            eventPublisher.publishEvent(event())
            try {
                return received.get(100, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                // subscription not live yet; publish again
            }
        }
        fail("No signal received before the deadline")
    }

    @Test
    fun `receives FeedSyncCompleted signal on feeds topic`() {
        val stompSession = connectWithJwt()
        val received = CompletableFuture<Map<String, Any>>()

        subscribeTo(stompSession, "/topic/user/$userId/feeds", received)

        val signal = publishUntilReceived(received) {
            FeedSyncCompleted(
                userId = userId,
                timestamp = Instant.now(),
                feedId = UUID.randomUUID(),
                newItemCount = 3,
                feedsRefreshed = 1
            )
        }
        assertEquals("FEED_SYNC_COMPLETED", signal["eventType"])
        assertEquals(3, signal["newItemCount"])
    }

    @Test
    fun `receives JobStatusChanged signal on jobs topic`() {
        val stompSession = connectWithJwt()
        val received = CompletableFuture<Map<String, Any>>()

        subscribeTo(stompSession, "/topic/user/$userId/jobs", received)

        val signal = publishUntilReceived(received) {
            JobStatusChanged(
                userId = userId,
                timestamp = Instant.now(),
                jobId = UUID.randomUUID(),
                jobType = "RSS_FETCH",
                oldStatus = "PENDING",
                newStatus = "RUNNING"
            )
        }
        assertEquals("JOB_STATUS_CHANGED", signal["eventType"])
        assertEquals("RUNNING", signal["newStatus"])
    }

    @Test
    fun `receives ContentMutated signal on sync topic`() {
        val stompSession = connectWithJwt()
        val received = CompletableFuture<Map<String, Any>>()

        subscribeTo(stompSession, "/topic/user/$userId/sync", received)

        val signal = publishUntilReceived(received) {
            ContentMutated(
                userId = userId,
                timestamp = Instant.now(),
                contentType = ContentType.BOOKMARK,
                mutationType = MutationType.CREATED,
                entityId = UUID.randomUUID()
            )
        }
        assertEquals("CONTENT_MUTATED", signal["eventType"])
        assertEquals("BOOKMARK", signal["contentType"])
        assertEquals("CREATED", signal["mutationType"])
    }

    @Test
    fun `receives VideoDownloadCompleted signal on videos topic`() {
        val stompSession = connectWithJwt()
        val received = CompletableFuture<Map<String, Any>>()

        subscribeTo(stompSession, "/topic/user/$userId/videos", received)

        val signal = publishUntilReceived(received) {
            VideoDownloadCompleted(
                userId = userId,
                timestamp = Instant.now(),
                videoId = UUID.randomUUID(),
                listId = UUID.randomUUID(),
                success = true
            )
        }
        assertEquals("VIDEO_DOWNLOAD_COMPLETED", signal["eventType"])
        assertEquals(true, signal["success"])
    }

    @Test
    fun `receives IngestReady signal on ingests topic`() {
        val stompSession = connectWithJwt()
        val received = CompletableFuture<Map<String, Any>>()

        subscribeTo(stompSession, "/topic/user/$userId/ingests", received)

        val signal = publishUntilReceived(received) {
            IngestReady(
                userId = userId,
                timestamp = Instant.now(),
                previewId = UUID.randomUUID(),
                itemCount = 10
            )
        }
        assertEquals("INGEST_READY", signal["eventType"])
        assertEquals(10, signal["itemCount"])
    }

    @Test
    fun `does not receive another user's signals and the session stays connected`() {
        val stompSession = connectWithJwt()
        val otherUserId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val receivedOther = CompletableFuture<Map<String, Any>>()
        val receivedOwn = CompletableFuture<Map<String, Any>>()

        // Frames on one session are processed in order, so once the own-topic subscription is
        // live the earlier frame for the other user's topic has already been handled.
        subscribeTo(stompSession, "/topic/user/$otherUserId/jobs", receivedOther)
        subscribeTo(stompSession, "/topic/user/$userId/jobs", receivedOwn)

        publishUntilReceived(receivedOwn) {
            JobStatusChanged(
                userId = userId,
                timestamp = Instant.now(),
                jobId = UUID.randomUUID(),
                jobType = "RSS_FETCH",
                oldStatus = "PENDING",
                newStatus = "RUNNING"
            )
        }

        eventPublisher.publishEvent(JobStatusChanged(
            userId = otherUserId,
            timestamp = Instant.now(),
            jobId = UUID.randomUUID(),
            jobType = "RSS_FETCH",
            oldStatus = "PENDING",
            newStatus = "RUNNING"
        ))

        // The session is still usable: a new subscription on it still receives its own signals.
        val receivedAfter = CompletableFuture<Map<String, Any>>()
        subscribeTo(stompSession, "/topic/user/$userId/sync", receivedAfter)
        publishUntilReceived(receivedAfter) {
            ContentMutated(
                userId = userId,
                timestamp = Instant.now(),
                contentType = ContentType.BOOKMARK,
                mutationType = MutationType.CREATED,
                entityId = UUID.randomUUID()
            )
        }

        assertTrue(stompSession.isConnected, "Session should stay connected after a dropped SUBSCRIBE")
        assertTrue(!receivedOther.isDone, "Should not receive another user's signal")
    }

    @Test
    fun `rejects connection without JWT`() {
        val client = WebSocketStompClient(StandardWebSocketClient())
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
