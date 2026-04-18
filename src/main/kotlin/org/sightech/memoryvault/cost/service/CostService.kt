package org.sightech.memoryvault.cost.service

import org.sightech.memoryvault.cost.entity.CostRecord
import java.time.LocalDate

interface CostService {
    fun refreshCosts(): CostRecord?
    fun getLatestCost(): CostRecord?
    fun getCostHistory(months: Int): List<CostRecord>
    fun getDailyCosts(from: LocalDate, to: LocalDate): List<CostRecord>
}
