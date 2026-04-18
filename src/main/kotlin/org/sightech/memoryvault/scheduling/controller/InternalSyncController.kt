package org.sightech.memoryvault.scheduling.controller

import kotlinx.coroutines.runBlocking
import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.cost.service.CostService
import org.sightech.memoryvault.feed.service.FeedService
import org.sightech.memoryvault.scheduling.entity.JobType
import org.sightech.memoryvault.scheduling.entity.TriggerSource
import org.sightech.memoryvault.scheduling.service.SyncJobService
import org.sightech.memoryvault.youtube.service.YoutubeListService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("aws")
@RequestMapping("/api/internal/sync")
class InternalSyncController(
    private val feedService: FeedService,
    private val youtubeListService: YoutubeListService,
    private val syncJobService: SyncJobService,
    private val costService: CostService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/feeds")
    fun syncFeeds(): ResponseEntity<Map<String, Any>> {
        log.info("Internal trigger: feed sync")
        val metadata = syncJobService.runTracked(
            JobType.RSS_FETCH,
            TriggerSource.SCHEDULED,
            CurrentUser.SYSTEM_USER_ID
        ) {
            val results = runBlocking { feedService.refreshFeed(null) }
            val totalNew = results.sumOf { it.second }
            log.info("Feed sync complete: {} feeds refreshed, {} new items", results.size, totalNew)
            mapOf("feedsRefreshed" to results.size, "newItems" to totalNew)
        }
        return ResponseEntity.ok(metadata ?: emptyMap())
    }

    @PostMapping("/youtube")
    fun syncYoutube(): ResponseEntity<Map<String, Any>> {
        log.info("Internal trigger: YouTube sync")
        val metadata = syncJobService.runTracked(
            JobType.YT_SYNC,
            TriggerSource.SCHEDULED,
            CurrentUser.SYSTEM_USER_ID
        ) {
            val results = youtubeListService.refreshList(null)
            val totalNew = results.sumOf { it.newVideos }
            val totalRemoved = results.sumOf { it.removedVideos }
            log.info(
                "YouTube sync complete: {} lists synced, {} new videos, {} removals",
                results.size, totalNew, totalRemoved
            )
            mapOf(
                "listsSynced" to results.size,
                "newVideos" to totalNew,
                "removedVideos" to totalRemoved,
                "downloadSuccesses" to results.sumOf { it.downloadSuccesses },
                "downloadFailures" to results.sumOf { it.downloadFailures }
            )
        }
        return ResponseEntity.ok(metadata ?: emptyMap())
    }

    @PostMapping("/costs/refresh")
    fun refreshCosts(): ResponseEntity<Map<String, Any>> {
        log.info("Internal trigger: cost refresh")
        val record = costService.refreshCosts()
        return if (record != null) {
            ResponseEntity.ok(mapOf(
                "billingDate" to record.billingDate.toString(),
                "totalCostUsd" to record.totalCostUsd.toString()
            ))
        } else {
            ResponseEntity.noContent().build()
        }
    }
}
