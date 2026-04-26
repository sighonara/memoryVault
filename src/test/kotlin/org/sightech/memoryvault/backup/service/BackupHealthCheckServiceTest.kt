package org.sightech.memoryvault.backup.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.backup.entity.*
import org.sightech.memoryvault.backup.provider.BackupProvider
import org.sightech.memoryvault.backup.provider.BackupProviderFactory
import org.sightech.memoryvault.backup.repository.BackupProviderRepository
import org.sightech.memoryvault.backup.repository.BackupRecordRepository
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class BackupHealthCheckServiceTest {

    private val recordRepo = mockk<BackupRecordRepository>()
    private val providerRepo = mockk<BackupProviderRepository>()
    private val providerFactory = mockk<BackupProviderFactory>()
    private val backupService = mockk<BackupService>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val service = BackupHealthCheckService(recordRepo, providerRepo, providerFactory, backupService, eventPublisher, 3)

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val providerId = UUID.randomUUID()
    private val videoId = UUID.randomUUID()

    private val providerEntity = BackupProviderEntity(
        id = providerId, userId = userId,
        type = BackupProviderType.INTERNET_ARCHIVE,
        name = "IA", credentialsEncrypted = "enc"
    )

    @BeforeEach
    fun setUp() {
        every { recordRepo.save(any()) } answers { firstArg() }
        every { providerRepo.findById(providerId) } returns Optional.of(providerEntity)
    }

    @Test
    fun `healthy check resets failure count`() {
        val record = BackupRecord(videoId = videoId, providerId = providerId, status = BackupStatus.BACKED_UP).apply {
            externalUrl = "https://archive.org/details/yt-abc"
            healthCheckFailures = 2
        }
        every { recordRepo.findAllBackedUp() } returns listOf(record)
        val mockProvider = mockk<BackupProvider>()
        every { providerFactory.create(providerEntity) } returns mockProvider
        every { mockProvider.checkHealth("https://archive.org/details/yt-abc") } returns true

        val result = service.runHealthChecks()

        assertEquals(0, record.healthCheckFailures)
        assertEquals(1, result["checked"] as Int)
        assertEquals(0, result["lost"] as Int)
    }

    @Test
    fun `failure increments count but does not mark LOST below threshold`() {
        val record = BackupRecord(videoId = videoId, providerId = providerId, status = BackupStatus.BACKED_UP).apply {
            externalUrl = "https://archive.org/details/yt-abc"
            healthCheckFailures = 1
        }
        every { recordRepo.findAllBackedUp() } returns listOf(record)
        val mockProvider = mockk<BackupProvider>()
        every { providerFactory.create(providerEntity) } returns mockProvider
        every { mockProvider.checkHealth("https://archive.org/details/yt-abc") } returns false

        service.runHealthChecks()

        assertEquals(2, record.healthCheckFailures)
        assertEquals(BackupStatus.BACKED_UP, record.status)
    }

    @Test
    fun `failure at threshold marks LOST and publishes event`() {
        val record = BackupRecord(videoId = videoId, providerId = providerId, status = BackupStatus.BACKED_UP).apply {
            externalUrl = "https://archive.org/details/yt-abc"
            healthCheckFailures = 2
        }
        every { recordRepo.findAllBackedUp() } returns listOf(record)
        val mockProvider = mockk<BackupProvider>()
        every { providerFactory.create(providerEntity) } returns mockProvider
        every { mockProvider.checkHealth("https://archive.org/details/yt-abc") } returns false
        every { providerRepo.findAllActiveByUserId(userId) } returns listOf(providerEntity)

        service.runHealthChecks()

        assertEquals(BackupStatus.LOST, record.status)
        verify { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `LOST record triggers secondary failover if secondary provider exists`() {
        val record = BackupRecord(videoId = videoId, providerId = providerId, status = BackupStatus.BACKED_UP).apply {
            externalUrl = "https://archive.org/details/yt-abc"
            healthCheckFailures = 2
        }
        val secondaryProvider = BackupProviderEntity(
            userId = userId, type = BackupProviderType.CUSTOM,
            name = "Secondary", credentialsEncrypted = "enc", isPrimary = false
        )
        every { recordRepo.findAllBackedUp() } returns listOf(record)
        val mockProvider = mockk<BackupProvider>()
        every { providerFactory.create(providerEntity) } returns mockProvider
        every { mockProvider.checkHealth("https://archive.org/details/yt-abc") } returns false
        every { providerRepo.findAllActiveByUserId(userId) } returns listOf(providerEntity, secondaryProvider)
        every { backupService.createPendingRecord(videoId, secondaryProvider.id) } returns mockk()

        service.runHealthChecks()

        verify { backupService.createPendingRecord(videoId, secondaryProvider.id) }
    }
}
