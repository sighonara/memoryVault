package org.sightech.memoryvault.cost.service

import org.sightech.memoryvault.cost.entity.CostRecord
import org.sightech.memoryvault.cost.repository.CostRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.costexplorer.CostExplorerClient
import software.amazon.awssdk.services.costexplorer.model.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Service
@Profile("aws")
class AwsCostService(
    private val ceClient: CostExplorerClient,
    private val repository: CostRecordRepository
) : CostService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun refreshCosts(): CostRecord? {
        val today = LocalDate.now()
        val start = today.withDayOfMonth(1)
        val end = today.plusDays(1)

        val request = GetCostAndUsageRequest.builder()
            .timePeriod(DateInterval.builder()
                .start(start.toString())
                .end(end.toString())
                .build())
            .granularity(Granularity.DAILY)
            .metrics(listOf("UnblendedCost"))
            .groupBy(GroupDefinition.builder()
                .type(GroupDefinitionType.DIMENSION)
                .key("SERVICE")
                .build())
            .build()

        val response = ceClient.getCostAndUsage(request)
        var latestRecord: CostRecord? = null

        for (result in response.resultsByTime()) {
            val date = LocalDate.parse(result.timePeriod().start())
            val serviceCosts = mutableMapOf<String, BigDecimal>()

            for (group in result.groups()) {
                val serviceName = group.keys().firstOrNull() ?: continue
                val amount = group.metrics()["UnblendedCost"]?.amount()
                    ?.toBigDecimalOrNull() ?: continue
                if (amount.compareTo(BigDecimal.ZERO) != 0) {
                    serviceCosts[serviceName] = amount
                }
            }

            val total = serviceCosts.values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
            val existing = repository.findByBillingDate(date)

            val record = if (existing != null) {
                existing.serviceCosts = serviceCosts
                existing.totalCostUsd = total
                existing.fetchedAt = Instant.now()
                existing
            } else {
                CostRecord(
                    billingDate = date,
                    serviceCosts = serviceCosts,
                    totalCostUsd = total,
                    fetchedAt = Instant.now()
                )
            }

            latestRecord = repository.save(record)
        }

        log.info("Cost refresh complete: {} to {}, latest total={}",
            start, end, latestRecord?.totalCostUsd ?: "no data")
        return latestRecord
    }

    override fun getLatestCost(): CostRecord? =
        repository.findFirstByOrderByBillingDateDesc()

    override fun getCostHistory(months: Int): List<CostRecord> {
        val from = LocalDate.now().minusMonths(months.toLong()).withDayOfMonth(1)
        val to = LocalDate.now()
        return repository.findByBillingDateBetweenOrderByBillingDateDesc(from, to)
    }

    override fun getDailyCosts(from: LocalDate, to: LocalDate): List<CostRecord> =
        repository.findByBillingDateBetweenOrderByBillingDateDesc(from, to)
}
