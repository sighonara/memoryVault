package org.sightech.memoryvault.billing

import org.sightech.memoryvault.billing.service.VideoPurgeService
import org.sightech.memoryvault.scheduling.JobScheduler
import org.sightech.memoryvault.scheduling.entity.JobType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class BillingSyncRegistrar(
    private val jobScheduler: JobScheduler,
    private val videoPurgeService: VideoPurgeService,
    @Value("\${memoryvault.billing.video-purge-cron:-}") private val videoPurgeCron: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun registerJobs() {
        jobScheduler.schedule("billing-video-purge", videoPurgeCron, JobType.VIDEO_PURGE) {
            log.info("Video purge job starting")
            videoPurgeService.purge()
        }
    }
}
