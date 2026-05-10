package org.sightech.memoryvault.billing.service

import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class VideoPurgeService(
    private val subscriptionRepository: SubscriptionRepository,
    private val videoRepository: VideoRepository,
    private val storageService: StorageService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun purge(): Map<String, Any> {
        val expired = subscriptionRepository.findExpiredGracePeriods(Instant.now())
        var purged = 0

        for (sub in expired) {
            val videos = videoRepository.findAllWithFilePathByUserId(sub.userId)
            for (video in videos) {
                val path = video.filePath ?: continue
                try {
                    storageService.delete(path)
                    video.filePath = null
                    videoRepository.save(video)
                    purged++
                } catch (e: Exception) {
                    log.warn("Failed to delete video file key={}: {}", path, e.message)
                }
            }
            sub.status = SubscriptionStatus.CANCELLED
            subscriptionRepository.save(sub)
            log.info("Purged videos for userId={} count={}", sub.userId, videos.size)
        }

        return mapOf("purged" to purged)
    }
}
