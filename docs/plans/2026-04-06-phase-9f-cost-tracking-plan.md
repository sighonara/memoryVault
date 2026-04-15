# Phase 9F — Cost Tracking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Track AWS costs per billing cycle, store history, and expose via MCP tool + internal refresh endpoint.

**Architecture:** A `CostService` interface with AWS and local profiles. The AWS implementation uses Cost Explorer SDK to fetch monthly costs grouped by service, maps them to compute/storage/transfer buckets, and persists as `AwsCostRecord` entities. A daily scheduled refresh runs at 6 AM (AWS cost data lags ~24h). An internal endpoint allows on-demand refresh.

**Tech Stack:** AWS SDK v2 (Cost Explorer), Spring Boot (Kotlin), Flyway, Spring AI MCP

**Design Spec:** `docs/plans/2026-04-03-phase-9-infrastructure-design.md` § 9F

---

### Task 1: Add Cost Explorer SDK Dependency + Migration

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/main/resources/db/migration/V7__aws_cost_records.sql`

- [ ] **Step 1: Add Cost Explorer dependency**

In `build.gradle.kts`, add after the SSM dependency:

```kotlin
implementation("software.amazon.awssdk:costexplorer")
```

- [ ] **Step 2: Create V7 migration**

Create `src/main/resources/db/migration/V7__aws_cost_records.sql`:

```sql
CREATE TABLE aws_cost_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    billing_cycle_start DATE NOT NULL,
    billing_cycle_end   DATE NOT NULL,
    compute_cost_usd    DECIMAL(10,4) NOT NULL DEFAULT 0,
    storage_cost_usd    DECIMAL(10,4) NOT NULL DEFAULT 0,
    transfer_cost_usd   DECIMAL(10,4) NOT NULL DEFAULT 0,
    total_cost_usd      DECIMAL(10,4) NOT NULL DEFAULT 0,
    fetched_at          TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_aws_cost_records_billing_cycle ON aws_cost_records(billing_cycle_start DESC);
```

- [ ] **Step 3: Verify compilation and migration**

```bash
./gradlew compileKotlin
```

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts src/main/resources/db/migration/V7__aws_cost_records.sql && git commit -m "feat: add Cost Explorer SDK and aws_cost_records migration"
```

---

### Task 2: AwsCostRecord Entity + Repository

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/cost/entity/AwsCostRecord.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/cost/repository/AwsCostRecordRepository.kt`

- [ ] **Step 1: Create entity**

Create `src/main/kotlin/org/sightech/memoryvault/cost/entity/AwsCostRecord.kt`:

```kotlin
package org.sightech.memoryvault.cost.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "aws_cost_records")
class AwsCostRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID(0, 0),

    @Column(name = "billing_cycle_start", nullable = false)
    val billingCycleStart: LocalDate,

    @Column(name = "billing_cycle_end", nullable = false)
    val billingCycleEnd: LocalDate,

    @Column(name = "compute_cost_usd", nullable = false)
    var computeCostUsd: BigDecimal = BigDecimal.ZERO,

    @Column(name = "storage_cost_usd", nullable = false)
    var storageCostUsd: BigDecimal = BigDecimal.ZERO,

    @Column(name = "transfer_cost_usd", nullable = false)
    var transferCostUsd: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_cost_usd", nullable = false)
    var totalCostUsd: BigDecimal = BigDecimal.ZERO,

    @Column(name = "fetched_at", nullable = false)
    val fetchedAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
```

- [ ] **Step 2: Create repository**

Create `src/main/kotlin/org/sightech/memoryvault/cost/repository/AwsCostRecordRepository.kt`:

```kotlin
package org.sightech.memoryvault.cost.repository

import org.sightech.memoryvault.cost.entity.AwsCostRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface AwsCostRecordRepository : JpaRepository<AwsCostRecord, UUID> {
    fun findFirstByOrderByFetchedAtDesc(): AwsCostRecord?
    fun findByBillingCycleStartGreaterThanEqualOrderByBillingCycleStartDesc(start: LocalDate): List<AwsCostRecord>
}
```

- [ ] **Step 3: Verify compilation**

```bash
./gradlew compileKotlin
```

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/cost/ && git commit -m "feat: add AwsCostRecord entity and repository"
```

---

### Task 3: CostService Interface + Local Implementation

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/cost/service/CostService.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/cost/service/LocalCostService.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/cost/service/LocalCostServiceTest.kt`

- [ ] **Step 1: Define CostService interface**

Create `src/main/kotlin/org/sightech/memoryvault/cost/service/CostService.kt`:

```kotlin
package org.sightech.memoryvault.cost.service

import org.sightech.memoryvault.cost.entity.AwsCostRecord

interface CostService {
    fun fetchCurrentCycle(): AwsCostRecord?
    fun getLatestCost(): AwsCostRecord?
    fun getCostHistory(months: Int): List<AwsCostRecord>
}
```

- [ ] **Step 2: Write failing test for LocalCostService**

Create `src/test/kotlin/org/sightech/memoryvault/cost/service/LocalCostServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.cost.service

import org.junit.jupiter.api.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalCostServiceTest {

    private val service = LocalCostService()

    @Test
    fun `fetchCurrentCycle returns null`() {
        assertNull(service.fetchCurrentCycle())
    }

    @Test
    fun `getLatestCost returns null`() {
        assertNull(service.getLatestCost())
    }

    @Test
    fun `getCostHistory returns empty list`() {
        assertTrue(service.getCostHistory(3).isEmpty())
    }
}
```

- [ ] **Step 3: Implement LocalCostService**

Create `src/main/kotlin/org/sightech/memoryvault/cost/service/LocalCostService.kt`:

```kotlin
package org.sightech.memoryvault.cost.service

import org.sightech.memoryvault.cost.entity.AwsCostRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("local | test")
class LocalCostService : CostService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun fetchCurrentCycle(): AwsCostRecord? {
        log.debug("Cost tracking not available in local profile")
        return null
    }

    override fun getLatestCost(): AwsCostRecord? = null

    override fun getCostHistory(months: Int): List<AwsCostRecord> = emptyList()
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew test --tests "*LocalCostServiceTest"
```

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/cost/service/CostService.kt src/main/kotlin/org/sightech/memoryvault/cost/service/LocalCostService.kt src/test/kotlin/org/sightech/memoryvault/cost/service/LocalCostServiceTest.kt && git commit -m "feat: add CostService interface and LocalCostService"
```

---

### Task 4: AwsCostService Implementation

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/cost/service/AwsCostService.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/cost/service/AwsCostServiceTest.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/config/AwsConfig.kt`

- [ ] **Step 1: Add CostExplorerClient bean to AwsConfig**

Add to `AwsConfig.kt`:

```kotlin
@Value("\${memoryvault.cost.region:us-east-1}")
lateinit var costRegion: String

@Bean
fun costExplorerClient(): CostExplorerClient = CostExplorerClient.builder()
    .region(Region.of(costRegion))
    .build()
```

Add import: `software.amazon.awssdk.services.costexplorer.CostExplorerClient`

- [ ] **Step 2: Write failing tests**

Create `src/test/kotlin/org/sightech/memoryvault/cost/service/AwsCostServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.cost.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.cost.repository.AwsCostRecordRepository
import software.amazon.awssdk.services.costexplorer.CostExplorerClient
import software.amazon.awssdk.services.costexplorer.model.*
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AwsCostServiceTest {

    private val ceClient = mockk<CostExplorerClient>()
    private val repository = mockk<AwsCostRecordRepository>(relaxed = true)
    private lateinit var service: AwsCostService

    @BeforeEach
    fun setUp() {
        service = AwsCostService(ceClient, repository)
    }

    @Test
    fun `fetchCurrentCycle queries Cost Explorer and saves record`() {
        val groups = listOf(
            group("Amazon Elastic Compute Cloud - Compute", "5.5000"),
            group("AWS Lambda", "0.5000"),
            group("Amazon Simple Storage Service", "1.2000"),
            group("Amazon Relational Database Service", "3.0000"),
            group("AWS Data Transfer", "0.3000")
        )

        every { ceClient.getCostAndUsage(any<GetCostAndUsageRequest>()) } returns
            GetCostAndUsageResponse.builder()
                .resultsByTime(listOf(
                    ResultByTime.builder()
                        .groups(groups)
                        .timePeriod(DateInterval.builder()
                            .start(LocalDate.now().withDayOfMonth(1).toString())
                            .end(LocalDate.now().plusMonths(1).withDayOfMonth(1).toString())
                            .build())
                        .build()
                ))
                .build() as GetCostAndUsageResponse

        every { repository.save(any()) } answers { firstArg() }

        val record = service.fetchCurrentCycle()

        assertNotNull(record)
        assertEquals("6.0000".toBigDecimal(), record.computeCostUsd)
        assertEquals("4.2000".toBigDecimal(), record.storageCostUsd)
        assertEquals("0.3000".toBigDecimal(), record.transferCostUsd)
        assertEquals("10.5000".toBigDecimal(), record.totalCostUsd)
        verify { repository.save(any()) }
    }

    @Test
    fun `getLatestCost delegates to repository`() {
        every { repository.findFirstByOrderByFetchedAtDesc() } returns null
        val result = service.getLatestCost()
        assertEquals(null, result)
    }

    @Test
    fun `getCostHistory returns records for last N months`() {
        every {
            repository.findByBillingCycleStartGreaterThanEqualOrderByBillingCycleStartDesc(any())
        } returns emptyList()
        val result = service.getCostHistory(3)
        assertEquals(0, result.size)
    }

    private fun group(service: String, amount: String): Group =
        Group.builder()
            .keys(listOf(service))
            .metrics(mapOf("UnblendedCost" to MetricValue.builder().amount(amount).unit("USD").build()))
            .build()
}
```

- [ ] **Step 3: Implement AwsCostService**

Create `src/main/kotlin/org/sightech/memoryvault/cost/service/AwsCostService.kt`:

```kotlin
package org.sightech.memoryvault.cost.service

import org.sightech.memoryvault.cost.entity.AwsCostRecord
import org.sightech.memoryvault.cost.repository.AwsCostRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.costexplorer.CostExplorerClient
import software.amazon.awssdk.services.costexplorer.model.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Service
@Profile("aws")
class AwsCostService(
    private val ceClient: CostExplorerClient,
    private val repository: AwsCostRecordRepository
) : CostService {

    private val log = LoggerFactory.getLogger(javaClass)

    private val computeServices = setOf(
        "Amazon Elastic Compute Cloud - Compute", "AWS Lambda"
    )
    private val storageServices = setOf(
        "Amazon Simple Storage Service", "Amazon Relational Database Service"
    )
    private val transferServices = setOf(
        "AWS Data Transfer"
    )

    override fun fetchCurrentCycle(): AwsCostRecord {
        val now = LocalDate.now()
        val start = now.withDayOfMonth(1)
        val end = now.plusMonths(1).withDayOfMonth(1)

        val request = GetCostAndUsageRequest.builder()
            .timePeriod(DateInterval.builder()
                .start(start.toString())
                .end(end.toString())
                .build())
            .granularity(Granularity.MONTHLY)
            .metrics(listOf("UnblendedCost"))
            .groupBy(GroupDefinition.builder()
                .type(GroupDefinitionType.DIMENSION)
                .key("SERVICE")
                .build())
            .build()

        val response = ceClient.getCostAndUsage(request)
        var compute = BigDecimal.ZERO
        var storage = BigDecimal.ZERO
        var transfer = BigDecimal.ZERO

        for (result in response.resultsByTime()) {
            for (group in result.groups()) {
                val serviceName = group.keys().firstOrNull() ?: continue
                val amount = group.metrics()["UnblendedCost"]?.amount()
                    ?.toBigDecimalOrNull() ?: continue

                when {
                    serviceName in computeServices -> compute = compute.add(amount)
                    serviceName in storageServices -> storage = storage.add(amount)
                    serviceName in transferServices -> transfer = transfer.add(amount)
                    else -> compute = compute.add(amount) // default bucket
                }
            }
        }

        val total = compute.add(storage).add(transfer)
        val record = AwsCostRecord(
            billingCycleStart = start,
            billingCycleEnd = end,
            computeCostUsd = compute,
            storageCostUsd = storage,
            transferCostUsd = transfer,
            totalCostUsd = total,
            fetchedAt = Instant.now()
        )

        log.info("Cost fetch complete: compute={}, storage={}, transfer={}, total={}",
            compute, storage, transfer, total)
        return repository.save(record)
    }

    override fun getLatestCost(): AwsCostRecord? =
        repository.findFirstByOrderByFetchedAtDesc()

    override fun getCostHistory(months: Int): List<AwsCostRecord> {
        val since = LocalDate.now().minusMonths(months.toLong()).withDayOfMonth(1)
        return repository.findByBillingCycleStartGreaterThanEqualOrderByBillingCycleStartDesc(since)
    }
}
```

- [ ] **Step 4: Run tests**

```bash
./gradlew test --tests "*AwsCostServiceTest"
./gradlew test
```

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/cost/service/AwsCostService.kt src/test/kotlin/org/sightech/memoryvault/cost/service/AwsCostServiceTest.kt src/main/kotlin/org/sightech/memoryvault/config/AwsConfig.kt && git commit -m "feat: implement AwsCostService with Cost Explorer integration"
```

---

### Task 5: Cost Refresh Endpoint + Schedule

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/cost/controller/CostController.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/cost/controller/CostControllerTest.kt`

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/org/sightech/memoryvault/cost/controller/CostControllerTest.kt`:

```kotlin
package org.sightech.memoryvault.cost.controller

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.cost.entity.AwsCostRecord
import org.sightech.memoryvault.cost.service.CostService
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class CostControllerTest {

    private val costService = mockk<CostService>()
    private val controller = CostController(costService)

    @Test
    fun `refresh triggers cost fetch`() {
        val record = AwsCostRecord(
            billingCycleStart = LocalDate.of(2026, 4, 1),
            billingCycleEnd = LocalDate.of(2026, 5, 1),
            totalCostUsd = BigDecimal("10.50")
        )
        every { costService.fetchCurrentCycle() } returns record

        val response = controller.refresh()
        assertEquals(200, response.statusCode.value())
        verify { costService.fetchCurrentCycle() }
    }

    @Test
    fun `getLatest returns latest cost record`() {
        every { costService.getLatestCost() } returns null

        val response = controller.getLatest()
        assertEquals(200, response.statusCode.value())
    }

    @Test
    fun `getHistory returns cost records`() {
        every { costService.getCostHistory(3) } returns emptyList()

        val response = controller.getHistory(3)
        assertEquals(200, response.statusCode.value())
    }
}
```

- [ ] **Step 2: Implement CostController**

Create `src/main/kotlin/org/sightech/memoryvault/cost/controller/CostController.kt`:

```kotlin
package org.sightech.memoryvault.cost.controller

import org.sightech.memoryvault.cost.entity.AwsCostRecord
import org.sightech.memoryvault.cost.service.CostService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/internal/costs")
class CostController(private val costService: CostService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/refresh")
    fun refresh(): ResponseEntity<AwsCostRecord?> {
        log.info("Cost refresh triggered")
        val record = costService.fetchCurrentCycle()
        return ResponseEntity.ok(record)
    }

    @GetMapping("/latest")
    fun getLatest(): ResponseEntity<AwsCostRecord?> {
        return ResponseEntity.ok(costService.getLatestCost())
    }

    @GetMapping("/history")
    fun getHistory(@RequestParam(defaultValue = "6") months: Int): ResponseEntity<List<AwsCostRecord>> {
        return ResponseEntity.ok(costService.getCostHistory(months))
    }

    @Scheduled(cron = "0 0 6 * * *")
    fun scheduledRefresh() {
        log.info("Scheduled cost refresh running")
        costService.fetchCurrentCycle()
    }
}
```

- [ ] **Step 3: Run tests**

```bash
./gradlew test --tests "*CostControllerTest"
```

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/cost/controller/CostController.kt src/test/kotlin/org/sightech/memoryvault/cost/controller/CostControllerTest.kt && git commit -m "feat: add CostController with scheduled refresh and internal endpoints"
```

---

### Task 6: MCP Cost Tool

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/mcp/CostTools.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/mcp/CostToolsTest.kt`

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/org/sightech/memoryvault/mcp/CostToolsTest.kt`:

```kotlin
package org.sightech.memoryvault.mcp

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.cost.entity.AwsCostRecord
import org.sightech.memoryvault.cost.service.CostService
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertContains
import kotlin.test.assertNotNull

class CostToolsTest {

    private val costService = mockk<CostService>()
    private val tools = CostTools(costService)

    @Test
    fun `getAwsCosts returns latest cost when available`() {
        every { costService.getLatestCost() } returns AwsCostRecord(
            billingCycleStart = LocalDate.of(2026, 4, 1),
            billingCycleEnd = LocalDate.of(2026, 5, 1),
            computeCostUsd = BigDecimal("6.00"),
            storageCostUsd = BigDecimal("4.20"),
            transferCostUsd = BigDecimal("0.30"),
            totalCostUsd = BigDecimal("10.50")
        )
        every { costService.getCostHistory(any()) } returns emptyList()

        val result = tools.getAwsCosts(false, 0)
        assertNotNull(result)
        assertContains(result, "10.50")
    }

    @Test
    fun `getAwsCosts returns message when no data`() {
        every { costService.getLatestCost() } returns null
        every { costService.getCostHistory(any()) } returns emptyList()

        val result = tools.getAwsCosts(false, 0)
        assertNotNull(result)
    }
}
```

- [ ] **Step 2: Implement CostTools**

Create `src/main/kotlin/org/sightech/memoryvault/mcp/CostTools.kt`:

```kotlin
package org.sightech.memoryvault.mcp

import org.sightech.memoryvault.cost.service.CostService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

@Component
class CostTools(private val costService: CostService) {

    @Tool(description = "Get AWS cost information for the current billing cycle and optional history. Use when the user asks about AWS spending, costs, or billing.")
    fun getAwsCosts(
        @ToolParam(description = "Include cost history for previous months") includeHistory: Boolean,
        @ToolParam(description = "Number of months of history to include (1-12)") historyMonths: Int
    ): String {
        val latest = costService.getLatestCost()
            ?: return "No cost data available. Cost tracking may not be configured or data hasn't been fetched yet."

        val sb = StringBuilder()
        sb.appendLine("## Current Billing Cycle (${latest.billingCycleStart} to ${latest.billingCycleEnd})")
        sb.appendLine("- Compute: \$${latest.computeCostUsd}")
        sb.appendLine("- Storage: \$${latest.storageCostUsd}")
        sb.appendLine("- Transfer: \$${latest.transferCostUsd}")
        sb.appendLine("- **Total: \$${latest.totalCostUsd}**")
        sb.appendLine("- Last updated: ${latest.fetchedAt}")

        if (includeHistory && historyMonths > 0) {
            val history = costService.getCostHistory(historyMonths.coerceIn(1, 12))
            if (history.isNotEmpty()) {
                sb.appendLine("\n## Cost History")
                for (record in history) {
                    sb.appendLine("- ${record.billingCycleStart}: \$${record.totalCostUsd}")
                }
            }
        }

        return sb.toString()
    }
}
```

- [ ] **Step 3: Run tests**

```bash
./gradlew test --tests "*CostToolsTest"
./gradlew test
```

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/mcp/CostTools.kt src/test/kotlin/org/sightech/memoryvault/mcp/CostToolsTest.kt && git commit -m "feat: add getAwsCosts MCP tool"
```

---

## Summary Table

| Task  | Description                                | Key Files                                                                    |
|-------|--------------------------------------------|------------------------------------------------------------------------------|
| 1     | Cost Explorer SDK + migration              | `build.gradle.kts`, `V7__aws_cost_records.sql`                               |
| 2     | AwsCostRecord entity + repository          | `cost/entity/AwsCostRecord.kt`, `cost/repository/AwsCostRecordRepository.kt` |
| 3     | CostService interface + LocalCostService   | `cost/service/CostService.kt`, `cost/service/LocalCostService.kt`            |
| 4     | AwsCostService (Cost Explorer integration) | `cost/service/AwsCostService.kt`, `config/AwsConfig.kt`                      |
| 5     | CostController + scheduled refresh         | `cost/controller/CostController.kt`                                          |
| 6     | MCP CostTools                              | `mcp/CostTools.kt`                                                           |
