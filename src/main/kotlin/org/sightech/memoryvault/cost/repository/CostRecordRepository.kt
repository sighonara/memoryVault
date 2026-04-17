package org.sightech.memoryvault.cost.repository

import org.sightech.memoryvault.cost.entity.CostRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface CostRecordRepository : JpaRepository<CostRecord, UUID> {
    fun findFirstByOrderByBillingDateDesc(): CostRecord?
    fun findByBillingDateBetweenOrderByBillingDateDesc(from: LocalDate, to: LocalDate): List<CostRecord>
    fun findByBillingDate(date: LocalDate): CostRecord?
}
