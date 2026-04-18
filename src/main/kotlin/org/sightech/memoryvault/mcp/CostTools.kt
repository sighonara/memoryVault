package org.sightech.memoryvault.mcp

import org.sightech.memoryvault.cost.service.CostService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class CostTools(private val costService: CostService) {

    @Tool(description = "Get current cloud cost summary and monthly history. Use when the user asks about cloud spending, billing, or cost trends.")
    fun getAwsCosts(
        @ToolParam(description = "Number of months of history to include (default 6)") months: Int = 6
    ): String {
        val latest = costService.getLatestCost()
            ?: return "No cost data available. Cost tracking may not be configured or data hasn't been fetched yet."

        val sb = StringBuilder()
        sb.appendLine("## Current Cost (${latest.billingDate})")
        sb.appendLine("Total: \$${latest.totalCostUsd}")
        sb.appendLine()
        sb.appendLine("Per-service breakdown:")
        latest.serviceCosts.entries
            .sortedByDescending { it.value }
            .forEach { (service, cost) ->
                sb.appendLine("  $service: \$${cost}")
            }
        sb.appendLine()
        sb.appendLine("Last updated: ${latest.fetchedAt}")

        val history = costService.getCostHistory(months.coerceIn(1, 24))
        if (history.isNotEmpty()) {
            val monthlyTotals = history
                .groupBy { it.billingDate.toString().substring(0, 7) }
                .map { (month, records) ->
                    val total = records.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.totalCostUsd) }
                    month to total
                }
                .sortedByDescending { it.first }

            sb.appendLine()
            sb.appendLine("## Monthly History")
            monthlyTotals.forEach { (month, total) ->
                sb.appendLine("  $month: \$${total}")
            }
        }

        return sb.toString()
    }
}
