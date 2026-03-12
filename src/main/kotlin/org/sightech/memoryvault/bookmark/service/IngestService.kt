package org.sightech.memoryvault.bookmark.service

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.bookmark.entity.*
import org.sightech.memoryvault.bookmark.repository.BookmarkRepository
import org.sightech.memoryvault.bookmark.repository.FolderRepository
import org.sightech.memoryvault.bookmark.repository.IngestPreviewRepository
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.net.URI

@Service
class IngestService(
    private val bookmarkRepository: BookmarkRepository,
    private val folderRepository: FolderRepository,
    private val bookmarkService: BookmarkService,
    private val ingestPreviewRepository: IngestPreviewRepository,
    private val objectMapper: ObjectMapper
) {

    fun generatePreview(input: List<IngestBookmarkInput>): IngestPreviewResult {
        val userId = CurrentUser.userId()
        val folders = folderRepository.findAllActiveByUserId(userId)
        val folderByName = folders.associateBy { it.name }

        val items = input.map { item ->
            val normalizedUrl = normalizeUrl(item.url)
            val existing = bookmarkRepository.findByNormalizedUrlAndUserId(normalizedUrl, userId)
            val deleted = if (existing == null) {
                bookmarkRepository.findByNormalizedUrlIncludingDeleted(normalizedUrl, userId)
            } else null

            when {
                deleted != null -> IngestItem(
                    url = item.url, title = item.title,
                    status = IngestStatus.PREVIOUSLY_DELETED,
                    existingBookmarkId = deleted.id,
                    browserFolder = item.browserFolder
                )
                existing == null -> IngestItem(
                    url = item.url, title = item.title,
                    status = IngestStatus.NEW,
                    suggestedFolderId = item.browserFolder?.let { folderByName[it]?.id },
                    browserFolder = item.browserFolder
                )
                existing.title != item.title -> IngestItem(
                    url = item.url, title = item.title,
                    status = IngestStatus.TITLE_CHANGED,
                    existingBookmarkId = existing.id,
                    browserFolder = item.browserFolder
                )
                item.browserFolder != null && existing.folderId != folderByName[item.browserFolder]?.id -> IngestItem(
                    url = item.url, title = item.title,
                    status = IngestStatus.MOVED,
                    existingBookmarkId = existing.id,
                    suggestedFolderId = folderByName[item.browserFolder]?.id,
                    browserFolder = item.browserFolder
                )
                else -> IngestItem(
                    url = item.url, title = item.title,
                    status = IngestStatus.UNCHANGED,
                    existingBookmarkId = existing.id
                )
            }
        }

        val summary = IngestSummary(
            newCount = items.count { it.status == IngestStatus.NEW },
            unchangedCount = items.count { it.status == IngestStatus.UNCHANGED },
            movedCount = items.count { it.status == IngestStatus.MOVED },
            titleChangedCount = items.count { it.status == IngestStatus.TITLE_CHANGED },
            previouslyDeletedCount = items.count { it.status == IngestStatus.PREVIOUSLY_DELETED }
        )

        // Store preview in DB
        val previewEntity = IngestPreviewEntity(
            userId = userId,
            previewData = objectMapper.writeValueAsString(mapOf("items" to items, "summary" to summary))
        )
        ingestPreviewRepository.save(previewEntity)

        return IngestPreviewResult(previewId = previewEntity.id, items = items, summary = summary)
    }

    companion object {
        fun normalizeUrl(url: String): String {
            val uri = URI.create(url.trim())
            val scheme = (uri.scheme ?: "https").lowercase()
            val host = (uri.host ?: "").lowercase().removePrefix("www.")
            val port = if (uri.port > 0 && uri.port != 443 && uri.port != 80) ":${uri.port}" else ""
            val path = (uri.rawPath ?: "").trimEnd('/')
            val query = uri.rawQuery?.split("&")?.sorted()?.joinToString("&")
            val queryString = if (query != null) "?$query" else ""
            return "$scheme://$host$port$path$queryString"
        }
    }
}
