package org.sightech.memoryvault.websocket

import java.time.Instant
import java.util.UUID

enum class VaultEventType {
    FEED_SYNC_COMPLETED,
    JOB_STATUS_CHANGED,
    VIDEO_DOWNLOAD_COMPLETED,
    INGEST_READY,
    CONTENT_MUTATED,
    BACKUP_LOST
}

enum class ContentType {
    BOOKMARK, FEED_ITEM, VIDEO, FOLDER, CATEGORY
}

enum class MutationType {
    CREATED, UPDATED, DELETED
}

sealed interface VaultEvent {
    val eventType: VaultEventType
    val userId: UUID
    val timestamp: Instant
}

data class FeedSyncCompleted(
    override val userId: UUID,
    override val timestamp: Instant,
    val feedId: UUID?,
    val newItemCount: Int,
    val feedsRefreshed: Int
) : VaultEvent {
    override val eventType = VaultEventType.FEED_SYNC_COMPLETED
}

data class JobStatusChanged(
    override val userId: UUID,
    override val timestamp: Instant,
    val jobId: UUID,
    val jobType: String,
    val oldStatus: String,
    val newStatus: String
) : VaultEvent {
    override val eventType = VaultEventType.JOB_STATUS_CHANGED
}

data class VideoDownloadCompleted(
    override val userId: UUID,
    override val timestamp: Instant,
    val videoId: UUID,
    val listId: UUID,
    val success: Boolean
) : VaultEvent {
    override val eventType = VaultEventType.VIDEO_DOWNLOAD_COMPLETED
}

data class IngestReady(
    override val userId: UUID,
    override val timestamp: Instant,
    val previewId: UUID,
    val itemCount: Int
) : VaultEvent {
    override val eventType = VaultEventType.INGEST_READY
}

data class ContentMutated(
    override val userId: UUID,
    override val timestamp: Instant,
    val contentType: ContentType,
    val mutationType: MutationType,
    val entityId: UUID? = null
) : VaultEvent {
    override val eventType = VaultEventType.CONTENT_MUTATED
}

data class BackupLost(
    override val userId: UUID,
    override val timestamp: Instant,
    val videoId: UUID,
    val providerId: UUID,
    val externalUrl: String?
) : VaultEvent {
    override val eventType = VaultEventType.BACKUP_LOST
}
