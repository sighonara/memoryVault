package org.sightech.memoryvault.bookmark.controller

import org.sightech.memoryvault.bookmark.entity.*
import org.sightech.memoryvault.bookmark.service.IngestService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/bookmarks/ingest")
class IngestController(
    private val ingestService: IngestService
) {

    data class IngestRequest(
        val bookmarks: List<IngestBookmarkInput>
    )

    data class CommitRequest(
        val resolutions: List<IngestResolution>
    )

    @PostMapping
    fun ingest(@RequestBody request: IngestRequest): ResponseEntity<IngestPreviewResult> {
        val preview = ingestService.generatePreview(request.bookmarks)
        return ResponseEntity.ok(preview)
    }

    @GetMapping("/{previewId}")
    fun getPreview(@PathVariable previewId: UUID): ResponseEntity<IngestPreviewResult> {
        val preview = ingestService.getPreview(previewId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(preview)
    }

    @PostMapping("/{previewId}/commit")
    fun commit(
        @PathVariable previewId: UUID,
        @RequestBody request: CommitRequest
    ): ResponseEntity<CommitResult> {
        val result = ingestService.commitResolutions(previewId, request.resolutions)
        return ResponseEntity.ok(result)
    }
}
