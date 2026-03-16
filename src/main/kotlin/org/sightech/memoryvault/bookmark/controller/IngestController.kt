package org.sightech.memoryvault.bookmark.controller

import org.sightech.memoryvault.bookmark.entity.*
import org.sightech.memoryvault.bookmark.service.IngestService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/bookmarks/ingest")
class IngestController(
    private val ingestService: IngestService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class IngestRequest(
        val bookmarks: List<IngestBookmarkInput>
    )

    data class CommitRequest(
        val resolutions: List<IngestResolution>
    )

    @PostMapping
    fun ingest(@RequestBody request: IngestRequest): ResponseEntity<IngestPreviewResult> {
        log.info("Ingest request received with {} bookmarks", request.bookmarks.size)
        val preview = ingestService.generatePreview(request.bookmarks)
        log.info("Ingest preview created: id={}, new={}, unchanged={}, moved={}, titleChanged={}, previouslyDeleted={}",
            preview.previewId, preview.summary.newCount, preview.summary.unchangedCount,
            preview.summary.movedCount, preview.summary.titleChangedCount, preview.summary.previouslyDeletedCount)
        return ResponseEntity.ok(preview)
    }

    @GetMapping("/{previewId}")
    fun getPreview(@PathVariable previewId: UUID): ResponseEntity<IngestPreviewResult> {
        log.info("Fetching ingest preview: id={}", previewId)
        val preview = ingestService.getPreview(previewId)
            ?: return ResponseEntity.notFound().build<IngestPreviewResult>().also {
                log.warn("Ingest preview not found: id={}", previewId)
            }
        return ResponseEntity.ok(preview)
    }

    @PostMapping("/{previewId}/commit")
    fun commit(
        @PathVariable previewId: UUID,
        @RequestBody request: CommitRequest
    ): ResponseEntity<CommitResult> {
        log.info("Committing ingest preview: id={}, resolutions={}", previewId, request.resolutions.size)
        val result = ingestService.commitResolutions(previewId, request.resolutions)
        log.info("Ingest committed: accepted={}, skipped={}, undeleted={}", result.accepted, result.skipped, result.undeleted)
        return ResponseEntity.ok(result)
    }
}
