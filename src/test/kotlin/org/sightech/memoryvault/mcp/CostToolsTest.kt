package org.sightech.memoryvault.mcp

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.cost.entity.CostRecord
import org.sightech.memoryvault.cost.service.CostService
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Instant
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CostToolsTest {

    private val costService = mockk<CostService>()
    private val tools = CostTools(costService)

    @Test
    fun `getAwsCosts returns formatted cost summary`() {
        val latest = CostRecord(
            billingDate = LocalDate.of(2026, 4, 15),
            serviceCosts = mapOf(
                "Amazon Elastic Compute Cloud - Compute" to BigDecimal("14.50"),
                "Amazon Relational Database Service" to BigDecimal("12.30"),
                "Amazon Simple Storage Service" to BigDecimal("0.02")
            ),
            totalCostUsd = BigDecimal("26.82"),
            fetchedAt = Instant.now()
        )
        every { costService.getLatestCost() } returns latest
        every { costService.getCostHistory(6) } returns listOf(latest)

        val result = tools.getAwsCosts(6)

        assertNotNull(result)
        assertTrue(result.contains("26.82"), "Expected result to contain total cost")
        assertTrue(result.contains("Amazon Elastic Compute Cloud"), "Expected result to contain service name")
        assertTrue(result.contains("14.50"), "Expected result to contain service cost")
    }

    @Test
    fun `getAwsCosts returns message when no data`() {
        every { costService.getLatestCost() } returns null
        every { costService.getCostHistory(6) } returns emptyList()

        val result = tools.getAwsCosts(6)

        assertTrue(result.contains("No cost data available"), "Expected message when no data available")
    }

    @Test
    fun `getAwsCosts includes monthly history`() {
        val april = CostRecord(
            billingDate = LocalDate.of(2026, 4, 15),
            serviceCosts = mapOf("EC2" to BigDecimal("10.00")),
            totalCostUsd = BigDecimal("10.00"),
            fetchedAt = Instant.now()
        )
        val march = CostRecord(
            billingDate = LocalDate.of(2026, 3, 31),
            serviceCosts = mapOf("EC2" to BigDecimal("28.00")),
            totalCostUsd = BigDecimal("28.00"),
            fetchedAt = Instant.now()
        )
        every { costService.getLatestCost() } returns april
        every { costService.getCostHistory(6) } returns listOf(april, march)

        val result = tools.getAwsCosts(6)

        assertTrue(result.contains("2026-04"), "Expected result to contain April billing month")
        assertTrue(result.contains("2026-03"), "Expected result to contain March billing month")
        assertTrue(result.contains("28.00"), "Expected result to contain March total")
    }
}
