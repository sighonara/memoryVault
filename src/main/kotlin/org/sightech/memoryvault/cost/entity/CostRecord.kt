package org.sightech.memoryvault.cost.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "cost_records")
class CostRecord(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "billing_date", nullable = false, unique = true)
    val billingDate: LocalDate,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "service_costs", nullable = false, columnDefinition = "jsonb")
    var serviceCosts: Map<String, BigDecimal> = emptyMap(),

    @Column(name = "total_cost_usd", nullable = false)
    var totalCostUsd: BigDecimal = BigDecimal.ZERO,

    @Column(name = "fetched_at", nullable = false)
    var fetchedAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Version
    val version: Long = 0
)
