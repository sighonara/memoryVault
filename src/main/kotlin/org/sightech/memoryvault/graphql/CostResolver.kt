package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.cost.service.CostService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal

data class CostRecordDto(
    val id: String,
    val billingDate: String,
    val serviceCosts: String,
    val totalCostUsd: String,
    val fetchedAt: java.time.Instant
)

data class MonthlyCost(
    val month: String,
    val totalCostUsd: String
)

data class CostSummary(
    val current: CostRecordDto?,
    val monthlyTotals: List<MonthlyCost>
)

@Controller
class CostResolver(
    private val costService: CostService
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()

    @QueryMapping
    fun costs(@Argument months: Int?): CostSummary {
        try {
            val effectiveMonths = months ?: 6
            val latest = costService.getLatestCost()
            val history = costService.getCostHistory(effectiveMonths)

            val current = latest?.let { toDto(it) }

            val monthlyTotals = history
                .groupBy { it.billingDate.toString().substring(0, 7) }
                .map { (month, records) ->
                    val total = records.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.totalCostUsd) }
                    MonthlyCost(month = month, totalCostUsd = total.toPlainString())
                }
                .sortedByDescending { it.month }

            log.info("Costs query: current={} monthlyTotals={}", current != null, monthlyTotals.size)
            return CostSummary(current = current, monthlyTotals = monthlyTotals)
        } catch (e: Exception) {
            log.error("Exception in costs query: {}", e.message, e)
            throw e
        }
    }

    @MutationMapping
    fun refreshCosts(): CostRecordDto? {
        try {
            val record = costService.refreshCosts()
            log.info("Costs refresh: record={}", record != null)
            return record?.let { toDto(it) }
        } catch (e: Exception) {
            log.error("Exception in refreshCosts: {}", e.message, e)
            throw e
        }
    }

    private fun toDto(record: org.sightech.memoryvault.cost.entity.CostRecord): CostRecordDto {
        return CostRecordDto(
            id = record.id.toString(),
            billingDate = record.billingDate.toString(),
            serviceCosts = objectMapper.writeValueAsString(record.serviceCosts),
            totalCostUsd = record.totalCostUsd.toPlainString(),
            fetchedAt = record.fetchedAt
        )
    }
}
