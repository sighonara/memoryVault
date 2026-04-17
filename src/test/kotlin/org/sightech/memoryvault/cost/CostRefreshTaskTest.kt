package org.sightech.memoryvault.cost

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.cost.entity.CostRecord
import org.sightech.memoryvault.cost.service.CostService
import java.math.BigDecimal
import java.time.LocalDate

class CostRefreshTaskTest {

    private val costService = mockk<CostService>()
    private val task = CostRefreshTask(costService)

    @Test
    fun `scheduledRefresh calls refreshCosts`() {
        val record = CostRecord(
            billingDate = LocalDate.now(),
            totalCostUsd = BigDecimal("31.24")
        )
        every { costService.refreshCosts() } returns record

        task.scheduledRefresh()

        verify(exactly = 1) { costService.refreshCosts() }
    }

    @Test
    fun `scheduledRefresh does not throw when refreshCosts fails`() {
        every { costService.refreshCosts() } throws RuntimeException("Cost Explorer down")

        task.scheduledRefresh()

        verify(exactly = 1) { costService.refreshCosts() }
    }
}
