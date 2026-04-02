package org.sightech.memoryvault.websocket

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class VaultEventTest {

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `FeedSyncCompleted has correct eventType`() {
        val event = FeedSyncCompleted(
            userId = userId,
            timestamp = Instant.now(),
            feedId = UUID.randomUUID(),
            newItemCount = 5,
            feedsRefreshed = 1
        )
        assertEquals(VaultEventType.FEED_SYNC_COMPLETED, event.eventType)
    }

    @Test
    fun `JobStatusChanged has correct eventType`() {
        val event = JobStatusChanged(
            userId = userId,
            timestamp = Instant.now(),
            jobId = UUID.randomUUID(),
            jobType = "RSS_FETCH",
            oldStatus = "PENDING",
            newStatus = "RUNNING"
        )
        assertEquals(VaultEventType.JOB_STATUS_CHANGED, event.eventType)
    }

    @Test
    fun `VideoDownloadCompleted has correct eventType`() {
        val event = VideoDownloadCompleted(
            userId = userId,
            timestamp = Instant.now(),
            videoId = UUID.randomUUID(),
            listId = UUID.randomUUID(),
            success = true
        )
        assertEquals(VaultEventType.VIDEO_DOWNLOAD_COMPLETED, event.eventType)
    }

    @Test
    fun `IngestReady has correct eventType`() {
        val event = IngestReady(
            userId = userId,
            timestamp = Instant.now(),
            previewId = UUID.randomUUID(),
            itemCount = 10
        )
        assertEquals(VaultEventType.INGEST_READY, event.eventType)
    }

    @Test
    fun `ContentMutated has correct eventType`() {
        val event = ContentMutated(
            userId = userId,
            timestamp = Instant.now(),
            contentType = ContentType.BOOKMARK,
            mutationType = MutationType.CREATED,
            entityId = UUID.randomUUID()
        )
        assertEquals(VaultEventType.CONTENT_MUTATED, event.eventType)
    }
}
