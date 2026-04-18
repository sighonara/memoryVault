package org.sightech.memoryvault.graphql

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.cost.entity.CostRecord
import org.sightech.memoryvault.cost.service.CostService
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CostResolverTest {

    private val costService = mockk<CostService>()
    private val resolver = CostResolver(costService)

    @Test
    fun `costs returns summary with current and monthly totals`() {
        val records = listOf(
            CostRecord(billingDate = LocalDate.of(2026, 4, 15), totalCostUsd = BigDecimal("10.00"),
                serviceCosts = mapOf("EC2" to BigDecimal("7.00"), "S3" to BigDecimal("3.00"))),
            CostRecord(billingDate = LocalDate.of(2026, 4, 14), totalCostUsd = BigDecimal("9.50"),
                serviceCosts = mapOf("EC2" to BigDecimal("6.50"), "S3" to BigDecimal("3.00"))),
            CostRecord(billingDate = LocalDate.of(2026, 3, 31), totalCostUsd = BigDecimal("28.00"),
                serviceCosts = mapOf("EC2" to BigDecimal("20.00"), "S3" to BigDecimal("8.00")))
        )
        every { costService.getLatestCost() } returns records[0]
        every { costService.getCostHistory(6) } returns records

        val summary = resolver.costs(6)

        assertNotNull(summary.current)
        assertEquals("10.00", summary.current!!.totalCostUsd)
        assertEquals(2, summary.monthlyTotals.size)
        assertEquals("2026-04", summary.monthlyTotals[0].month)
        assertEquals("19.50", summary.monthlyTotals[0].totalCostUsd)
        assertEquals("2026-03", summary.monthlyTotals[1].month)
        assertEquals("28.00", summary.monthlyTotals[1].totalCostUsd)
    }

    @Test
    fun `costs returns empty summary when no data`() {
        every { costService.getLatestCost() } returns null
        every { costService.getCostHistory(6) } returns emptyList()

        val summary = resolver.costs(6)

        assertNull(summary.current)
        assertEquals(0, summary.monthlyTotals.size)
    }

    @Test
    fun `refreshCosts delegates to service`() {
        val record = CostRecord(billingDate = LocalDate.now(), totalCostUsd = BigDecimal("31.24"))
        every { costService.refreshCosts() } returns record

        val result = resolver.refreshCosts()

        assertNotNull(result)
        verify { costService.refreshCosts() }
    }
}
