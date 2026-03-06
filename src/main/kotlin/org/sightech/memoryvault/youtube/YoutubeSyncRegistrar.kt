package org.sightech.memoryvault.youtube

import org.sightech.memoryvault.scheduling.JobScheduler
import org.sightech.memoryvault.youtube.service.YoutubeListService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class YoutubeSyncRegistrar(
    private val jobScheduler: JobScheduler,
    private val youtubeListService: YoutubeListService,
    @Value("\${memoryvault.youtube.sync-cron:-}") private val syncCron: String
) {

    private val logger = LoggerFactory.getLogger(YoutubeSyncRegistrar::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun registerYoutubeSyncJob() {
        jobScheduler.schedule("youtube-sync", syncCron) {
            logger.info("YouTube sync job starting")
            val results = youtubeListService.refreshList(null)
            val totalNew = results.sumOf { it.newVideos }
            val totalRemoved = results.sumOf { it.removedVideos }
            logger.info("YouTube sync complete: {} lists synced, {} new videos, {} removals detected",
                results.size, totalNew, totalRemoved)
        }
    }
}
