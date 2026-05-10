package org.sightech.memoryvault.youtube.controller

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/videos")
class VideoController(
    private val videoRepository: VideoRepository,
    private val storageService: StorageService,
    private val subscriptionRepository: SubscriptionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/{id}/download")
    fun download(@PathVariable id: UUID): ResponseEntity<InputStreamResource> {
        val userId = CurrentUser.userId()
        val video = videoRepository.findByIdAndUserId(id, userId)
            ?: return ResponseEntity.notFound().build()
        val filePath = video.filePath
            ?: return ResponseEntity.notFound().build()

        val sub = subscriptionRepository.findByUserId(userId)
        val allowed = sub != null && (sub.status == SubscriptionStatus.ACTIVE ||
                sub.status == SubscriptionStatus.GRACE_PERIOD)
        if (!allowed) return ResponseEntity.status(403).build()

        log.info("Video download videoId={} userId={}", id, userId)
        val stream = storageService.retrieve(filePath)
        val filename = video.title?.replace("[^a-zA-Z0-9._-]".toRegex(), "_") ?: id.toString()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename.mp4\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(InputStreamResource(stream))
    }
}
