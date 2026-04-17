package org.sightech.memoryvault.cost.service

import org.sightech.memoryvault.cost.entity.CostRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
@Profile("local | test")
class LocalCostService : CostService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun refreshCosts(): CostRecord? {
        log.debug("Cost tracking not available in local profile")
        return null
    }

    override fun getLatestCost(): CostRecord? = null

    override fun getCostHistory(months: Int): List<CostRecord> = emptyList()

    override fun getDailyCosts(from: LocalDate, to: LocalDate): List<CostRecord> = emptyList()
}
