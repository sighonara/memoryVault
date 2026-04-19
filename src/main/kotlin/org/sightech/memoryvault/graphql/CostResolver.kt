package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.cost.entity.CostRecord
import org.sightech.memoryvault.cost.service.CostService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
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
    private val NOISE_THRESHOLD = BigDecimal("0.005")

    @QueryMapping
    fun costs(@Argument months: Int?): CostSummary {
        val effectiveMonths = months ?: 6
        val history = costService.getCostHistory(effectiveMonths)

        val currentMonthPrefix = LocalDate.now().toString().substring(0, 7)
        val currentMonthRecords = history.filter { it.billingDate.toString().startsWith(currentMonthPrefix) }

        val current = if (currentMonthRecords.isNotEmpty()) {
            aggregateMonth(currentMonthRecords, currentMonthPrefix)
        } else null

        val monthlyTotals = history
            .groupBy { it.billingDate.toString().substring(0, 7) }
            .map { (month, records) ->
                val total = records.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.totalCostUsd) }
                MonthlyCost(month = month, totalCostUsd = formatUsd(total))
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

    private fun aggregateMonth(records: List<CostRecord>, monthPrefix: String): CostRecordDto {
        val aggregatedCosts = mutableMapOf<String, BigDecimal>()
        for (record in records) {
            for ((service, cost) in record.serviceCosts) {
                aggregatedCosts[service] = (aggregatedCosts[service] ?: BigDecimal.ZERO).add(cost)
            }
        }
        val filteredCosts = aggregatedCosts.filter { it.value.abs() >= NOISE_THRESHOLD }
        val total = filteredCosts.values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
        val latestFetchedAt = records.maxOf { it.fetchedAt }

        val serviceCostsJson = buildString {
            append("{")
            filteredCosts.entries.joinTo(this) { (k, v) -> "\"$k\":\"${formatUsd(v)}\"" }
            append("}")
        }

        return CostRecordDto(
            id = records.first().id,
            billingDate = "$monthPrefix-01",
            serviceCosts = serviceCostsJson,
            totalCostUsd = formatUsd(total),
            fetchedAt = latestFetchedAt
        )
    }

    private fun toDto(record: CostRecord): CostRecordDto {
        val filteredCosts = record.serviceCosts.filter { it.value.abs() >= NOISE_THRESHOLD }
        val serviceCostsJson = buildString {
            append("{")
            filteredCosts.entries.joinTo(this) { (k, v) -> "\"$k\":\"${formatUsd(v)}\"" }
            append("}")
        }
        return CostRecordDto(
            id = record.id,
            billingDate = record.billingDate.toString(),
            serviceCosts = serviceCostsJson,
            totalCostUsd = formatUsd(record.totalCostUsd),
            fetchedAt = record.fetchedAt
        )
    }

    private fun formatUsd(value: BigDecimal): String =
        value.setScale(2, RoundingMode.HALF_UP).toPlainString()
}
