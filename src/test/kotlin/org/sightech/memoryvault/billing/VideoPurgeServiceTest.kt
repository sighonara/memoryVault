package org.sightech.memoryvault.billing

import io.mockk.*
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.entity.SubscriptionEntity
import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.sightech.memoryvault.billing.service.VideoPurgeService
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.youtube.entity.Video
import org.sightech.memoryvault.youtube.repository.VideoRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class VideoPurgeServiceTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val videoRepository = mockk<VideoRepository>(relaxed = true)
    private val storageService = mockk<StorageService>(relaxed = true)
    private val service = VideoPurgeService(subscriptionRepository, videoRepository, storageService)

    @Test
    fun `purges video files for expired grace periods`() {
        val userId = UUID.randomUUID()
        val sub = SubscriptionEntity(
            userId = userId,
            stripeCustomerId = "cus_123",
            stripeSubscriptionId = "sub_grace",
            billingInterval = BillingInterval.MONTHLY,
            status = SubscriptionStatus.GRACE_PERIOD,
            gracePeriodEndsAt = Instant.now().minusSeconds(3600)
        )

        val video = mockk<Video>(relaxed = true)
        every { video.filePath } returns "videos/old.mp4"

        every { subscriptionRepository.findExpiredGracePeriods(any()) } returns listOf(sub)
        every { videoRepository.findAllWithFilePathByUserId(userId) } returns listOf(video)
        every { videoRepository.save(any()) } returnsArgument 0
        every { subscriptionRepository.save(any()) } returnsArgument 0

        val result = service.purge()

        verify { storageService.delete("videos/old.mp4") }
        verify { video.filePath = null }
        verify { videoRepository.save(video) }
        assertEquals(1, result["purged"])
    }

    @Test
    fun `does not purge when no expired grace periods`() {
        every { subscriptionRepository.findExpiredGracePeriods(any()) } returns emptyList()

        service.purge()

        verify(exactly = 0) { storageService.delete(any()) }
    }
}
