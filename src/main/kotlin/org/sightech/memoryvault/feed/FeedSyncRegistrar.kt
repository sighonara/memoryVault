package org.sightech.memoryvault.feed

import kotlinx.coroutines.runBlocking
import org.sightech.memoryvault.feed.service.FeedService
import org.sightech.memoryvault.scheduling.JobScheduler
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class FeedSyncRegistrar(
    private val jobScheduler: JobScheduler,
    private val feedService: FeedService,
    @Value("\${memoryvault.feeds.sync-cron:-}") private val syncCron: String
) {

    private val logger = LoggerFactory.getLogger(FeedSyncRegistrar::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun registerFeedSyncJob() {
        jobScheduler.schedule("feed-sync", syncCron) {
            logger.info("Feed sync job starting")
            val results = runBlocking { feedService.refreshFeed(null) }
            val totalNew = results.sumOf { it.second }
            logger.info("Feed sync complete: {} feeds refreshed, {} new items", results.size, totalNew)
        }
    }
}
