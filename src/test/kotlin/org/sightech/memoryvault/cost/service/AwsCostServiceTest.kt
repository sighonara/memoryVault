package org.sightech.memoryvault.cost.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.cost.entity.CostRecord
import org.sightech.memoryvault.cost.repository.CostRecordRepository
import software.amazon.awssdk.services.costexplorer.CostExplorerClient
import software.amazon.awssdk.services.costexplorer.model.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AwsCostServiceTest {

    private val ceClient = mockk<CostExplorerClient>()
    private val repository = mockk<CostRecordRepository>(relaxed = true)
    private lateinit var service: AwsCostService

    @BeforeEach
    fun setUp() {
        service = AwsCostService(ceClient, repository)
    }

    @Test
    fun `refreshCosts fetches from Cost Explorer and saves per-day records`() {
        val today = LocalDate.now()
        val response = buildCostResponse(
            today.toString(),
            listOf(
                "Amazon Elastic Compute Cloud - Compute" to "5.5000",
                "Amazon Simple Storage Service" to "1.2000",
                "AWS Data Transfer" to "0.3000"
            )
        )

        every { ceClient.getCostAndUsage(any<GetCostAndUsageRequest>()) } returns response
        every { repository.findByBillingDate(today) } returns null
        every { repository.save(any()) } answers { firstArg() }

        val result = service.refreshCosts()

        assertNotNull(result)
        assertEquals(today, result.billingDate)
        assertEquals(BigDecimal("5.5000"), result.serviceCosts["Amazon Elastic Compute Cloud - Compute"])
        assertEquals(BigDecimal("1.2000"), result.serviceCosts["Amazon Simple Storage Service"])
        assertEquals(BigDecimal("0.3000"), result.serviceCosts["AWS Data Transfer"])
        assertEquals(BigDecimal("7.0000"), result.totalCostUsd)
        verify { repository.save(any()) }
    }

    @Test
    fun `refreshCosts upserts existing record for same billing date`() {
        val today = LocalDate.now()
        val existing = CostRecord(
            billingDate = today,
            serviceCosts = mapOf("EC2" to BigDecimal("3.00")),
            totalCostUsd = BigDecimal("3.00")
        )

        val response = buildCostResponse(
            today.toString(),
            listOf("Amazon Elastic Compute Cloud - Compute" to "5.5000")
        )

        every { ceClient.getCostAndUsage(any<GetCostAndUsageRequest>()) } returns response
        every { repository.findByBillingDate(today) } returns existing
        every { repository.save(any()) } answers { firstArg() }

        val result = service.refreshCosts()

        assertNotNull(result)
        assertEquals(existing.id, result.id)
        assertEquals(BigDecimal("5.5000"), result.totalCostUsd)
    }

    @Test
    fun `refreshCosts sends correct date range to Cost Explorer`() {
        val requestSlot = slot<GetCostAndUsageRequest>()
        val emptyResponse = buildCostResponse(LocalDate.now().toString(), emptyList())

        every { ceClient.getCostAndUsage(capture(requestSlot)) } returns emptyResponse
        every { repository.findByBillingDate(any()) } returns null
        every { repository.save(any()) } answers { firstArg() }

        service.refreshCosts()

        val request = requestSlot.captured
        val today = LocalDate.now()
        assertEquals(today.withDayOfMonth(1).toString(), request.timePeriod().start())
        assertEquals(today.plusDays(1).toString(), request.timePeriod().end())
        assertEquals(Granularity.DAILY, request.granularity())
    }

    @Test
    fun `getLatestCost delegates to repository`() {
        every { repository.findFirstByOrderByBillingDateDesc() } returns null
        assertNull(service.getLatestCost())
        verify { repository.findFirstByOrderByBillingDateDesc() }
    }

    @Test
    fun `getCostHistory queries correct date range`() {
        every { repository.findByBillingDateBetweenOrderByBillingDateDesc(any(), any()) } returns emptyList()
        val result = service.getCostHistory(3)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getDailyCosts delegates to repository`() {
        val from = LocalDate.of(2026, 4, 1)
        val to = LocalDate.of(2026, 4, 15)
        every { repository.findByBillingDateBetweenOrderByBillingDateDesc(from, to) } returns emptyList()
        val result = service.getDailyCosts(from, to)
        assertTrue(result.isEmpty())
    }

    private fun buildCostResponse(date: String, services: List<Pair<String, String>>): GetCostAndUsageResponse {
        val groups = services.map { (name, amount) ->
            Group.builder()
                .keys(listOf(name))
                .metrics(mapOf("UnblendedCost" to MetricValue.builder().amount(amount).unit("USD").build()))
                .build()
        }
        return GetCostAndUsageResponse.builder()
            .resultsByTime(listOf(
                ResultByTime.builder()
                    .timePeriod(DateInterval.builder().start(date).end(date).build())
                    .groups(groups)
                    .build()
            ))
            .build() as GetCostAndUsageResponse
    }
}
