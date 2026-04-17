package org.sightech.memoryvault.cost.service

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalCostServiceTest {

    private val service = LocalCostService()

    @Test
    fun `refreshCosts returns null`() {
        assertNull(service.refreshCosts())
    }

    @Test
    fun `getLatestCost returns null`() {
        assertNull(service.getLatestCost())
    }

    @Test
    fun `getCostHistory returns empty list`() {
        assertTrue(service.getCostHistory(6).isEmpty())
    }

    @Test
    fun `getDailyCosts returns empty list`() {
        assertTrue(service.getDailyCosts(LocalDate.now().minusDays(7), LocalDate.now()).isEmpty())
    }
}
