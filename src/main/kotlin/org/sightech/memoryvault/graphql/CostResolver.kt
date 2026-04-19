package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.cost.service.CostService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.util.UUID

data class CostRecordDto(
    val id: UUID,
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

        return CostSummary(current = current, monthlyTotals = monthlyTotals)
    }

    @MutationMapping
    fun refreshCosts(): CostRecordDto? {
        val record = costService.refreshCosts()
        log.info("Cost refresh complete: record={}", record != null)
        return record?.let { toDto(it) }
    }

    private fun toDto(record: org.sightech.memoryvault.cost.entity.CostRecord): CostRecordDto {
        return CostRecordDto(
            id = record.id,
            billingDate = record.billingDate.toString(),
            serviceCosts = objectMapper.writeValueAsString(record.serviceCosts),
            totalCostUsd = record.totalCostUsd.toPlainString(),
            fetchedAt = record.fetchedAt
        )
    }
}
