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
import kotlin.test.assertTrue

class CostResolverTest {

    private val costService = mockk<CostService>()
    private val resolver = CostResolver(costService)

    @Test
    fun `costs aggregates current month and computes monthly totals`() {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val lastMonth = today.minusMonths(1)
        val records = listOf(
            CostRecord(billingDate = today, totalCostUsd = BigDecimal("10.00"),
                serviceCosts = mapOf("EC2" to BigDecimal("7.00"), "S3" to BigDecimal("3.00"))),
            CostRecord(billingDate = yesterday, totalCostUsd = BigDecimal("9.50"),
                serviceCosts = mapOf("EC2" to BigDecimal("6.50"), "S3" to BigDecimal("3.00"))),
            CostRecord(billingDate = lastMonth, totalCostUsd = BigDecimal("28.00"),
                serviceCosts = mapOf("EC2" to BigDecimal("20.00"), "S3" to BigDecimal("8.00")))
        )
        every { costService.getCostHistory(6) } returns records

        val summary = resolver.costs(6)

        assertNotNull(summary.current)
        assertEquals("19.50", summary.current!!.totalCostUsd)
        val serviceCosts = summary.current!!.serviceCosts
        assertTrue(serviceCosts.contains("\"EC2\":\"13.50\""))
        assertTrue(serviceCosts.contains("\"S3\":\"6.00\""))
        assertEquals(2, summary.monthlyTotals.size)
        assertEquals("19.50", summary.monthlyTotals[0].totalCostUsd)
        assertEquals("28.00", summary.monthlyTotals[1].totalCostUsd)
    }

    @Test
    fun `costs filters out near-zero noise from service costs`() {
        val today = LocalDate.now()
        val records = listOf(
            CostRecord(billingDate = today, totalCostUsd = BigDecimal("10.00"),
                serviceCosts = mapOf(
                    "EC2" to BigDecimal("10.00"),
                    "Data Transfer" to BigDecimal("0.000000243"),
                    "ECR" to BigDecimal("-0.0000000004")
                ))
        )
        every { costService.getCostHistory(6) } returns records

        val summary = resolver.costs(6)

        assertNotNull(summary.current)
        assertEquals("10.00", summary.current!!.totalCostUsd)
        assertTrue(summary.current!!.serviceCosts.contains("EC2"))
        assertTrue(!summary.current!!.serviceCosts.contains("Data Transfer"))
        assertTrue(!summary.current!!.serviceCosts.contains("ECR"))
    }

    @Test
    fun `costs returns empty summary when no data`() {
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
        assertEquals("31.24", result!!.totalCostUsd)
        verify { costService.refreshCosts() }
    }
}
