package org.sightech.memoryvault.bookmark.entity

import java.util.UUID

enum class IngestStatus {
    NEW, UNCHANGED, MOVED, TITLE_CHANGED, PREVIOUSLY_DELETED
}

data class IngestBookmarkInput(
    val url: String,
    val title: String,
    val browserFolder: String?
)

data class IngestItem(
    val url: String,
    val title: String,
    val status: IngestStatus,
    val existingBookmarkId: UUID? = null,
    val suggestedFolderId: UUID? = null,
    val browserFolder: String? = null
)

data class IngestSummary(
    val newCount: Int = 0,
    val unchangedCount: Int = 0,
    val movedCount: Int = 0,
    val titleChangedCount: Int = 0,
    val previouslyDeletedCount: Int = 0
)

data class IngestPreviewResult(
    val previewId: UUID,
    val items: List<IngestItem>,
    val summary: IngestSummary
)

enum class IngestAction {
    ACCEPT, SKIP, UNDELETE
}

data class IngestResolution(
    val url: String,
    val action: IngestAction
)

data class CommitResult(
    val accepted: Int,
    val skipped: Int,
    val undeleted: Int
)
