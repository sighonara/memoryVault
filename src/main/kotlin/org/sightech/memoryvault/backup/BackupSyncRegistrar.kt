package org.sightech.memoryvault.backup

import org.sightech.memoryvault.backup.entity.BackupStatus
import org.sightech.memoryvault.backup.repository.BackupProviderRepository
import org.sightech.memoryvault.backup.repository.BackupRecordRepository
import org.sightech.memoryvault.backup.service.BackupService
import org.sightech.memoryvault.backup.service.BackupHealthCheckService
import org.sightech.memoryvault.scheduling.JobScheduler
import org.sightech.memoryvault.scheduling.entity.JobType
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class BackupSyncRegistrar(
    private val jobScheduler: JobScheduler,
    private val recordRepo: BackupRecordRepository,
    private val providerRepo: BackupProviderRepository,
    private val videoRepo: VideoRepository,
    private val backupService: BackupService,
    private val healthCheckService: BackupHealthCheckService,
    @Value("\${memoryvault.backup.upload-cron:-}") private val uploadCron: String,
    @Value("\${memoryvault.backup.health-check-cron:-}") private val healthCheckCron: String,
    @Value("\${memoryvault.backup.max-uploads-per-day:10}") private val maxUploadsPerDay: Int
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun registerBackupJobs() {
        jobScheduler.schedule("backup-upload", uploadCron, JobType.BACKUP_UPLOAD) {
            log.info("Backup upload job starting")
            val pending = recordRepo.findByStatus(BackupStatus.PENDING)
            val uploadsPerRun = (maxUploadsPerDay / 4).coerceAtLeast(1)
            val batch = pending.take(uploadsPerRun)

            var uploaded = 0
            var failed = 0
            for (record in batch) {
                val provider = providerRepo.findById(record.providerId).orElse(null) ?: continue
                val video = videoRepo.findById(record.videoId).orElse(null) ?: continue
                val filePath = video.filePath ?: continue

                backupService.processUpload(record, provider, video.youtubeVideoId, filePath)
                if (record.status == BackupStatus.BACKED_UP) uploaded++ else failed++
            }

            log.info("Backup upload job complete: uploaded={} failed={} remaining={}", uploaded, failed, pending.size - batch.size)
            mapOf("uploaded" to uploaded, "failed" to failed, "remaining" to (pending.size - batch.size))
        }

        jobScheduler.schedule("backup-health-check", healthCheckCron, JobType.BACKUP_HEALTH_CHECK) {
            log.info("Backup health check job starting")
            val result = healthCheckService.runHealthChecks()
            log.info("Backup health check job complete: {}", result)
            @Suppress("UNCHECKED_CAST")
            result as Map<String, Any>
        }
    }
}
