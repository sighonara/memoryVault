package org.sightech.memoryvault.cost

import org.sightech.memoryvault.cost.service.CostService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("aws")
class CostRefreshTask(private val costService: CostService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 6 * * *")
    fun scheduledRefresh() {
        log.info("Scheduled cost refresh starting")
        try {
            val record = costService.refreshCosts()
            log.info("Scheduled cost refresh complete: total={}", record?.totalCostUsd ?: "no data")
        } catch (e: Exception) {
            log.warn("Scheduled cost refresh failed: {}", e.message, e)
        }
    }
}
