package org.sightech.memoryvault.websocket

import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebSocketEventRelayTest {

    private val messagingTemplate = mockk<SimpMessagingTemplate>(relaxed = true)
    private val relay = WebSocketEventRelay(messagingTemplate)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `relays FeedSyncCompleted to correct topic`() {
        val feedId = UUID.randomUUID()
        val event = FeedSyncCompleted(
            userId = userId,
            timestamp = Instant.now(),
            feedId = feedId,
            newItemCount = 5,
            feedsRefreshed = 1
        )

        relay.onFeedSyncCompleted(event)

        val topicSlot = slot<String>()
        val payloadSlot = slot<Any>()
        verify { messagingTemplate.convertAndSend(capture(topicSlot), capture(payloadSlot)) }
        assertEquals("/topic/user/$userId/feeds", topicSlot.captured)
        val payload = payloadSlot.captured as Map<*, *>
        assertEquals("FEED_SYNC_COMPLETED", payload["eventType"])
        assertEquals(feedId.toString(), payload["feedId"])
        assertEquals(5, payload["newItemCount"])
    }

    @Test
    fun `relays JobStatusChanged to correct topic`() {
        val jobId = UUID.randomUUID()
        val event = JobStatusChanged(
            userId = userId,
            timestamp = Instant.now(),
            jobId = jobId,
            jobType = "RSS_FETCH",
            oldStatus = "PENDING",
            newStatus = "RUNNING"
        )

        relay.onJobStatusChanged(event)

        val topicSlot = slot<String>()
        val payloadSlot = slot<Any>()
        verify { messagingTemplate.convertAndSend(capture(topicSlot), capture(payloadSlot)) }
        assertEquals("/topic/user/$userId/jobs", topicSlot.captured)
        val payload = payloadSlot.captured as Map<*, *>
        assertEquals("JOB_STATUS_CHANGED", payload["eventType"])
        assertEquals("RUNNING", payload["newStatus"])
    }

    @Test
    fun `relays VideoDownloadCompleted to correct topic`() {
        val videoId = UUID.randomUUID()
        val listId = UUID.randomUUID()
        val event = VideoDownloadCompleted(
            userId = userId,
            timestamp = Instant.now(),
            videoId = videoId,
            listId = listId,
            success = true
        )

        relay.onVideoDownloadCompleted(event)

        val topicSlot = slot<String>()
        val payloadSlot = slot<Any>()
        verify { messagingTemplate.convertAndSend(capture(topicSlot), capture(payloadSlot)) }
        assertEquals("/topic/user/$userId/videos", topicSlot.captured)
        val payload = payloadSlot.captured as Map<*, *>
        assertEquals("VIDEO_DOWNLOAD_COMPLETED", payload["eventType"])
        assertEquals(true, payload["success"])
    }

    @Test
    fun `relays IngestReady to correct topic`() {
        val previewId = UUID.randomUUID()
        val event = IngestReady(
            userId = userId,
            timestamp = Instant.now(),
            previewId = previewId,
            itemCount = 10
        )

        relay.onIngestReady(event)

        val topicSlot = slot<String>()
        val payloadSlot = slot<Any>()
        verify { messagingTemplate.convertAndSend(capture(topicSlot), capture(payloadSlot)) }
        assertEquals("/topic/user/$userId/ingests", topicSlot.captured)
        val payload = payloadSlot.captured as Map<*, *>
        assertEquals("INGEST_READY", payload["eventType"])
        assertEquals(10, payload["itemCount"])
    }

    @Test
    fun `relays ContentMutated to correct topic`() {
        val entityId = UUID.randomUUID()
        val event = ContentMutated(
            userId = userId,
            timestamp = Instant.now(),
            contentType = ContentType.BOOKMARK,
            mutationType = MutationType.CREATED,
            entityId = entityId
        )

        relay.onContentMutated(event)

        val topicSlot = slot<String>()
        val payloadSlot = slot<Any>()
        verify { messagingTemplate.convertAndSend(capture(topicSlot), capture(payloadSlot)) }
        assertEquals("/topic/user/$userId/sync", topicSlot.captured)
        val payload = payloadSlot.captured as Map<*, *>
        assertEquals("CONTENT_MUTATED", payload["eventType"])
        assertEquals("BOOKMARK", payload["contentType"])
        assertEquals("CREATED", payload["mutationType"])
    }

    @Test
    fun `swallows exception from messagingTemplate`() {
        io.mockk.every { messagingTemplate.convertAndSend(any<String>(), any<Any>()) } throws RuntimeException("broker down")

        val event = ContentMutated(
            userId = userId,
            timestamp = Instant.now(),
            contentType = ContentType.FEED_ITEM,
            mutationType = MutationType.UPDATED,
            entityId = null
        )

        // Should not throw
        relay.onContentMutated(event)
    }
}
