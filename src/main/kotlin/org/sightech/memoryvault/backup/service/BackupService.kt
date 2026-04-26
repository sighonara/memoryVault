package org.sightech.memoryvault.backup.service

import org.sightech.memoryvault.backup.entity.*
import org.sightech.memoryvault.backup.provider.BackupProviderFactory
import org.sightech.memoryvault.backup.provider.VideoBackupMetadata
import org.sightech.memoryvault.backup.repository.BackupProviderRepository
import org.sightech.memoryvault.backup.repository.BackupRecordRepository
import org.sightech.memoryvault.crypto.EncryptionService
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.websocket.VideoDownloadCompleted
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class BackupService(
    private val providerRepo: BackupProviderRepository,
    private val recordRepo: BackupRecordRepository,
    private val videoRepo: VideoRepository,
    private val providerFactory: BackupProviderFactory,
    private val storageService: StorageService,
    private val encryptionService: EncryptionService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun createPendingRecord(videoId: UUID, providerId: UUID): BackupRecord {
        val existing = recordRepo.findByVideoIdAndProviderId(videoId, providerId)
        if (existing != null) return existing

        val record = BackupRecord(videoId = videoId, providerId = providerId)
        log.info("Created PENDING backup record videoId={} providerId={}", videoId, providerId)
        return recordRepo.save(record)
    }

    fun processUpload(record: BackupRecord, providerEntity: BackupProviderEntity, youtubeVideoId: String, filePath: String) {
        record.status = BackupStatus.UPLOADING
        record.updatedAt = Instant.now()
        recordRepo.save(record)

        try {
            val provider = providerFactory.create(providerEntity)

            val existing = provider.search(youtubeVideoId)
            if (existing != null) {
                record.status = BackupStatus.BACKED_UP
                record.externalId = existing.externalId
                record.externalUrl = existing.externalUrl
                record.errorMessage = null
                record.updatedAt = Instant.now()
                recordRepo.save(record)
                log.info("Video already on provider videoId={} externalUrl={}", record.videoId, existing.externalUrl)
                return
            }

            val video = videoRepo.findById(record.videoId).orElse(null)
            val metadata = VideoBackupMetadata(
                youtubeVideoId = youtubeVideoId,
                title = video?.title,
                description = video?.description,
                youtubeUrl = video?.youtubeUrl ?: "https://www.youtube.com/watch?v=$youtubeVideoId"
            )

            storageService.retrieve(filePath).use { inputStream ->
                val result = provider.upload(inputStream, metadata)
                record.status = BackupStatus.BACKED_UP
                record.externalId = result.externalId
                record.externalUrl = result.externalUrl
                record.errorMessage = null
                record.updatedAt = Instant.now()
                recordRepo.save(record)
                log.info("Backup upload complete videoId={} externalUrl={}", record.videoId, result.externalUrl)
            }
        } catch (e: Exception) {
            record.status = BackupStatus.FAILED
            record.errorMessage = e.message
            record.updatedAt = Instant.now()
            recordRepo.save(record)
            log.warn("Backup upload failed videoId={}: {}", record.videoId, e.message)
        }
    }

    fun addProvider(userId: UUID, type: BackupProviderType, name: String, credentialsJson: String, isPrimary: Boolean): BackupProviderEntity {
        val encrypted = encryptionService.encrypt(credentialsJson)
        val provider = BackupProviderEntity(
            userId = userId,
            type = type,
            name = name,
            credentialsEncrypted = encrypted,
            isPrimary = isPrimary
        )
        log.info("Added backup provider name={} type={} userId={}", name, type, userId)
        return providerRepo.save(provider)
    }

    fun updateProvider(id: UUID, userId: UUID, name: String?, credentialsJson: String?): BackupProviderEntity? {
        val provider = providerRepo.findActiveByIdAndUserId(id, userId) ?: return null
        if (name != null) provider.name = name
        if (credentialsJson != null) provider.credentialsEncrypted = encryptionService.encrypt(credentialsJson)
        provider.updatedAt = Instant.now()
        log.info("Updated backup provider id={}", id)
        return providerRepo.save(provider)
    }

    fun deleteProvider(id: UUID, userId: UUID): Boolean {
        val provider = providerRepo.findActiveByIdAndUserId(id, userId) ?: return false
        provider.deletedAt = Instant.now()
        provider.updatedAt = Instant.now()
        providerRepo.save(provider)
        log.info("Soft-deleted backup provider id={}", id)
        return true
    }

    fun getProviders(userId: UUID): List<BackupProviderEntity> =
        providerRepo.findAllActiveByUserId(userId)

    fun getBackupRecords(videoId: UUID): List<BackupRecord> =
        recordRepo.findByVideoId(videoId)

    fun getBackupStatusForVideo(videoId: UUID): String? {
        val records = recordRepo.findByVideoId(videoId)
        if (records.isEmpty()) return null

        val hasPrimary = records.any { it.status == BackupStatus.BACKED_UP }
        val hasSecondary = records.size > 1 && records.drop(1).any { it.status == BackupStatus.BACKED_UP }
        val hasLost = records.any { it.status == BackupStatus.LOST }
        val hasFailed = records.any { it.status == BackupStatus.FAILED }
        val hasPending = records.any { it.status == BackupStatus.PENDING || it.status == BackupStatus.UPLOADING }

        return when {
            hasPrimary && hasSecondary -> "BACKED_UP_BOTH"
            hasSecondary && !hasPrimary -> "BACKED_UP_SECONDARY"
            hasPrimary -> "BACKED_UP"
            hasLost -> "LOST"
            hasFailed -> "FAILED"
            hasPending -> "PENDING"
            else -> null
        }
    }

    fun backfillCount(userId: UUID): Int {
        val primary = providerRepo.findPrimaryByUserId(userId) ?: return 0
        val downloadedIds = videoRepo.findDownloadedVideoIdsByUserId(userId)
        val backedUpIds = recordRepo.findVideoIdsByProviderId(primary.id).toSet()
        return downloadedIds.count { it !in backedUpIds }
    }

    fun triggerBackfill(userId: UUID): Int {
        val primary = providerRepo.findPrimaryByUserId(userId) ?: return 0
        val downloadedIds = videoRepo.findDownloadedVideoIdsByUserId(userId)
        val backedUpIds = recordRepo.findVideoIdsByProviderId(primary.id).toSet()
        val toBackfill = downloadedIds.filter { it !in backedUpIds }

        for (videoId in toBackfill) {
            createPendingRecord(videoId, primary.id)
        }
        log.info("Backfill queued {} videos for userId={}", toBackfill.size, userId)
        return toBackfill.size
    }

    @EventListener
    fun onVideoDownloadCompleted(event: VideoDownloadCompleted) {
        if (!event.success) return

        val primary = providerRepo.findPrimaryByUserId(event.userId) ?: return
        createPendingRecord(event.videoId, primary.id)
        log.info("Queued backup for newly downloaded video videoId={}", event.videoId)
    }

    fun getStats(userId: UUID): BackupStats {
        val total = recordRepo.countByUserId(userId)
        val backedUp = recordRepo.countByUserIdAndStatus(userId, BackupStatus.BACKED_UP)
        val pending = recordRepo.countByUserIdAndStatus(userId, BackupStatus.PENDING) +
            recordRepo.countByUserIdAndStatus(userId, BackupStatus.UPLOADING)
        val lost = recordRepo.countByUserIdAndStatus(userId, BackupStatus.LOST)
        val failed = recordRepo.countByUserIdAndStatus(userId, BackupStatus.FAILED)
        return BackupStats(total, backedUp, pending, lost, failed)
    }
}

data class BackupStats(
    val total: Long,
    val backedUp: Long,
    val pending: Long,
    val lost: Long,
    val failed: Long
)
