package org.sightech.memoryvault.bookmark.service

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.bookmark.entity.*
import org.sightech.memoryvault.bookmark.repository.BookmarkRepository
import org.sightech.memoryvault.bookmark.repository.FolderRepository
import org.sightech.memoryvault.bookmark.repository.IngestPreviewRepository
import org.sightech.memoryvault.websocket.IngestReady
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Instant
import java.util.UUID

@Service
class IngestService(
    private val bookmarkRepository: BookmarkRepository,
    private val folderRepository: FolderRepository,
    private val bookmarkService: BookmarkService,
    private val ingestPreviewRepository: IngestPreviewRepository,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

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

        eventPublisher.publishEvent(IngestReady(
            userId = userId, timestamp = Instant.now(),
            previewId = previewEntity.id, itemCount = items.size
        ))

        log.info("Generated ingest preview previewId={} itemCount={}", previewEntity.id, items.size)
        return IngestPreviewResult(previewId = previewEntity.id, items = items, summary = summary)
    }

    fun commitResolutions(previewId: UUID, resolutions: List<IngestResolution>): CommitResult {
        val userId = CurrentUser.userId()
        val preview = ingestPreviewRepository.findActiveByIdAndUserId(previewId, userId)
            ?: throw IllegalArgumentException("Preview not found or expired")

        if (preview.committed) {
            log.warn("Ingest preview already committed previewId={}", previewId)
            return CommitResult(accepted = 0, skipped = 0, undeleted = 0)
        }

        val data = objectMapper.readTree(preview.previewData)
        val items = data["items"].map { node ->
            IngestItem(
                url = node["url"].stringValue(),
                title = node["title"].stringValue(),
                status = IngestStatus.valueOf(node["status"].stringValue()),
                existingBookmarkId = node["existingBookmarkId"]?.stringValue()?.let { UUID.fromString(it) },
                suggestedFolderId = node["suggestedFolderId"]?.stringValue()?.let { UUID.fromString(it) },
                browserFolder = node["browserFolder"]?.stringValue()
            )
        }

        val resolutionMap = resolutions.associateBy { normalizeUrl(it.url) }
        var accepted = 0
        var skipped = 0
        var undeleted = 0
        // Maps full folder path (e.g., "Tech/AI") to its folder ID
        val createdFolders = mutableMapOf<String, UUID>()

        fun resolveOrCreateFolderPath(path: String): UUID {
            return createdFolders.getOrPut(path) {
                val segments = path.split("/")
                var parentId: UUID? = null
                // Walk each segment, creating intermediate folders as needed
                for (i in segments.indices) {
                    val subPath = segments.subList(0, i + 1).joinToString("/")
                    parentId = createdFolders.getOrPut(subPath) {
                        log.info("Creating folder '{}' (parent={}) during ingest commit", segments[i], parentId)
                        bookmarkService.createFolder(segments[i], parentId).id
                    }
                }
                parentId!!
            }
        }

        items.forEach { item ->
            val normalizedUrl = normalizeUrl(item.url)
            val resolution = resolutionMap[normalizedUrl] ?: return@forEach
            when (resolution.action) {
                IngestAction.SKIP -> skipped++
                IngestAction.ACCEPT -> {
                    when (item.status) {
                        IngestStatus.NEW -> {
                            val bookmark = bookmarkService.create(item.url, item.title, emptyList())
                            val folderId = item.suggestedFolderId ?: item.browserFolder?.let { resolveOrCreateFolderPath(it) }
                            folderId?.let { bookmarkService.moveBookmark(bookmark.id, it) }
                        }
                        IngestStatus.TITLE_CHANGED -> {
                            item.existingBookmarkId?.let { id ->
                                val bookmark = bookmarkRepository.findById(id).orElse(null)
                                if (bookmark != null) {
                                    bookmark.title = item.title
                                    bookmark.updatedAt = Instant.now()
                                    bookmarkRepository.save(bookmark)
                                }
                            }
                        }
                        IngestStatus.MOVED -> {
                            item.existingBookmarkId?.let { id ->
                                val folderId = item.suggestedFolderId ?: item.browserFolder?.let { resolveOrCreateFolderPath(it) }
                                folderId?.let { bookmarkService.moveBookmark(id, it) }
                            }
                        }
                        else -> {}
                    }
                    accepted++
                }
                IngestAction.UNDELETE -> {
                    item.existingBookmarkId?.let { id ->
                        val bookmark = bookmarkRepository.findById(id).orElse(null)
                        if (bookmark != null) {
                            bookmark.deletedAt = null
                            bookmark.updatedAt = Instant.now()
                            bookmarkRepository.save(bookmark)
                        }
                    }
                    undeleted++
                }
            }
        }

        preview.committed = true
        ingestPreviewRepository.save(preview)

        log.info("Committed ingest previewId={} accepted={} skipped={} undeleted={}", previewId, accepted, skipped, undeleted)
        return CommitResult(accepted = accepted, skipped = skipped, undeleted = undeleted)
    }

    fun getPreview(previewId: UUID): IngestPreviewResult? {
        val userId = CurrentUser.userId()
        val preview = ingestPreviewRepository.findActiveByIdAndUserId(previewId, userId) ?: return null
        val data = objectMapper.readTree(preview.previewData)
        val items = data["items"].map { node ->
            IngestItem(
                url = node["url"].stringValue(),
                title = node["title"].stringValue(),
                status = IngestStatus.valueOf(node["status"].stringValue()),
                existingBookmarkId = node["existingBookmarkId"]?.stringValue()?.let { UUID.fromString(it) },
                suggestedFolderId = node["suggestedFolderId"]?.stringValue()?.let { UUID.fromString(it) },
                browserFolder = node["browserFolder"]?.stringValue()
            )
        }
        val summary = IngestSummary(
            newCount = items.count { it.status == IngestStatus.NEW },
            unchangedCount = items.count { it.status == IngestStatus.UNCHANGED },
            movedCount = items.count { it.status == IngestStatus.MOVED },
            titleChangedCount = items.count { it.status == IngestStatus.TITLE_CHANGED },
            previouslyDeletedCount = items.count { it.status == IngestStatus.PREVIOUSLY_DELETED }
        )
        return IngestPreviewResult(previewId = preview.id, items = items, summary = summary)
    }

    fun getPendingPreviews(): List<IngestPreviewResult> {
        val userId = CurrentUser.userId()
        return ingestPreviewRepository.findPendingByUserId(userId).map { preview ->
            val data = objectMapper.readTree(preview.previewData)
            val items = data["items"].map { node ->
                IngestItem(
                    url = node["url"].stringValue(),
                    title = node["title"].stringValue(),
                    status = IngestStatus.valueOf(node["status"].stringValue()),
                    existingBookmarkId = node["existingBookmarkId"]?.stringValue()?.let { UUID.fromString(it) },
                    suggestedFolderId = node["suggestedFolderId"]?.stringValue()?.let { UUID.fromString(it) },
                    browserFolder = node["browserFolder"]?.stringValue()
                )
            }
            val summary = IngestSummary(
                newCount = items.count { it.status == IngestStatus.NEW },
                unchangedCount = items.count { it.status == IngestStatus.UNCHANGED },
                movedCount = items.count { it.status == IngestStatus.MOVED },
                titleChangedCount = items.count { it.status == IngestStatus.TITLE_CHANGED },
                previouslyDeletedCount = items.count { it.status == IngestStatus.PREVIOUSLY_DELETED }
            )
            IngestPreviewResult(previewId = preview.id, items = items, summary = summary)
        }
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
