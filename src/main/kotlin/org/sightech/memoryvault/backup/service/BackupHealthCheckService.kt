package org.sightech.memoryvault.backup.service

import org.sightech.memoryvault.backup.entity.BackupProviderEntity
import org.sightech.memoryvault.backup.entity.BackupStatus
import org.sightech.memoryvault.backup.provider.BackupProviderFactory
import org.sightech.memoryvault.backup.repository.BackupProviderRepository
import org.sightech.memoryvault.backup.repository.BackupRecordRepository
import org.sightech.memoryvault.websocket.BackupLost
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class BackupHealthCheckService(
    private val recordRepo: BackupRecordRepository,
    private val providerRepo: BackupProviderRepository,
    private val providerFactory: BackupProviderFactory,
    private val backupService: BackupService,
    private val eventPublisher: ApplicationEventPublisher,
    @Value("\${memoryvault.backup.health-check-failure-threshold:3}") private val failureThreshold: Int
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun runHealthChecks(): Map<String, Any> {
        val records = recordRepo.findAllBackedUp()
        var checked = 0
        var lost = 0

        val providerCache = mutableMapOf<UUID, BackupProviderEntity>()

        for (record in records) {
            val externalUrl = record.externalUrl ?: continue
            val providerEntity = providerCache[record.providerId]
                ?: providerRepo.findById(record.providerId).orElse(null)?.also { providerCache[record.providerId] = it }
                ?: continue

            try {
                val provider = providerFactory.create(providerEntity)
                val healthy = provider.checkHealth(externalUrl)
                checked++

                record.lastHealthCheckAt = Instant.now()

                if (healthy) {
                    record.healthCheckFailures = 0
                } else {
                    record.healthCheckFailures++
                    log.warn("Health check failed videoId={} failures={}/{}", record.videoId, record.healthCheckFailures, failureThreshold)

                    if (record.healthCheckFailures >= failureThreshold) {
                        record.status = BackupStatus.LOST
                        lost++
                        log.warn("Backup marked LOST videoId={} externalUrl={}", record.videoId, externalUrl)

                        eventPublisher.publishEvent(BackupLost(
                            userId = providerEntity.userId,
                            timestamp = Instant.now(),
                            videoId = record.videoId,
                            providerId = record.providerId,
                            externalUrl = externalUrl
                        ))

                        triggerSecondaryFailover(record.videoId, providerEntity)
                    }
                }

                record.updatedAt = Instant.now()
                recordRepo.save(record)
            } catch (e: Exception) {
                log.warn("Health check error videoId={}: {}", record.videoId, e.message)
            }
        }

        log.info("Health check complete: checked={} lost={}", checked, lost)
        return mapOf("checked" to checked, "lost" to lost)
    }

    private fun triggerSecondaryFailover(videoId: UUID, lostProvider: BackupProviderEntity) {
        val allProviders = providerRepo.findAllActiveByUserId(lostProvider.userId)
        val secondary = allProviders.find { it.id != lostProvider.id && !it.isPrimary }
        if (secondary != null) {
            backupService.createPendingRecord(videoId, secondary.id)
            log.info("Secondary failover queued videoId={} secondaryProviderId={}", videoId, secondary.id)
        }
    }
}
