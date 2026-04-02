package org.sightech.memoryvault.websocket

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class WebSocketEventRelay(private val messagingTemplate: SimpMessagingTemplate) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async("websocketRelayExecutor")
    @EventListener
    fun onFeedSyncCompleted(event: FeedSyncCompleted) {
        send("/topic/user/${event.userId}/feeds", mapOf(
            "eventType" to event.eventType.name,
            "feedId" to event.feedId?.toString(),
            "newItemCount" to event.newItemCount
        ))
    }

    @Async("websocketRelayExecutor")
    @EventListener
    fun onJobStatusChanged(event: JobStatusChanged) {
        send("/topic/user/${event.userId}/jobs", mapOf(
            "eventType" to event.eventType.name,
            "jobId" to event.jobId.toString(),
            "jobType" to event.jobType,
            "newStatus" to event.newStatus
        ))
    }

    @Async("websocketRelayExecutor")
    @EventListener
    fun onVideoDownloadCompleted(event: VideoDownloadCompleted) {
        send("/topic/user/${event.userId}/videos", mapOf(
            "eventType" to event.eventType.name,
            "videoId" to event.videoId.toString(),
            "listId" to event.listId.toString(),
            "success" to event.success
        ))
    }

    @Async("websocketRelayExecutor")
    @EventListener
    fun onIngestReady(event: IngestReady) {
        send("/topic/user/${event.userId}/ingests", mapOf(
            "eventType" to event.eventType.name,
            "previewId" to event.previewId.toString(),
            "itemCount" to event.itemCount
        ))
    }

    @Async("websocketRelayExecutor")
    @EventListener
    fun onContentMutated(event: ContentMutated) {
        send("/topic/user/${event.userId}/sync", mapOf(
            "eventType" to event.eventType.name,
            "contentType" to event.contentType.name,
            "mutationType" to event.mutationType.name,
            "entityId" to event.entityId?.toString()
        ))
    }

    private fun send(topic: String, payload: Map<String, Any?>) {
        try {
            messagingTemplate.convertAndSend(topic, payload)
            log.debug("Signal sent topic={} eventType={}", topic, payload["eventType"])
        } catch (e: Exception) {
            log.warn("Failed to relay signal topic={} eventType={}: {}", topic, payload["eventType"], e.message)
        }
    }
}
