# Phase 9F — Cost Tracking: Design Spec

## Overview

Track cloud infrastructure costs, store daily history, and surface them in the admin UI and via MCP tool. Cloud-provider-specific code is behind an interface with `@Profile` implementations, following the `LogService` pattern.

**Decisions made during brainstorming:**
- Per-service costs stored as JSONB map (not fixed compute/storage/transfer buckets) — flexible for new services
- Daily granularity (one row per UTC day) — AWS billing dates are UTC-aligned across all regions
- `@Scheduled` daily refresh at 6 AM UTC inside Spring Boot (not EventBridge + Lambda — lightweight API call)
- Cost summary embedded in existing Stats tab (not a separate tab), with expandable detail view
- Keep all history (never delete), UI defaults to 6 months with a selector
- Entity named `CostRecord` (provider-agnostic), not `AwsCostRecord`
- `@Profile("local | test")` for local impl (affirmative, not `!aws` negation)

---

## Data Model

### CostRecord Entity

Flyway migration creates `cost_records` table:

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

Key decisions:
- **`billing_date DATE`** — one row per UTC day. AWS Cost Explorer returns UTC-aligned dates regardless of resource region. `DATE` type has no timezone component.
- **`service_costs JSONB`** — e.g. `{"EC2": 14.50, "RDS": 12.30, "S3": 0.02, "Lambda": 0.01}`. No schema changes when new services appear.
- **`total_cost_usd DECIMAL(10,4)`** — denormalized sum for quick queries and sorting.
- **`UNIQUE` on `billing_date`** — one record per day, upserted on refresh.
- **No `user_id`** — cost data is system-wide, not per-user.
- **No soft delete** — cost records are factual snapshots.
- **Optimistic locking** via `version` — protects against concurrent refresh writes.

### JPA Entity

```kotlin
@Entity
@Table(name = "cost_records")
class CostRecord(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "billing_date", nullable = false, unique = true)
    val billingDate: LocalDate,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "service_costs", nullable = false, columnDefinition = "jsonb")
    var serviceCosts: Map<String, BigDecimal> = emptyMap(),
    @Column(name = "total_cost_usd", nullable = false)
    var totalCostUsd: BigDecimal = BigDecimal.ZERO,
    @Column(name = "fetched_at", nullable = false)
    val fetchedAt: Instant = Instant.now(),
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Version val version: Long = 0
)
```

### Repository

```kotlin
interface CostRecordRepository : JpaRepository<CostRecord, UUID> {
    fun findFirstByOrderByBillingDateDesc(): CostRecord?
    fun findByBillingDateBetweenOrderByBillingDateDesc(from: LocalDate, to: LocalDate): List<CostRecord>
    fun findByBillingDate(date: LocalDate): CostRecord?
}
```

---

## Service Layer

### CostService Interface

```kotlin
interface CostService {
    fun refreshCosts(): CostRecord?
    fun getLatestCost(): CostRecord?
    fun getCostHistory(months: Int): List<CostRecord>
    fun getDailyCosts(from: LocalDate, to: LocalDate): List<CostRecord>
}
```

### AwsCostService (`@Profile("aws")`)

- Injects `CostExplorerClient` (AWS SDK v2) via a `@Bean` in a `@Configuration @Profile("aws")` config class
- `refreshCosts()`:
  - Calls `GetCostAndUsage` with `DAILY` granularity, `GROUP_BY SERVICE`, date range = 1st of current month → today + 1 day
  - For each day in the response, builds a `Map<String, BigDecimal>` of service name → cost
  - Computes `totalCostUsd` as sum of all service costs
  - Upserts by `billing_date`: finds existing record by date, updates if present, inserts if new
  - Logs at INFO: date range, total cost, number of services
  - Returns the most recent day's record
- `getLatestCost()`: delegates to repository `findFirstByOrderByBillingDateDesc()`
- `getCostHistory(months)`: queries from N months ago to today
- `getDailyCosts(from, to)`: direct date range query

On Cost Explorer API failure: logs at WARN with exception, returns null from `refreshCosts()`.

### LocalCostService (`@Profile("local | test")`)

- `refreshCosts()` returns null
- `getLatestCost()` returns null
- `getCostHistory()` / `getDailyCosts()` return empty lists
- App boots without AWS credentials

### CostRefreshTask (`@Profile("aws")`)

- Separate `@Component` (not mixed into the controller)
- `@Scheduled(cron = "0 0 6 * * *")` — daily at 6 AM UTC
- Calls `costService.refreshCosts()`
- Catches exceptions to prevent killing the scheduler thread
- Logs at INFO on success, WARN on failure

---

## API Layer

### Internal Endpoint

Add to existing `InternalSyncController`:

```
POST /api/internal/costs/refresh
```

Behind `InternalApiKeyFilter` (existing). Calls `costService.refreshCosts()`. Returns the record or 204 if null.

### GraphQL

Added to `admin.graphqls`:

```graphql
type CostRecord {
    id: ID!
    billingDate: String!
    serviceCosts: JSON!
    totalCostUsd: Float!
    fetchedAt: String!
}

type CostSummary {
    current: CostRecord
    monthlyTotals: [MonthlyCost!]!
}

type MonthlyCost {
    month: String!
    totalCostUsd: Float!
}
```

Added to existing `Query` type:

```graphql
costs(months: Int = 6): CostSummary!
```

**`CostResolver`** in `graphql/` package:
- `costs(months)` returns `CostSummary` with:
  - `current`: latest cost record (today or most recent)
  - `monthlyTotals`: daily records aggregated by month (sum `totalCostUsd`, group by year-month), for the requested number of months

### GraphQL Mutation

```graphql
type Mutation {
    refreshCosts: CostRecord
}
```

Used by the admin UI refresh button. Calls `costService.refreshCosts()`.

---

## Admin UI

### Stats Tab — Cost Summary Card

New card in the existing stats grid (alongside bookmark count, feed count, etc.):

- **Collapsed state:** Shows current month total (e.g., "$31.24 this month") with trend indicator vs previous month (↑/↓ percentage or "first month")
- **Expanded state:** Click to expand inline, revealing:
  1. **Per-service breakdown table** — columns: Service, Cost. Sorted descending by cost. Data from `current.serviceCosts` JSONB.
  2. **Monthly history table** — columns: Month, Total. Data from `monthlyTotals`. Month selector dropdown: 3 / 6 / 12 / All (default 6).
  3. **Refresh button** — calls `refreshCosts` GraphQL mutation, reloads data. Shows spinner during refresh.
- **No data state:** Card shows "No cost data available" with a refresh button.
- Expansion state is component-local (not persisted across page loads).

### Store Changes

Add to `admin.store.ts`:
- `costSummary: CostSummary | null` in state
- `loadCosts(months: number)` method — Apollo query
- `refreshCosts()` method — Apollo mutation, reloads costs on success

### Angular Components

- **`cost-card/`** — standalone component in `admin/` feature folder
  - `cost-card.ts` — component with expand/collapse, month selector, refresh button
  - Barrel export (`index.ts`)
- Integrated into `stats-panel.ts` as an additional card in the grid

---

## MCP Tool

**`CostTools.kt`** in `mcp/` package:

```kotlin
@Component
class CostTools(private val costService: CostService) {

    @Tool(description = "Get current cloud cost summary and monthly history. " +
        "Use when the user asks about cloud spending, billing, or cost trends.")
    fun getAwsCosts(months: Int = 6): String
}
```

- Calls `costService.getLatestCost()` and `costService.getCostHistory(months)`
- Formats as human-readable string: current day's per-service breakdown, total, and month-over-month history table
- When no data: returns "Cost tracking is only available in production" or "No cost data yet"
- Single tool covers all natural questions ("how much am I spending?", "show cost trends")

---

## IAM + Dependencies

### Terraform

New inline policy on the EC2 instance role in `terraform/iam.tf`:

```hcl
resource "aws_iam_role_policy" "ec2_cost_explorer" {
  name = "${var.project_name}-ec2-cost-explorer"
  role = aws_iam_role.ec2.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["ce:GetCostAndUsage"]
      Resource = ["*"]
    }]
  })
}
```

Cost Explorer actions don't support resource-level scoping — `Resource: *` is the tightest possible. Single action, least privilege.

No other Terraform changes. No new Lambda, EventBridge, or infrastructure.

### Gradle

```kotlin
implementation("software.amazon.awssdk:costexplorer")
```

Already under the existing BOM (`software.amazon.awssdk:bom`), no version needed.

---

## Testing

### Backend

| Test | What it verifies |
|------|------------------|
| `AwsCostServiceTest` | Mocked `CostExplorerClient`: correct date range, per-service JSONB mapping, total computation, upsert (second refresh updates, not duplicates) |
| `LocalCostServiceTest` | Returns null/empty for all methods |
| `CostRefreshTaskTest` | `@Scheduled` calls `refreshCosts()`, exception handling doesn't kill scheduler |
| `CostResolverTest` | GraphQL resolver returns `CostSummary` shape from mocked service |
| `CostToolsTest` | MCP tool formats output correctly, handles null/empty from local profile |

### Frontend

| Test | What it verifies |
|------|------------------|
| `cost-card` component | Renders current total, expand/collapse, breakdown table, history table, refresh button |
| Store | `loadCosts` populates state, `refreshCosts` triggers mutation and reloads |

### Smoke Test

On `aws` profile (Cognito active): authenticated GraphQL `costs` query returns valid response shape (no assertion on dollar amounts).

---

## Cross-Cutting Concerns

| Concern | Status |
|---------|--------|
| **Auth** | GraphQL `costs` query and `refreshCosts` mutation behind existing JWT auth. Internal refresh endpoint behind `InternalApiKeyFilter`. |
| **Secrets** | No new secrets. Cost Explorer uses EC2 instance role credentials (default chain). |
| **Logging** | INFO on successful refresh (date range, total, service count). WARN on API failure. No secrets in logs. |
| **Error handling** | `CostRefreshTask` catches all exceptions. UI shows `fetchedAt` so staleness is visible. |
| **Deployment** | `terraform apply` for IAM policy (no EC2 replacement, zero downtime), then `git push`. |
| **Profile gating** | `AwsCostService` + `CostRefreshTask` = `@Profile("aws")`. `LocalCostService` = `@Profile("local \| test")`. No wrong-profile loading. |
| **Idempotency** | Upsert by `billing_date` — safe to re-run refresh. |
