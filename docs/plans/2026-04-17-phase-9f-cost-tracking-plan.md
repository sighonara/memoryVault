# Phase 9F — Cost Tracking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Track cloud infrastructure costs daily, store per-service breakdown as JSONB, and surface in admin UI + MCP tool.

**Architecture:** `CostService` interface with `AwsCostService` (`@Profile("aws")`) calling AWS Cost Explorer SDK and `LocalCostService` (`@Profile("local | test")`) returning stubs. Daily `@Scheduled` refresh. GraphQL query + mutation for admin UI. Expandable cost card in existing stats panel. MCP tool for Claude Desktop.

**Tech Stack:** AWS SDK v2 (Cost Explorer), Spring Boot 4.x (Kotlin), Flyway, Spring for GraphQL, Angular 21 (standalone components, NgRx Signal Store, Apollo Angular), Spring AI MCP

**Design Spec:** `docs/plans/2026-04-17-phase-9f-cost-tracking-design.md`

**Key discovery:** `@EnableScheduling` is not currently present in the project. The existing `SpringJobScheduler` uses programmatic `TaskScheduler.schedule()` with `CronTrigger`. We must add `@EnableScheduling` to `SchedulingConfig` for `@Scheduled` to work.

**GraphQL note:** No `JSON` scalar exists. `serviceCosts` will be serialized as a JSON string (`String!` in schema) and parsed client-side with `JSON.parse()`. This avoids adding a new dependency for one field.

---

### Task 1: Gradle Dependency + Flyway Migration

**Files:**
- Modify: `build.gradle.kts` (line ~58, after `cloudwatchlogs`)
- Create: `src/main/resources/db/migration/V7__cost_records.sql`

- [ ] **Step 1: Add Cost Explorer SDK dependency**

In `build.gradle.kts`, add after line 58 (`implementation("software.amazon.awssdk:cloudwatchlogs")`):

```kotlin
implementation("software.amazon.awssdk:costexplorer")
```

- [ ] **Step 2: Create V7 migration**

Create `src/main/resources/db/migration/V7__cost_records.sql`:

```sql
CREATE TABLE cost_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    billing_date    DATE NOT NULL UNIQUE,
    service_costs   JSONB NOT NULL DEFAULT '{}',
    total_cost_usd  DECIMAL(10,4) NOT NULL DEFAULT 0,
    fetched_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_cost_records_billing_date ON cost_records(billing_date DESC);
```

- [ ] **Step 3: Verify compilation and migration**

```bash
./gradlew compileKotlin
```

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts src/main/resources/db/migration/V7__cost_records.sql && git commit -m "feat: add Cost Explorer SDK and cost_records migration"
```

---

### Task 2: CostRecord Entity + Repository

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/cost/entity/CostRecord.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/cost/repository/CostRecordRepository.kt`

- [ ] **Step 1: Create CostRecord entity**

Create `src/main/kotlin/org/sightech/memoryvault/cost/entity/CostRecord.kt`:

```kotlin
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
```

- [ ] **Step 2: Create CostRecordRepository**

Create `src/main/kotlin/org/sightech/memoryvault/cost/repository/CostRecordRepository.kt`:

```kotlin
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
```

- [ ] **Step 3: Verify compilation**

```bash
./gradlew compileKotlin
```

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/cost/ && git commit -m "feat: add CostRecord entity and repository"
```

---

### Task 3: CostService Interface + LocalCostService

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/cost/service/CostService.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/cost/service/LocalCostService.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/cost/service/LocalCostServiceTest.kt`

- [ ] **Step 1: Write failing tests for LocalCostService**

Create `src/test/kotlin/org/sightech/memoryvault/cost/service/LocalCostServiceTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "*LocalCostServiceTest" 2>&1 | tail -5
```

Expected: compilation failure (classes don't exist yet).

- [ ] **Step 3: Create CostService interface**

Create `src/main/kotlin/org/sightech/memoryvault/cost/service/CostService.kt`:

```kotlin
package org.sightech.memoryvault.cost.service

import org.sightech.memoryvault.cost.entity.CostRecord
import java.time.LocalDate

interface CostService {
    fun refreshCosts(): CostRecord?
    fun getLatestCost(): CostRecord?
    fun getCostHistory(months: Int): List<CostRecord>
    fun getDailyCosts(from: LocalDate, to: LocalDate): List<CostRecord>
}
```

- [ ] **Step 4: Create LocalCostService**

Create `src/main/kotlin/org/sightech/memoryvault/cost/service/LocalCostService.kt`:

```kotlin
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
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew test --tests "*LocalCostServiceTest"
```

Expected: all 4 tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/cost/service/ src/test/kotlin/org/sightech/memoryvault/cost/service/ && git commit -m "feat: add CostService interface and LocalCostService"
```

---

### Task 4: AwsCostService Implementation

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/config/AwsConfig.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/cost/service/AwsCostService.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/cost/service/AwsCostServiceTest.kt`

- [ ] **Step 1: Write failing tests for AwsCostService**

Create `src/test/kotlin/org/sightech/memoryvault/cost/service/AwsCostServiceTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "*AwsCostServiceTest" 2>&1 | tail -5
```

Expected: compilation failure (AwsCostService doesn't exist yet).

- [ ] **Step 3: Add CostExplorerClient bean to AwsConfig**

Modify `src/main/kotlin/org/sightech/memoryvault/config/AwsConfig.kt`. Add the import and bean:

```kotlin
package org.sightech.memoryvault.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.costexplorer.CostExplorerClient
import software.amazon.awssdk.services.s3.S3Client

@Configuration
@Profile("aws")
class AwsConfig {

    @Value("\${memoryvault.storage.s3-region:us-east-1}")
    lateinit var s3Region: String

    @Value("\${memoryvault.logging.cloudwatch-region:us-east-1}")
    lateinit var cloudwatchRegion: String

    @Bean
    fun s3Client(): S3Client = S3Client.builder()
        .region(Region.of(s3Region))
        .build()

    @Bean
    fun cloudWatchLogsClient(): CloudWatchLogsClient = CloudWatchLogsClient.builder()
        .region(Region.of(cloudwatchRegion))
        .build()

    @Bean
    fun costExplorerClient(): CostExplorerClient = CostExplorerClient.builder()
        .region(Region.US_EAST_1)
        .build()
}
```

Note: Cost Explorer is a global service, always called against `us-east-1`.

- [ ] **Step 4: Implement AwsCostService**

Create `src/main/kotlin/org/sightech/memoryvault/cost/service/AwsCostService.kt`:

```kotlin
package org.sightech.memoryvault.cost.service

import org.sightech.memoryvault.cost.entity.CostRecord
import org.sightech.memoryvault.cost.repository.CostRecordRepository
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
    private val repository: CostRecordRepository
) : CostService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun refreshCosts(): CostRecord? {
        val today = LocalDate.now()
        val start = today.withDayOfMonth(1)
        val end = today.plusDays(1)

        val request = GetCostAndUsageRequest.builder()
            .timePeriod(DateInterval.builder()
                .start(start.toString())
                .end(end.toString())
                .build())
            .granularity(Granularity.DAILY)
            .metrics(listOf("UnblendedCost"))
            .groupBy(GroupDefinition.builder()
                .type(GroupDefinitionType.DIMENSION)
                .key("SERVICE")
                .build())
            .build()

        val response = ceClient.getCostAndUsage(request)
        var latestRecord: CostRecord? = null

        for (result in response.resultsByTime()) {
            val date = LocalDate.parse(result.timePeriod().start())
            val serviceCosts = mutableMapOf<String, BigDecimal>()

            for (group in result.groups()) {
                val serviceName = group.keys().firstOrNull() ?: continue
                val amount = group.metrics()["UnblendedCost"]?.amount()
                    ?.toBigDecimalOrNull() ?: continue
                if (amount.compareTo(BigDecimal.ZERO) != 0) {
                    serviceCosts[serviceName] = amount
                }
            }

            val total = serviceCosts.values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
            val existing = repository.findByBillingDate(date)

            val record = if (existing != null) {
                existing.serviceCosts = serviceCosts
                existing.totalCostUsd = total
                existing.fetchedAt = Instant.now()
                existing
            } else {
                CostRecord(
                    billingDate = date,
                    serviceCosts = serviceCosts,
                    totalCostUsd = total,
                    fetchedAt = Instant.now()
                )
            }

            latestRecord = repository.save(record)
        }

        log.info("Cost refresh complete: {} to {}, latest total={}",
            start, end, latestRecord?.totalCostUsd ?: "no data")
        return latestRecord
    }

    override fun getLatestCost(): CostRecord? =
        repository.findFirstByOrderByBillingDateDesc()

    override fun getCostHistory(months: Int): List<CostRecord> {
        val from = LocalDate.now().minusMonths(months.toLong()).withDayOfMonth(1)
        val to = LocalDate.now()
        return repository.findByBillingDateBetweenOrderByBillingDateDesc(from, to)
    }

    override fun getDailyCosts(from: LocalDate, to: LocalDate): List<CostRecord> =
        repository.findByBillingDateBetweenOrderByBillingDateDesc(from, to)
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew test --tests "*AwsCostServiceTest"
```

Expected: all 6 tests pass.

- [ ] **Step 6: Run full test suite**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/config/AwsConfig.kt src/main/kotlin/org/sightech/memoryvault/cost/service/AwsCostService.kt src/test/kotlin/org/sightech/memoryvault/cost/service/AwsCostServiceTest.kt && git commit -m "feat: implement AwsCostService with Cost Explorer integration"
```

---

### Task 5: CostRefreshTask + @EnableScheduling + Internal Endpoint

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/config/SchedulingConfig.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/cost/CostRefreshTask.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/cost/CostRefreshTaskTest.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/scheduling/controller/InternalSyncController.kt`

- [ ] **Step 1: Write failing test for CostRefreshTask**

Create `src/test/kotlin/org/sightech/memoryvault/cost/CostRefreshTaskTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "*CostRefreshTaskTest" 2>&1 | tail -5
```

Expected: compilation failure.

- [ ] **Step 3: Add @EnableScheduling to SchedulingConfig**

Modify `src/main/kotlin/org/sightech/memoryvault/config/SchedulingConfig.kt`:

```kotlin
package org.sightech.memoryvault.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
@EnableScheduling
class SchedulingConfig {

    @Bean
    fun taskScheduler(): TaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 2
        scheduler.setThreadNamePrefix("memoryvault-scheduler-")
        scheduler.initialize()
        return scheduler
    }
}
```

- [ ] **Step 4: Create CostRefreshTask**

Create `src/main/kotlin/org/sightech/memoryvault/cost/CostRefreshTask.kt`:

```kotlin
package org.sightech.memoryvault.cost

import org.sightech.memoryvault.cost.service.CostService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("aws")
class CostRefreshTask(private val costService: CostService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 6 * * *")
    fun scheduledRefresh() {
        log.info("Scheduled cost refresh starting")
        try {
            val record = costService.refreshCosts()
            log.info("Scheduled cost refresh complete: total={}", record?.totalCostUsd ?: "no data")
        } catch (e: Exception) {
            log.warn("Scheduled cost refresh failed: {}", e.message, e)
        }
    }
}
```

- [ ] **Step 5: Add cost refresh to InternalSyncController**

Modify `src/main/kotlin/org/sightech/memoryvault/scheduling/controller/InternalSyncController.kt`. Add the `CostService` dependency and new endpoint.

Add import:

```kotlin
import org.sightech.memoryvault.cost.service.CostService
```

Add constructor parameter:

```kotlin
private val costService: CostService
```

Add method after the `syncYoutube()` method:

```kotlin
@PostMapping("/costs/refresh")
fun refreshCosts(): ResponseEntity<Map<String, Any>> {
    log.info("Internal trigger: cost refresh")
    val record = costService.refreshCosts()
    return if (record != null) {
        ResponseEntity.ok(mapOf(
            "billingDate" to record.billingDate.toString(),
            "totalCostUsd" to record.totalCostUsd.toString()
        ))
    } else {
        ResponseEntity.noContent().build()
    }
}
```

Note: the `InternalSyncController` is `@RequestMapping("/api/internal/sync")`, so the full path is `/api/internal/sync/costs/refresh`. This is behind the existing `InternalApiKeyFilter`.

- [ ] **Step 6: Run tests**

```bash
./gradlew test --tests "*CostRefreshTaskTest"
./gradlew test
```

Expected: all tests pass. Existing `InternalSyncControllerTest` may need updating if it validates constructor params — check for compile errors.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/config/SchedulingConfig.kt src/main/kotlin/org/sightech/memoryvault/cost/CostRefreshTask.kt src/test/kotlin/org/sightech/memoryvault/cost/CostRefreshTaskTest.kt src/main/kotlin/org/sightech/memoryvault/scheduling/controller/InternalSyncController.kt && git commit -m "feat: add CostRefreshTask with @Scheduled and internal refresh endpoint"
```

---

### Task 6: GraphQL Schema + Resolver

**Files:**
- Modify: `src/main/resources/graphql/admin.graphqls`
- Modify: `src/main/resources/graphql/schema.graphqls`
- Create: `src/main/kotlin/org/sightech/memoryvault/graphql/CostResolver.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/graphql/CostResolverTest.kt`

- [ ] **Step 1: Write failing test for CostResolver**

Create `src/test/kotlin/org/sightech/memoryvault/graphql/CostResolverTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "*CostResolverTest" 2>&1 | tail -5
```

Expected: compilation failure.

- [ ] **Step 3: Add cost types to admin.graphqls**

Append to `src/main/resources/graphql/admin.graphqls`:

```graphql
type CostRecord {
    id: UUID!
    billingDate: String!
    serviceCosts: String!
    totalCostUsd: String!
    fetchedAt: Instant!
}

type CostSummary {
    current: CostRecord
    monthlyTotals: [MonthlyCost!]!
}

type MonthlyCost {
    month: String!
    totalCostUsd: String!
}
```

Note: `serviceCosts` is `String!` (JSON-serialized) since we don't have a `JSON` scalar. `totalCostUsd` is `String!` to preserve decimal precision (GraphQL `Float` would lose precision).

- [ ] **Step 4: Add costs query and mutation to schema.graphqls**

Add to the `Query` type in `src/main/resources/graphql/schema.graphqls`, after the `stats` line:

```graphql
    # Costs
    costs(months: Int): CostSummary!
```

Add to the `Mutation` type, at the end:

```graphql
    # Costs
    refreshCosts: CostRecord
```

- [ ] **Step 5: Create CostResolver**

Create `src/main/kotlin/org/sightech/memoryvault/graphql/CostResolver.kt`:

```kotlin
package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.cost.service.CostService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal

data class CostRecordDto(
    val id: String,
    val billingDate: String,
    val serviceCosts: String,
    val totalCostUsd: String,
    val fetchedAt: java.time.Instant
)

data class MonthlyCost(
    val month: String,
    val totalCostUsd: String
)

data class CostSummary(
    val current: CostRecordDto?,
    val monthlyTotals: List<MonthlyCost>
)

@Controller
class CostResolver(
    private val costService: CostService
) {

    private val objectMapper = ObjectMapper()

    @QueryMapping
    fun costs(@Argument months: Int?): CostSummary {
        val effectiveMonths = months ?: 6
        val latest = costService.getLatestCost()
        val history = costService.getCostHistory(effectiveMonths)

        val current = latest?.let { toDto(it) }

        val monthlyTotals = history
            .groupBy { it.billingDate.toString().substring(0, 7) }
            .map { (month, records) ->
                val total = records.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.totalCostUsd) }
                MonthlyCost(month = month, totalCostUsd = total.toPlainString())
            }
            .sortedByDescending { it.month }

        return CostSummary(current = current, monthlyTotals = monthlyTotals)
    }

    @MutationMapping
    fun refreshCosts(): CostRecordDto? {
        val record = costService.refreshCosts()
        return record?.let { toDto(it) }
    }

    private fun toDto(record: org.sightech.memoryvault.cost.entity.CostRecord): CostRecordDto {
        return CostRecordDto(
            id = record.id.toString(),
            billingDate = record.billingDate.toString(),
            serviceCosts = objectMapper.writeValueAsString(record.serviceCosts),
            totalCostUsd = record.totalCostUsd.toPlainString(),
            fetchedAt = record.fetchedAt
        )
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
./gradlew test --tests "*CostResolverTest"
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/graphql/admin.graphqls src/main/resources/graphql/schema.graphqls src/main/kotlin/org/sightech/memoryvault/graphql/CostResolver.kt src/test/kotlin/org/sightech/memoryvault/graphql/CostResolverTest.kt && git commit -m "feat: add GraphQL costs query, refreshCosts mutation, and CostResolver"
```

---

### Task 7: MCP CostTools

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
import org.sightech.memoryvault.cost.entity.CostRecord
import org.sightech.memoryvault.cost.service.CostService
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertContains
import kotlin.test.assertNotNull

class CostToolsTest {

    private val costService = mockk<CostService>()
    private val tools = CostTools(costService)

    @Test
    fun `getAwsCosts returns formatted cost summary`() {
        val latest = CostRecord(
            billingDate = LocalDate.of(2026, 4, 15),
            serviceCosts = mapOf(
                "Amazon Elastic Compute Cloud - Compute" to BigDecimal("14.50"),
                "Amazon Relational Database Service" to BigDecimal("12.30"),
                "Amazon Simple Storage Service" to BigDecimal("0.02")
            ),
            totalCostUsd = BigDecimal("26.82")
        )
        every { costService.getLatestCost() } returns latest
        every { costService.getCostHistory(6) } returns listOf(latest)

        val result = tools.getAwsCosts(6)

        assertNotNull(result)
        assertContains(result, "26.82")
        assertContains(result, "Amazon Elastic Compute Cloud")
        assertContains(result, "14.50")
    }

    @Test
    fun `getAwsCosts returns message when no data`() {
        every { costService.getLatestCost() } returns null
        every { costService.getCostHistory(6) } returns emptyList()

        val result = tools.getAwsCosts(6)

        assertContains(result, "No cost data available")
    }

    @Test
    fun `getAwsCosts includes monthly history`() {
        val april = CostRecord(
            billingDate = LocalDate.of(2026, 4, 15),
            serviceCosts = mapOf("EC2" to BigDecimal("10.00")),
            totalCostUsd = BigDecimal("10.00")
        )
        val march = CostRecord(
            billingDate = LocalDate.of(2026, 3, 31),
            serviceCosts = mapOf("EC2" to BigDecimal("28.00")),
            totalCostUsd = BigDecimal("28.00")
        )
        every { costService.getLatestCost() } returns april
        every { costService.getCostHistory(6) } returns listOf(april, march)

        val result = tools.getAwsCosts(6)

        assertContains(result, "2026-04")
        assertContains(result, "2026-03")
        assertContains(result, "28.00")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "*CostToolsTest" 2>&1 | tail -5
```

Expected: compilation failure.

- [ ] **Step 3: Implement CostTools**

Create `src/main/kotlin/org/sightech/memoryvault/mcp/CostTools.kt`:

```kotlin
package org.sightech.memoryvault.mcp

import org.sightech.memoryvault.cost.service.CostService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class CostTools(private val costService: CostService) {

    @Tool(description = "Get current cloud cost summary and monthly history. Use when the user asks about cloud spending, billing, or cost trends.")
    fun getAwsCosts(
        @ToolParam(description = "Number of months of history to include (default 6)") months: Int = 6
    ): String {
        val latest = costService.getLatestCost()
            ?: return "No cost data available. Cost tracking may not be configured or data hasn't been fetched yet."

        val sb = StringBuilder()
        sb.appendLine("## Current Cost (${latest.billingDate})")
        sb.appendLine("Total: \$${latest.totalCostUsd}")
        sb.appendLine()
        sb.appendLine("Per-service breakdown:")
        latest.serviceCosts.entries
            .sortedByDescending { it.value }
            .forEach { (service, cost) ->
                sb.appendLine("  $service: \$${cost}")
            }
        sb.appendLine()
        sb.appendLine("Last updated: ${latest.fetchedAt}")

        val history = costService.getCostHistory(months.coerceIn(1, 24))
        if (history.isNotEmpty()) {
            val monthlyTotals = history
                .groupBy { it.billingDate.toString().substring(0, 7) }
                .map { (month, records) ->
                    val total = records.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.totalCostUsd) }
                    month to total
                }
                .sortedByDescending { it.first }

            sb.appendLine()
            sb.appendLine("## Monthly History")
            monthlyTotals.forEach { (month, total) ->
                sb.appendLine("  $month: \$${total}")
            }
        }

        return sb.toString()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "*CostToolsTest"
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/mcp/CostTools.kt src/test/kotlin/org/sightech/memoryvault/mcp/CostToolsTest.kt && git commit -m "feat: add getAwsCosts MCP tool"
```

---

### Task 8: Terraform IAM Policy

**Files:**
- Modify: `terraform/iam.tf`

- [ ] **Step 1: Add Cost Explorer IAM policy**

Add to `terraform/iam.tf`, after the `aws_iam_role_policy_attachment "ec2_ssm"` block (around line 99):

```hcl
# Cost Explorer read access for cost tracking
resource "aws_iam_role_policy" "ec2_cost_explorer" {
  name = "${var.project_name}-ec2-cost-explorer"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ce:GetCostAndUsage",
        ]
        Resource = ["*"]
      }
    ]
  })
}
```

- [ ] **Step 2: Format and validate**

```bash
cd terraform && terraform fmt && terraform validate
```

Expected: `Success! The configuration is valid.`

- [ ] **Step 3: Commit**

```bash
git add terraform/iam.tf && git commit -m "feat: add Cost Explorer IAM policy for EC2 instance role"
```

---

### Task 9: Angular Admin UI — GraphQL + Store

**Files:**
- Modify: `client/src/app/admin/admin.graphql`
- Modify: `client/src/app/admin/admin.store.ts`
- Regenerate: `client/src/app/shared/graphql/generated.ts`

- [ ] **Step 1: Add GraphQL operations**

Append to `client/src/app/admin/admin.graphql`:

```graphql
query GetCosts($months: Int) {
  costs(months: $months) {
    current {
      id
      billingDate
      serviceCosts
      totalCostUsd
      fetchedAt
    }
    monthlyTotals {
      month
      totalCostUsd
    }
  }
}

mutation RefreshCosts {
  refreshCosts {
    id
    billingDate
    serviceCosts
    totalCostUsd
    fetchedAt
  }
}
```

- [ ] **Step 2: Regenerate GraphQL types**

```bash
cd client && npx graphql-codegen
```

Expected: `generated.ts` updated with `GetCostsDocument`, `RefreshCostsDocument`, `CostSummary`, `CostRecord`, `MonthlyCost` types.

- [ ] **Step 3: Update admin store**

Modify `client/src/app/admin/admin.store.ts`. Add to imports:

```typescript
import {
  GetJobsDocument,
  GetLogsDocument,
  GetAdminStatsDocument,
  GetCostsDocument,
  RefreshCostsDocument,
  SyncJob,
  LogEntry,
  SystemStats,
  CostSummary,
} from '../shared/graphql/generated';
```

Add to `AdminState` interface:

```typescript
costSummary: CostSummary | null;
costMonths: number;
refreshingCosts: boolean;
```

Add to `initialState`:

```typescript
costSummary: null,
costMonths: 6,
refreshingCosts: false,
```

Add methods inside `withMethods`. After the `loadLogs` rxMethod:

```typescript
const loadCosts = rxMethod<void>(
  pipe(
    switchMap(() =>
      apollo.query({
        query: GetCostsDocument,
        variables: { months: store.costMonths() },
        fetchPolicy: 'network-only',
      })
    ),
    tap((result: any) => {
      patchState(store, { costSummary: result.data.costs });
    })
  )
);

const refreshCosts = rxMethod<void>(
  pipe(
    tap(() => patchState(store, { refreshingCosts: true })),
    switchMap(() =>
      apollo.mutate({
        mutation: RefreshCostsDocument,
      })
    ),
    tap(() => {
      patchState(store, { refreshingCosts: false });
      loadCosts();
    })
  )
);
```

Add to the returned object:

```typescript
loadCosts,
refreshCosts,
setCostMonths: (months: number) => { patchState(store, { costMonths: months }); loadCosts(); },
```

- [ ] **Step 4: Verify compilation**

```bash
cd client && npx ng build 2>&1 | tail -10
```

Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
cd client && git add src/app/admin/admin.graphql src/app/admin/admin.store.ts src/app/shared/graphql/generated.ts && git commit -m "feat: add costs GraphQL operations and admin store methods"
```

---

### Task 10: Angular Cost Card Component

**Files:**
- Create: `client/src/app/admin/cost-card/cost-card.ts`
- Create: `client/src/app/admin/cost-card/index.ts`
- Modify: `client/src/app/admin/stats-panel/stats-panel.ts`

- [ ] **Step 1: Create cost-card component**

Create `client/src/app/admin/cost-card/cost-card.ts`:

```typescript
import { Component, input, output, signal, computed } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { CostSummary } from '../../shared/graphql/generated';

@Component({
  selector: 'app-cost-card',
  standalone: true,
  imports: [MatIconModule, MatButtonModule, MatProgressSpinnerModule, MatSelectModule],
  template: `
    <div class="cost-card" [class.expanded]="expanded()">
      <div class="cost-header" (click)="expanded.set(!expanded())">
        <mat-icon>payments</mat-icon>
        <div class="cost-body">
          @if (costSummary()?.current) {
            <span class="stat-value">\${{ costSummary()!.current!.totalCostUsd }}</span>
            <span class="stat-label">{{ currentMonthLabel() }}</span>
            @if (trend()) {
              <span class="trend" [class.up]="trend()! > 0" [class.down]="trend()! < 0">
                {{ trend()! > 0 ? '+' : '' }}{{ trend()!.toFixed(0) }}% vs last month
              </span>
            }
          } @else {
            <span class="stat-label">No cost data available</span>
          }
        </div>
        <mat-icon class="expand-icon">{{ expanded() ? 'expand_less' : 'expand_more' }}</mat-icon>
      </div>

      @if (expanded()) {
        <div class="cost-detail">
          @if (costSummary()?.current) {
            <div class="section">
              <div class="section-header">
                <span>Per-Service Breakdown</span>
                <button mat-icon-button (click)="onRefresh.emit(); $event.stopPropagation()" [disabled]="refreshing()">
                  @if (refreshing()) {
                    <mat-spinner diameter="18"></mat-spinner>
                  } @else {
                    <mat-icon>refresh</mat-icon>
                  }
                </button>
              </div>
              <table class="breakdown-table">
                @for (entry of serviceEntries(); track entry.name) {
                  <tr>
                    <td>{{ entry.name }}</td>
                    <td class="amount">\${{ entry.cost }}</td>
                  </tr>
                }
              </table>
              <div class="fetched-at">Last updated: {{ formatDate(costSummary()!.current!.fetchedAt) }}</div>
            </div>
          }

          @if (costSummary()?.monthlyTotals?.length) {
            <div class="section">
              <div class="section-header">
                <span>Monthly History</span>
                <mat-select [value]="months()" (selectionChange)="onMonthsChange.emit($event.value)" class="month-select">
                  <mat-option [value]="3">3 months</mat-option>
                  <mat-option [value]="6">6 months</mat-option>
                  <mat-option [value]="12">12 months</mat-option>
                  <mat-option [value]="120">All</mat-option>
                </mat-select>
              </div>
              <table class="breakdown-table">
                @for (m of costSummary()!.monthlyTotals; track m.month) {
                  <tr>
                    <td>{{ m.month }}</td>
                    <td class="amount">\${{ m.totalCostUsd }}</td>
                  </tr>
                }
              </table>
            </div>
          }

          @if (!costSummary()?.current) {
            <div class="no-data">
              <button mat-stroked-button (click)="onRefresh.emit()">
                <mat-icon>refresh</mat-icon> Fetch cost data
              </button>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .cost-card { border-bottom: 1px solid #e8eaed; border-right: 1px solid #e8eaed; }
    .cost-header {
      display: flex; align-items: flex-start; gap: 10px;
      padding: 12px 16px; cursor: pointer;
    }
    .cost-header:hover { background: #f8f9fa; }
    .cost-header mat-icon:first-child { color: #5f6368; font-size: 20px; width: 20px; height: 20px; margin-top: 2px; overflow: visible; flex-shrink: 0; }
    .cost-body { display: flex; flex-direction: column; flex: 1; }
    .stat-value { font-size: 1.25rem; font-weight: 600; color: #202124; line-height: 1; }
    .stat-label { font-size: 0.75rem; color: #5f6368; margin-top: 2px; }
    .trend { font-size: 0.7rem; margin-top: 2px; }
    .trend.up { color: #c62828; }
    .trend.down { color: #2e7d32; }
    .expand-icon { color: #9aa0a6; margin-left: auto; }
    .cost-detail { padding: 0 16px 16px 46px; }
    .section { margin-bottom: 16px; }
    .section-header { display: flex; align-items: center; justify-content: space-between; font-size: 0.8125rem; font-weight: 500; color: #5f6368; margin-bottom: 8px; }
    .breakdown-table { width: 100%; font-size: 0.8125rem; border-collapse: collapse; }
    .breakdown-table tr { border-bottom: 1px solid #f1f3f4; }
    .breakdown-table td { padding: 4px 0; }
    .breakdown-table .amount { text-align: right; font-variant-numeric: tabular-nums; }
    .fetched-at { font-size: 0.6875rem; color: #9aa0a6; margin-top: 8px; }
    .month-select { width: 120px; }
    .no-data { padding: 16px 0; text-align: center; }
  `],
})
export class CostCardComponent {
  costSummary = input<CostSummary | null>(null);
  months = input<number>(6);
  refreshing = input<boolean>(false);
  onRefresh = output<void>();
  onMonthsChange = output<number>();

  expanded = signal(false);

  serviceEntries = computed(() => {
    const current = this.costSummary()?.current;
    if (!current) return [];
    const costs: Record<string, string> = JSON.parse(current.serviceCosts);
    return Object.entries(costs)
      .map(([name, cost]) => ({ name, cost }))
      .sort((a, b) => parseFloat(b.cost) - parseFloat(a.cost));
  });

  currentMonthLabel = computed(() => {
    const current = this.costSummary()?.current;
    if (!current) return '';
    const date = new Date(current.billingDate + 'T00:00:00');
    return date.toLocaleString('default', { month: 'long', year: 'numeric' });
  });

  trend = computed(() => {
    const totals = this.costSummary()?.monthlyTotals;
    if (!totals || totals.length < 2) return null;
    const current = parseFloat(totals[0].totalCostUsd);
    const previous = parseFloat(totals[1].totalCostUsd);
    if (previous === 0) return null;
    return ((current - previous) / previous) * 100;
  });

  formatDate(ts: any): string {
    if (!ts) return 'Unknown';
    return new Date(ts).toLocaleString();
  }
}
```

- [ ] **Step 2: Create barrel export**

Create `client/src/app/admin/cost-card/index.ts`:

```typescript
export { CostCardComponent } from './cost-card';
```

- [ ] **Step 3: Integrate into stats-panel**

Modify `client/src/app/admin/stats-panel/stats-panel.ts`. Add imports:

```typescript
import { CostCardComponent } from '../cost-card';
import { CostSummary } from '../../shared/graphql/generated';
```

Add to `imports` array:

```typescript
CostCardComponent,
```

Add inputs to the component class:

```typescript
costSummary = input<CostSummary | null>(null);
costMonths = input<number>(6);
refreshingCosts = input<boolean>(false);
onRefreshCosts = output<void>();
onCostMonthsChange = output<number>();
```

Add `output` to the import from `@angular/core`:

```typescript
import { Component, input, output } from '@angular/core';
```

Add the cost card to the template, after the last `stat-item` div and before the closing `</div>` of `stats-grid`:

```html
<app-cost-card
  [costSummary]="costSummary()"
  [months]="costMonths()"
  [refreshing]="refreshingCosts()"
  (onRefresh)="onRefreshCosts.emit()"
  (onMonthsChange)="onCostMonthsChange.emit($event)"
/>
```

- [ ] **Step 4: Wire stats-panel outputs in admin.ts**

Modify `client/src/app/admin/admin.ts`. Update the `app-stats-panel` in the template:

```html
<app-stats-panel
  [stats]="store.stats()"
  [costSummary]="store.costSummary()"
  [costMonths]="store.costMonths()"
  [refreshingCosts]="store.refreshingCosts()"
  (onRefreshCosts)="store.refreshCosts()"
  (onCostMonthsChange)="store.setCostMonths($event)"
/>
```

Update `ngOnInit` to also load costs:

```typescript
ngOnInit() {
  this.store.loadStats();
  this.store.loadJobs();
  this.store.loadLogs();
  this.store.loadCosts();
}
```

Update `onTabChange` — reload costs when switching to stats tab:

```typescript
onTabChange(index: number) {
  if (index === 0) { this.store.loadStats(); this.store.loadCosts(); }
  else if (index === 1) this.store.loadJobs();
  else if (index === 2) this.store.loadLogs();
  if (index !== 2) this.stopFollow();
}
```

- [ ] **Step 5: Verify build**

```bash
cd client && npx ng build 2>&1 | tail -10
```

Expected: build succeeds.

- [ ] **Step 6: Commit**

```bash
cd client && git add src/app/admin/cost-card/ src/app/admin/stats-panel/stats-panel.ts src/app/admin/admin.ts && git commit -m "feat: add expandable cost card to admin stats panel"
```

---

### Task 11: Smoke Test + Update Plan Docs

**Files:**
- Modify: `scripts/smoke-test.sh`
- Modify: `docs/plans/2026-04-17-phase-9f-cost-tracking-plan.md` (mark steps complete)
- Modify: `docs/plans/2026-03-05-tooling-first-design.md` (update master roadmap)

- [ ] **Step 1: Add cost query to smoke test**

In `scripts/smoke-test.sh`, add after the internal sync check (the `if [ "$COGNITO_ACTIVE" -eq 1 ]` block around line 92), still inside that block:

```bash
  check "Internal cost refresh rejects without key" "$BASE_URL/api/internal/sync/costs/refresh" 401 "-X POST"
```

- [ ] **Step 2: Add authenticated cost query check**

In the authenticated endpoints section (inside the `if [ -n "$TOKEN" ]` block), after the REST endpoints loop, add:

```bash
  # GraphQL costs query
  COST_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/graphql" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"query":"{ costs { current { totalCostUsd } monthlyTotals { month totalCostUsd } } }"}')
  if [ "$COST_STATUS" -eq 200 ]; then
    echo "  PASS  GraphQL costs query ($COST_STATUS)"
    PASS=$((PASS + 1))
  else
    echo "  FAIL  GraphQL costs query (expected 200, got $COST_STATUS)"
    FAIL=$((FAIL + 1))
  fi
```

- [ ] **Step 3: Update master roadmap**

In `docs/plans/2026-03-05-tooling-first-design.md`, update the Phase 9 description to note 9F is complete. Remove the `AwsCostRecord entity defined but not implemented` note if present. Add a note about cost tracking being live.

- [ ] **Step 4: Run full test suite**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add scripts/smoke-test.sh docs/plans/ && git commit -m "feat: add cost tracking smoke tests and update roadmap"
```

---

## Summary Table

| Task | Description | Key Files |
|------|-------------|-----------|
| 1 | Gradle dependency + Flyway migration | `build.gradle.kts`, `V7__cost_records.sql` |
| 2 | CostRecord entity + repository | `cost/entity/CostRecord.kt`, `cost/repository/CostRecordRepository.kt` |
| 3 | CostService interface + LocalCostService | `cost/service/CostService.kt`, `cost/service/LocalCostService.kt` |
| 4 | AwsCostService (Cost Explorer) | `cost/service/AwsCostService.kt`, `config/AwsConfig.kt` |
| 5 | CostRefreshTask + @EnableScheduling + internal endpoint | `cost/CostRefreshTask.kt`, `config/SchedulingConfig.kt`, `InternalSyncController.kt` |
| 6 | GraphQL schema + CostResolver | `admin.graphqls`, `schema.graphqls`, `graphql/CostResolver.kt` |
| 7 | MCP CostTools | `mcp/CostTools.kt` |
| 8 | Terraform IAM policy | `terraform/iam.tf` |
| 9 | Angular GraphQL ops + store | `admin.graphql`, `admin.store.ts` |
| 10 | Angular cost card component | `admin/cost-card/cost-card.ts`, `stats-panel.ts`, `admin.ts` |
| 11 | Smoke test + roadmap update | `smoke-test.sh`, master roadmap |
