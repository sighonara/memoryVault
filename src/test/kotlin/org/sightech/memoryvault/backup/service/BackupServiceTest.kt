package org.sightech.memoryvault.backup.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.backup.entity.BackupProviderEntity
import org.sightech.memoryvault.backup.entity.BackupProviderType
import org.sightech.memoryvault.backup.entity.BackupRecord
import org.sightech.memoryvault.backup.entity.BackupStatus
import org.sightech.memoryvault.backup.provider.BackupProvider
import org.sightech.memoryvault.backup.provider.BackupProviderFactory
import org.sightech.memoryvault.backup.provider.BackupSearchResult
import org.sightech.memoryvault.backup.provider.BackupUploadResult
import org.sightech.memoryvault.backup.repository.BackupProviderRepository
import org.sightech.memoryvault.backup.repository.BackupRecordRepository
import org.sightech.memoryvault.crypto.EncryptionService
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.youtube.repository.VideoRepository
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.test.assertEquals

class BackupServiceTest {

    private val providerRepo = mockk<BackupProviderRepository>()
    private val recordRepo = mockk<BackupRecordRepository>()
    private val videoRepo = mockk<VideoRepository>()
    private val providerFactory = mockk<BackupProviderFactory>()
    private val storageService = mockk<StorageService>()
    private val encryptionService = mockk<EncryptionService>()

    private val service = BackupService(providerRepo, recordRepo, videoRepo, providerFactory, storageService, encryptionService)

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val providerId = UUID.randomUUID()
    private val videoId = UUID.randomUUID()

    private val providerEntity = BackupProviderEntity(
        id = providerId,
        userId = userId,
        type = BackupProviderType.INTERNET_ARCHIVE,
        name = "Internet Archive",
        credentialsEncrypted = "encrypted-creds"
    )

    @BeforeEach
    fun setUp() {
        every { recordRepo.save(any()) } answers { firstArg() }
        every { providerRepo.save(any()) } answers { firstArg() }
    }

    @Test
    fun `createPendingRecord creates a PENDING backup record`() {
        every { recordRepo.findByVideoIdAndProviderId(videoId, providerId) } returns null

        val record = service.createPendingRecord(videoId, providerId)

        assertEquals(BackupStatus.PENDING, record.status)
        assertEquals(videoId, record.videoId)
        assertEquals(providerId, record.providerId)
        verify { recordRepo.save(any()) }
    }

    @Test
    fun `createPendingRecord returns existing record if already present`() {
        val existing = BackupRecord(videoId = videoId, providerId = providerId, status = BackupStatus.BACKED_UP)
        every { recordRepo.findByVideoIdAndProviderId(videoId, providerId) } returns existing

        val record = service.createPendingRecord(videoId, providerId)

        assertEquals(BackupStatus.BACKED_UP, record.status)
        verify(exactly = 0) { recordRepo.save(any()) }
    }

    @Test
    fun `processUpload marks BACKED_UP when IA already has the video`() {
        val record = BackupRecord(videoId = videoId, providerId = providerId)
        val mockProvider = mockk<BackupProvider>()
        every { providerFactory.create(providerEntity) } returns mockProvider
        every { mockProvider.search("ytVid123") } returns BackupSearchResult("yt-ytVid123", "https://archive.org/details/yt-ytVid123")

        service.processUpload(record, providerEntity, "ytVid123", "videos/path/file.mp4")

        assertEquals(BackupStatus.BACKED_UP, record.status)
        assertEquals("https://archive.org/details/yt-ytVid123", record.externalUrl)
        verify(exactly = 0) { storageService.retrieve(any()) }
        verify { recordRepo.save(record) }
    }

    @Test
    fun `processUpload uploads and marks BACKED_UP when not found on IA`() {
        val record = BackupRecord(videoId = videoId, providerId = providerId)
        val mockProvider = mockk<BackupProvider>()
        every { providerFactory.create(providerEntity) } returns mockProvider
        every { mockProvider.search("ytVid123") } returns null
        every { storageService.retrieve("videos/path/file.mp4") } returns ByteArrayInputStream(byteArrayOf(1, 2, 3))
        every { mockProvider.upload(any(), any()) } returns BackupUploadResult("yt-ytVid123", "https://archive.org/details/yt-ytVid123")
        every { videoRepo.findById(videoId) } returns java.util.Optional.empty()

        service.processUpload(record, providerEntity, "ytVid123", "videos/path/file.mp4")

        assertEquals(BackupStatus.BACKED_UP, record.status)
        assertEquals("yt-ytVid123", record.externalId)
        verify { storageService.retrieve("videos/path/file.mp4") }
        verify { recordRepo.save(record) }
    }

    @Test
    fun `processUpload marks FAILED on exception`() {
        val record = BackupRecord(videoId = videoId, providerId = providerId)
        val mockProvider = mockk<BackupProvider>()
        every { providerFactory.create(providerEntity) } returns mockProvider
        every { mockProvider.search("ytVid123") } throws RuntimeException("Network error")

        service.processUpload(record, providerEntity, "ytVid123", "videos/path/file.mp4")

        assertEquals(BackupStatus.FAILED, record.status)
        assertEquals("Network error", record.errorMessage)
        verify { recordRepo.save(record) }
    }

    @Test
    fun `addProvider encrypts credentials and saves`() {
        every { encryptionService.encrypt(any()) } returns "encrypted-json"

        val provider = service.addProvider(userId, BackupProviderType.INTERNET_ARCHIVE, "Internet Archive", """{"accessKey":"a","secretKey":"b"}""", true)

        assertEquals("encrypted-json", provider.credentialsEncrypted)
        assertEquals("Internet Archive", provider.name)
        verify { encryptionService.encrypt("""{"accessKey":"a","secretKey":"b"}""") }
        verify { providerRepo.save(any()) }
    }

    @Test
    fun `backfillCount returns count of downloaded videos without backup records`() {
        val allDownloaded = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        val alreadyBacked = listOf(allDownloaded[0])
        every { providerRepo.findPrimaryByUserId(userId) } returns providerEntity
        every { videoRepo.findDownloadedVideoIdsByUserId(userId) } returns allDownloaded
        every { recordRepo.findVideoIdsByProviderId(providerId) } returns alreadyBacked

        val count = service.backfillCount(userId)

        assertEquals(2, count)
    }
}
