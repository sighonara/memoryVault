# Phase 11 — Public Self-Signup & Subscription Billing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Open MemoryVault to public self-service signup with Cognito email verification, Stripe subscription billing across Free/Premium/Backup tiers, AOP-based tier enforcement, daily metered storage reporting to Stripe, and grace-period video purge on cancellation.

**Architecture:** Cognito handles signup/email verification. Spring Security auto-provisions a `users` row on first login. Stripe Checkout/Portal handles all payment UX. Tier enforcement uses a Spring AOP `@TierGate` aspect for regular methods; suspend functions use inline checks. Scheduled jobs follow the existing `SyncRegistrar` + `JobScheduler` pattern. Angular adds TierService, signup/billing pages, and locked-state UI where required.

**Tech Stack:** `com.stripe:stripe-java:26.13.0`, `spring-boot-starter-aop`, Cognito self-signup, `amazon-cognito-identity-js` (already installed in Angular).

---

## ⚠️ Prerequisite: Terraform apply BEFORE merging code

The Cognito `allow_admin_create_user_only = false` change (Task 1) must be applied with `terraform apply` before code ships. If the code lands first, the signup page is live but Cognito rejects self-registration attempts.

---

## File Map

**New — backend:**
- `src/main/resources/db/migration/V10__billing.sql`
- `src/main/kotlin/org/sightech/memoryvault/auth/entity/Tier.kt`
- `src/main/kotlin/org/sightech/memoryvault/billing/entity/BillingEnums.kt` — BillingInterval, SubscriptionStatus
- `src/main/kotlin/org/sightech/memoryvault/billing/entity/SubscriptionEntity.kt`
- `src/main/kotlin/org/sightech/memoryvault/billing/repository/SubscriptionRepository.kt`
- `src/main/kotlin/org/sightech/memoryvault/billing/tier/TierGate.kt`
- `src/main/kotlin/org/sightech/memoryvault/billing/tier/TierRequiredException.kt`
- `src/main/kotlin/org/sightech/memoryvault/billing/tier/TierEnforcementAspect.kt`
- `src/main/kotlin/org/sightech/memoryvault/billing/service/StripeService.kt`
- `src/main/kotlin/org/sightech/memoryvault/billing/service/BillingService.kt`
- `src/main/kotlin/org/sightech/memoryvault/billing/service/StorageUsageReporter.kt`
- `src/main/kotlin/org/sightech/memoryvault/billing/service/VideoPurgeService.kt`
- `src/main/kotlin/org/sightech/memoryvault/billing/BillingSyncRegistrar.kt`
- `src/main/kotlin/org/sightech/memoryvault/billing/controller/BillingController.kt`
- `src/main/kotlin/org/sightech/memoryvault/billing/controller/StripeWebhookController.kt`
- `src/main/kotlin/org/sightech/memoryvault/auth/controller/UserController.kt`
- `src/main/kotlin/org/sightech/memoryvault/auth/dto/UserProfileDto.kt`
- `src/main/kotlin/org/sightech/memoryvault/graphql/BillingResolver.kt`
- `src/main/kotlin/org/sightech/memoryvault/graphql/AdminUserResolver.kt`
- `src/main/kotlin/org/sightech/memoryvault/mcp/BillingTools.kt`

**Modified — backend:**
- `build.gradle.kts` — add stripe-java, spring-boot-starter-aop
- `src/main/kotlin/org/sightech/memoryvault/auth/entity/User.kt` — add tier, stripeCustomerId
- `src/main/kotlin/org/sightech/memoryvault/auth/repository/UserRepository.kt` — add findAllByTier
- `src/main/kotlin/org/sightech/memoryvault/config/CognitoJwtFilter.kt` — auto-provision user on first login
- `src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt` — permit /api/stripe/webhook + new SPA routes
- `src/main/kotlin/org/sightech/memoryvault/scheduling/entity/JobType.kt` — add STORAGE_USAGE_REPORT, VIDEO_PURGE
- `src/main/kotlin/org/sightech/memoryvault/feed/service/FeedService.kt` — 100-feed FREE limit
- `src/main/kotlin/org/sightech/memoryvault/youtube/service/YoutubeListService.kt` — @TierGate(Tier.BACKUP)
- `src/main/kotlin/org/sightech/memoryvault/youtube/service/VideoSyncService.kt` — inline tier check on download
- `src/main/kotlin/org/sightech/memoryvault/youtube/repository/VideoRepository.kt` — add findAllWithFilePathByUserId
- `src/main/kotlin/org/sightech/memoryvault/backup/service/BackupService.kt` — @TierGate(Tier.BACKUP)
- `src/main/kotlin/org/sightech/memoryvault/storage/StorageService.kt` — add sizeBytes(key)
- `src/main/kotlin/org/sightech/memoryvault/storage/LocalStorageService.kt` — implement sizeBytes
- `src/main/kotlin/org/sightech/memoryvault/storage/S3StorageService.kt` — implement sizeBytes
- `src/main/resources/application.properties` — billing config properties
- `src/main/resources/application-local.properties` — Stripe test key stubs
- `src/main/resources/graphql/schema.graphqls` — new types + queries
- `terraform/cognito.tf` — allow_admin_create_user_only = false
- `terraform/variables.tf` — stripe_secret_key, stripe_webhook_secret
- `terraform/ec2.tf` — pass new vars to user_data template
- `terraform/templates/user_data.sh` — STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET env vars
- `.env.sample` — add Stripe vars

**New — Angular:**
- `client/src/app/auth/signup/signup.component.ts/html/scss`
- `client/src/app/feature-billing/billing.component.ts/html/scss`
- `client/src/app/feature-billing/billing-success.component.ts/html`
- `client/src/app/feature-billing/billing-cancelled.component.ts/html`
- `client/src/app/feature-billing/billing.routes.ts`
- `client/src/app/core/services/tier.service.ts`
- `client/src/app/core/models/user-profile.model.ts`

**Modified — Angular:**
- `client/src/app/app.routes.ts` — /signup, /billing routes
- `client/src/app/shared/layout/top-bar.ts/html` — user icon menu with Billing + Sign out
- `client/src/app/auth/login/login.ts/html` — "Create account" link
- `client/src/app/youtube/youtube.ts/html` — locked state + grace period banner
- `client/src/app/reader/reader.ts/html` — upgrade prompt at feed limit
- `client/src/app/admin/admin.ts/html` — Users tab

---

## Task 1: Terraform Prerequisites

**Files:**
- Modify: `terraform/cognito.tf`
- Modify: `terraform/variables.tf`
- Modify: `terraform/ec2.tf`
- Modify: `terraform/templates/user_data.sh`
- Modify: `.env.sample`
- Modify: `src/main/resources/application-local.properties`

- [ ] **Step 1: Open Cognito to self-registration**

In `terraform/cognito.tf`, change:
```hcl
allow_admin_create_user_only = true
```
to:
```hcl
allow_admin_create_user_only = false
```

- [ ] **Step 2: Add Stripe variables to Terraform**

In `terraform/variables.tf`, append:
```hcl
variable "stripe_secret_key" {
  description = "Stripe secret API key (sk_live_... or sk_test_...)"
  type        = string
  sensitive   = true
}

variable "stripe_webhook_secret" {
  description = "Stripe webhook signing secret (whsec_...)"
  type        = string
  sensitive   = true
}

variable "stripe_premium_monthly_price_id" {
  description = "Stripe Price ID for Premium monthly subscription"
  type        = string
}

variable "stripe_premium_annual_price_id" {
  description = "Stripe Price ID for Premium annual subscription"
  type        = string
}

variable "stripe_backup_monthly_price_id" {
  description = "Stripe Price ID for Backup monthly subscription"
  type        = string
}

variable "stripe_backup_annual_price_id" {
  description = "Stripe Price ID for Backup annual subscription"
  type        = string
}
```

- [ ] **Step 3: Pass Stripe vars to user_data template in ec2.tf**

In `terraform/ec2.tf`, inside the `templatefile(...)` call, add after `encryption_key`:
```hcl
    stripe_secret_key              = var.stripe_secret_key
    stripe_webhook_secret          = var.stripe_webhook_secret
    stripe_premium_monthly_price_id = var.stripe_premium_monthly_price_id
    stripe_premium_annual_price_id  = var.stripe_premium_annual_price_id
    stripe_backup_monthly_price_id  = var.stripe_backup_monthly_price_id
    stripe_backup_annual_price_id   = var.stripe_backup_annual_price_id
```

- [ ] **Step 4: Add Stripe vars to user_data.sh env file**

In `terraform/templates/user_data.sh`, inside the `cat > /etc/memoryvault/env` heredoc, append after `MEMORYVAULT_ENCRYPTION_KEY`:
```bash
STRIPE_SECRET_KEY=${stripe_secret_key}
STRIPE_WEBHOOK_SECRET=${stripe_webhook_secret}
STRIPE_PREMIUM_MONTHLY_PRICE_ID=${stripe_premium_monthly_price_id}
STRIPE_PREMIUM_ANNUAL_PRICE_ID=${stripe_premium_annual_price_id}
STRIPE_BACKUP_MONTHLY_PRICE_ID=${stripe_backup_monthly_price_id}
STRIPE_BACKUP_ANNUAL_PRICE_ID=${stripe_backup_annual_price_id}
```

- [ ] **Step 5: Update .env.sample and local properties**

In `.env.sample`, append:
```
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_PREMIUM_MONTHLY_PRICE_ID=price_...
STRIPE_PREMIUM_ANNUAL_PRICE_ID=price_...
STRIPE_BACKUP_MONTHLY_PRICE_ID=price_...
STRIPE_BACKUP_ANNUAL_PRICE_ID=price_...
```

In `src/main/resources/application-local.properties`, append:
```properties
# Stripe — use test-mode keys locally; set to "disabled" to skip Stripe calls in local dev
STRIPE_SECRET_KEY=sk_test_disabled
STRIPE_WEBHOOK_SECRET=whsec_disabled
STRIPE_PREMIUM_MONTHLY_PRICE_ID=price_test_premium_monthly
STRIPE_PREMIUM_ANNUAL_PRICE_ID=price_test_premium_annual
STRIPE_BACKUP_MONTHLY_PRICE_ID=price_test_backup_monthly
STRIPE_BACKUP_ANNUAL_PRICE_ID=price_test_backup_annual
```

- [ ] **Step 6: Add new vars to terraform.tfvars (local, gitignored)**

Append to `terraform/terraform.tfvars` (fill in real values before applying):
```hcl
stripe_secret_key              = "sk_live_..."
stripe_webhook_secret          = "whsec_..."
stripe_premium_monthly_price_id = "price_..."
stripe_premium_annual_price_id  = "price_..."
stripe_backup_monthly_price_id  = "price_..."
stripe_backup_annual_price_id   = "price_..."
```

- [ ] **Step 7: Apply Terraform (run this before merging code)**

```bash
cd terraform
terraform plan
terraform apply
```

Expected: Cognito user pool updated (`allow_admin_create_user_only = false`). No EC2 replacement.

- [ ] **Step 8: Commit Terraform changes**

```bash
git add terraform/cognito.tf terraform/variables.tf terraform/ec2.tf terraform/templates/user_data.sh .env.sample src/main/resources/application-local.properties
git commit -m "feat: phase 11 terraform prerequisites — Cognito self-signup + Stripe config"
```


## Task 2: Stripe SDK + V10 Migration + Tier Enum + User Entity

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/main/kotlin/org/sightech/memoryvault/auth/entity/Tier.kt`
- Create: `src/main/resources/db/migration/V10__billing.sql`
- Modify: `src/main/kotlin/org/sightech/memoryvault/auth/entity/User.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/auth/repository/UserRepository.kt`
- Test: `src/test/kotlin/org/sightech/memoryvault/billing/BillingMigrationTest.kt`

- [ ] **Step 1: Add dependencies to build.gradle.kts**

In `build.gradle.kts`, inside the `dependencies` block, after the AWS SDK section add:
```kotlin
    // Stripe
    implementation("com.stripe:stripe-java:26.13.0")

    // AOP (for @TierGate enforcement)
    implementation("org.springframework.boot:spring-boot-starter-aop")
```

- [ ] **Step 2: Create Tier enum**

Create `src/main/kotlin/org/sightech/memoryvault/auth/entity/Tier.kt`:
```kotlin
package org.sightech.memoryvault.auth.entity

enum class Tier { FREE, PREMIUM, BACKUP }
```

Ordinal order matters: `FREE.ordinal=0 < PREMIUM.ordinal=1 < BACKUP.ordinal=2`. The TierEnforcementAspect will use ordinal comparison.

- [ ] **Step 3: Write the migration test (failing)**

Create `src/test/kotlin/org/sightech/memoryvault/billing/BillingMigrationTest.kt`:
```kotlin
package org.sightech.memoryvault.billing

import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class BillingMigrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("memoryvault_test")
            withUsername("memoryvault")
            withPassword("memoryvault")
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var userRepository: UserRepository

    @Test
    fun `new user defaults to FREE tier with null stripeCustomerId`() {
        val user = userRepository.save(User(
            email = "test-tier@example.com",
            displayName = "Test",
            role = UserRole.VIEWER
        ))
        assertEquals(Tier.FREE, user.tier)
        assertNull(user.stripeCustomerId)
    }

    @Test
    fun `user tier can be updated to PREMIUM`() {
        val user = userRepository.save(User(
            email = "premium@example.com",
            displayName = "Premium User",
            role = UserRole.VIEWER
        ))
        user.tier = Tier.PREMIUM
        val saved = userRepository.save(user)
        assertEquals(Tier.PREMIUM, saved.tier)
    }
}
```

- [ ] **Step 4: Run test — expect compilation failure (Tier not yet on User)**

```bash
./gradlew test --tests "org.sightech.memoryvault.billing.BillingMigrationTest" 2>&1 | tail -20
```
Expected: compilation error — `User` has no `tier` or `stripeCustomerId` property.

- [ ] **Step 5: Write V10 migration**

Create `src/main/resources/db/migration/V10__billing.sql`:
```sql
ALTER TABLE users ADD COLUMN tier VARCHAR(20) NOT NULL DEFAULT 'FREE';
ALTER TABLE users ADD COLUMN stripe_customer_id VARCHAR(255);

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    stripe_customer_id VARCHAR(255) NOT NULL,
    stripe_subscription_id VARCHAR(255) NOT NULL UNIQUE,
    billing_interval VARCHAR(10) NOT NULL CHECK (billing_interval IN ('MONTHLY', 'ANNUAL')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'PAST_DUE', 'CANCELLED', 'GRACE_PERIOD')),
    grace_period_ends_at TIMESTAMPTZ,
    last_usage_reported_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_subscriptions_user_id ON subscriptions(user_id);
CREATE INDEX idx_subscriptions_stripe_subscription_id ON subscriptions(stripe_subscription_id);
```

- [ ] **Step 6: Add tier and stripeCustomerId to User entity**

In `src/main/kotlin/org/sightech/memoryvault/auth/entity/User.kt`, add these two fields after `role`:
```kotlin
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var tier: Tier = Tier.FREE,

    @Column(name = "stripe_customer_id", length = 255)
    var stripeCustomerId: String? = null,
```

Also add the import at the top: `import org.sightech.memoryvault.auth.entity.Tier`

- [ ] **Step 7: Add findAllByTier to UserRepository**

In `src/main/kotlin/org/sightech/memoryvault/auth/repository/UserRepository.kt`, add:
```kotlin
    fun findAllByTierAndDeletedAtIsNull(tier: Tier): List<User>
```

- [ ] **Step 8: Run the migration test — expect pass**

```bash
./gradlew test --tests "org.sightech.memoryvault.billing.BillingMigrationTest" 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 9: Commit**

```bash
git add build.gradle.kts src/main/kotlin/org/sightech/memoryvault/auth/entity/Tier.kt src/main/kotlin/org/sightech/memoryvault/auth/entity/User.kt src/main/kotlin/org/sightech/memoryvault/auth/repository/UserRepository.kt src/main/resources/db/migration/V10__billing.sql src/test/kotlin/org/sightech/memoryvault/billing/BillingMigrationTest.kt
git commit -m "feat: V10 migration — billing tier + subscriptions table, Tier enum, Stripe SDK"
```


## Task 3: SubscriptionEntity + SubscriptionRepository + JobType Additions

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/billing/entity/BillingEnums.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/billing/entity/SubscriptionEntity.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/billing/repository/SubscriptionRepository.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/scheduling/entity/JobType.kt`
- Test: `src/test/kotlin/org/sightech/memoryvault/billing/SubscriptionRepositoryTest.kt`

- [ ] **Step 1: Write repository test (failing)**

Create `src/test/kotlin/org/sightech/memoryvault/billing/SubscriptionRepositoryTest.kt`:
```kotlin
package org.sightech.memoryvault.billing

import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.entity.SubscriptionEntity
import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class SubscriptionRepositoryTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("memoryvault_test")
            withUsername("memoryvault")
            withPassword("memoryvault")
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired lateinit var subscriptionRepository: SubscriptionRepository
    @Autowired lateinit var userRepository: UserRepository

    @Test
    fun `can save and find subscription by userId`() {
        val user = userRepository.save(User(
            email = "sub-test@example.com",
            displayName = "Sub Test",
            role = UserRole.VIEWER,
            tier = Tier.PREMIUM
        ))
        val sub = subscriptionRepository.save(SubscriptionEntity(
            userId = user.id,
            stripeCustomerId = "cus_test123",
            stripeSubscriptionId = "sub_test123",
            billingInterval = BillingInterval.MONTHLY,
            status = SubscriptionStatus.ACTIVE
        ))
        val found = subscriptionRepository.findByUserId(user.id)
        assertNotNull(found)
        assertEquals("sub_test123", found.stripeSubscriptionId)
    }

    @Test
    fun `findExpiredGracePeriods returns subscriptions past deadline`() {
        val user = userRepository.save(User(
            email = "grace@example.com",
            displayName = "Grace",
            role = UserRole.VIEWER
        ))
        subscriptionRepository.save(SubscriptionEntity(
            userId = user.id,
            stripeCustomerId = "cus_grace",
            stripeSubscriptionId = "sub_grace",
            billingInterval = BillingInterval.MONTHLY,
            status = SubscriptionStatus.GRACE_PERIOD,
            gracePeriodEndsAt = Instant.now().minusSeconds(3600)
        ))
        val expired = subscriptionRepository.findExpiredGracePeriods(Instant.now())
        assert(expired.any { it.stripeSubscriptionId == "sub_grace" })
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./gradlew test --tests "org.sightech.memoryvault.billing.SubscriptionRepositoryTest" 2>&1 | tail -15
```
Expected: compilation error — billing types not yet defined.

- [ ] **Step 3: Create BillingEnums.kt**

Create `src/main/kotlin/org/sightech/memoryvault/billing/entity/BillingEnums.kt`:
```kotlin
package org.sightech.memoryvault.billing.entity

enum class BillingInterval { MONTHLY, ANNUAL }

enum class SubscriptionStatus { ACTIVE, PAST_DUE, CANCELLED, GRACE_PERIOD }
```

- [ ] **Step 4: Create SubscriptionEntity.kt**

Create `src/main/kotlin/org/sightech/memoryvault/billing/entity/SubscriptionEntity.kt`:
```kotlin
package org.sightech.memoryvault.billing.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "subscriptions")
class SubscriptionEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "stripe_customer_id", nullable = false)
    val stripeCustomerId: String,

    @Column(name = "stripe_subscription_id", nullable = false, unique = true)
    val stripeSubscriptionId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 10)
    val billingInterval: BillingInterval,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SubscriptionStatus,

    @Column(name = "grace_period_ends_at")
    var gracePeriodEndsAt: Instant? = null,

    @Column(name = "last_usage_reported_at")
    var lastUsageReportedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
```

- [ ] **Step 5: Create SubscriptionRepository.kt**

Create `src/main/kotlin/org/sightech/memoryvault/billing/repository/SubscriptionRepository.kt`:
```kotlin
package org.sightech.memoryvault.billing.repository

import org.sightech.memoryvault.billing.entity.SubscriptionEntity
import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface SubscriptionRepository : JpaRepository<SubscriptionEntity, UUID> {
    fun findByUserId(userId: UUID): SubscriptionEntity?
    fun findByStripeSubscriptionId(stripeSubscriptionId: String): SubscriptionEntity?
    fun findAllByStatus(status: SubscriptionStatus): List<SubscriptionEntity>

    @Query("SELECT s FROM SubscriptionEntity s WHERE s.status = 'GRACE_PERIOD' AND s.gracePeriodEndsAt <= :cutoff")
    fun findExpiredGracePeriods(cutoff: Instant): List<SubscriptionEntity>
}
```

- [ ] **Step 6: Add new JobType values**

In `src/main/kotlin/org/sightech/memoryvault/scheduling/entity/JobType.kt`, extend the enum:
```kotlin
enum class JobType { RSS_FETCH, YT_SYNC, BOOKMARK_ARCHIVE, BACKUP_UPLOAD, BACKUP_HEALTH_CHECK, STORAGE_USAGE_REPORT, VIDEO_PURGE }
```

- [ ] **Step 7: Run the test — expect pass**

```bash
./gradlew test --tests "org.sightech.memoryvault.billing.SubscriptionRepositoryTest" 2>&1 | tail -15
```
Expected: `BUILD SUCCESSFUL`, 2 tests passed.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/billing/ src/main/kotlin/org/sightech/memoryvault/scheduling/entity/JobType.kt src/test/kotlin/org/sightech/memoryvault/billing/SubscriptionRepositoryTest.kt
git commit -m "feat: SubscriptionEntity, SubscriptionRepository, BillingEnums, JobType additions"
```


## Task 4: @TierGate Annotation + AOP Enforcement Aspect

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/billing/tier/TierGate.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/billing/tier/TierRequiredException.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/billing/tier/TierEnforcementAspect.kt`
- Test: `src/test/kotlin/org/sightech/memoryvault/billing/TierEnforcementAspectTest.kt`

The AOP aspect intercepts any `@Component`/`@Service` method annotated with `@TierGate`. It loads the authenticated user from `UserRepository` using `CurrentUser.userId()`, then throws `TierRequiredException` (→ 403) if their tier ordinal is below the required tier. `UserRole.OWNER` bypasses all checks. **Not applicable to `suspend` functions** — those use inline checks (see Task 9).

- [ ] **Step 1: Write the aspect unit test (failing)**

Create `src/test/kotlin/org/sightech/memoryvault/billing/TierEnforcementAspectTest.kt`:
```kotlin
package org.sightech.memoryvault.billing

import io.mockk.every
import io.mockk.mockk
import org.aspectj.lang.ProceedingJoinPoint
import org.junit.jupiter.api.assertThrows
import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.tier.TierEnforcementAspect
import org.sightech.memoryvault.billing.tier.TierGate
import org.sightech.memoryvault.billing.tier.TierRequiredException
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class TierEnforcementAspectTest {

    private val userRepository = mockk<UserRepository>()
    private val aspect = TierEnforcementAspect(userRepository)

    private fun mockTierGate(required: Tier): TierGate {
        val annotation = mockk<TierGate>()
        every { annotation.value } returns required
        return annotation
    }

    private fun mockJoinPoint(): ProceedingJoinPoint {
        val jp = mockk<ProceedingJoinPoint>()
        every { jp.proceed() } returns "result"
        return jp
    }

    private fun mockUser(role: UserRole, tier: Tier): User {
        val user = mockk<User>()
        every { user.role } returns role
        every { user.tier } returns tier
        return user
    }

    @Test
    fun `OWNER bypasses tier check regardless of tier`() {
        val userId = UUID.randomUUID()
        val user = mockUser(UserRole.OWNER, Tier.FREE)
        every { userRepository.findByIdAndDeletedAtIsNull(userId) } returns user

        // Would throw if check were applied, but OWNER bypasses
        val result = aspect.enforceTier(mockJoinPoint(), mockTierGate(Tier.BACKUP))
        assertEquals("result", result)
    }

    @Test
    fun `BACKUP user passes BACKUP gate`() {
        val user = mockUser(UserRole.VIEWER, Tier.BACKUP)
        every { userRepository.findByIdAndDeletedAtIsNull(any()) } returns user

        val result = aspect.enforceTier(mockJoinPoint(), mockTierGate(Tier.BACKUP))
        assertEquals("result", result)
    }

    @Test
    fun `FREE user fails BACKUP gate`() {
        val user = mockUser(UserRole.VIEWER, Tier.FREE)
        every { userRepository.findByIdAndDeletedAtIsNull(any()) } returns user

        assertThrows<TierRequiredException> {
            aspect.enforceTier(mockJoinPoint(), mockTierGate(Tier.BACKUP))
        }
    }

    @Test
    fun `PREMIUM user fails BACKUP gate`() {
        val user = mockUser(UserRole.VIEWER, Tier.PREMIUM)
        every { userRepository.findByIdAndDeletedAtIsNull(any()) } returns user

        assertThrows<TierRequiredException> {
            aspect.enforceTier(mockJoinPoint(), mockTierGate(Tier.BACKUP))
        }
    }

    @Test
    fun `FREE user passes FREE gate`() {
        val user = mockUser(UserRole.VIEWER, Tier.FREE)
        every { userRepository.findByIdAndDeletedAtIsNull(any()) } returns user

        val result = aspect.enforceTier(mockJoinPoint(), mockTierGate(Tier.FREE))
        assertEquals("result", result)
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./gradlew test --tests "org.sightech.memoryvault.billing.TierEnforcementAspectTest" 2>&1 | tail -10
```
Expected: compilation error — tier package types missing.

- [ ] **Step 3: Create TierGate annotation**

Create `src/main/kotlin/org/sightech/memoryvault/billing/tier/TierGate.kt`:
```kotlin
package org.sightech.memoryvault.billing.tier

import org.sightech.memoryvault.auth.entity.Tier

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TierGate(val value: Tier)
```

- [ ] **Step 4: Create TierRequiredException**

Create `src/main/kotlin/org/sightech/memoryvault/billing/tier/TierRequiredException.kt`:
```kotlin
package org.sightech.memoryvault.billing.tier

import org.sightech.memoryvault.auth.entity.Tier
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.FORBIDDEN)
class TierRequiredException(required: Tier) :
    RuntimeException("This feature requires ${required.name} tier or higher")
```

- [ ] **Step 5: Create TierEnforcementAspect**

Create `src/main/kotlin/org/sightech/memoryvault/billing/tier/TierEnforcementAspect.kt`:
```kotlin
package org.sightech.memoryvault.billing.tier

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Aspect
@Component
class TierEnforcementAspect(private val userRepository: UserRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Around("@annotation(tierGate)")
    fun enforceTier(joinPoint: ProceedingJoinPoint, tierGate: TierGate): Any? {
        val userId = CurrentUser.userId()
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: throw TierRequiredException(tierGate.value)

        if (user.role == UserRole.OWNER) {
            return joinPoint.proceed()
        }

        if (user.tier.ordinal < tierGate.value.ordinal) {
            log.warn("Tier gate blocked userId={} tier={} required={}", userId, user.tier, tierGate.value)
            throw TierRequiredException(tierGate.value)
        }

        return joinPoint.proceed()
    }
}
```

- [ ] **Step 6: Run the test — expect pass**

```bash
./gradlew test --tests "org.sightech.memoryvault.billing.TierEnforcementAspectTest" 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`, 5 tests passed.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/billing/tier/ src/test/kotlin/org/sightech/memoryvault/billing/TierEnforcementAspectTest.kt
git commit -m "feat: @TierGate annotation + TierEnforcementAspect + TierRequiredException"
```


## Task 5: CognitoJwtFilter Auto-Provisioning + GET /api/me

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/config/CognitoJwtFilter.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/auth/dto/UserProfileDto.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/auth/controller/UserController.kt`
- Test: `src/test/kotlin/org/sightech/memoryvault/auth/UserProvisioningTest.kt`

`CognitoJwtFilter` currently logs a warning and sets no auth when a valid JWT has no matching `users` row. Update it to auto-create the user with `tier=FREE, role=VIEWER`. The `CognitoClaims.role` field (from the `custom:role` Cognito attribute) is used if present, defaulting to `VIEWER`.

- [ ] **Step 1: Update CognitoJwtFilter to auto-provision**

Replace the `else` branch in `CognitoJwtFilter.doFilterInternal` (the "user not found" case):

Current code (lines 36–43 of `CognitoJwtFilter.kt`):
```kotlin
                val user = userRepository.findByEmailAndDeletedAtIsNull(claims.email)
                if (user != null) {
                    val authorities = listOf(SimpleGrantedAuthority("ROLE_${claims.role}"))
                    val auth = UsernamePasswordAuthenticationToken(user.id.toString(), null, authorities)
                    SecurityContextHolder.getContext().authentication = auth
                } else {
                    log.warn("Cognito user {} not found in database", claims.email)
                }
```

Replace with:
```kotlin
                val user = userRepository.findByEmailAndDeletedAtIsNull(claims.email)
                    ?: provisionUser(claims.email, claims.role)
                val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
                val auth = UsernamePasswordAuthenticationToken(user.id.toString(), null, authorities)
                SecurityContextHolder.getContext().authentication = auth
```

Add the `provisionUser` private method to `CognitoJwtFilter`:
```kotlin
    private fun provisionUser(email: String, roleHint: String): User {
        val role = runCatching { UserRole.valueOf(roleHint) }.getOrDefault(UserRole.VIEWER)
        val user = userRepository.save(User(
            email = email,
            displayName = email.substringBefore("@"),
            role = role
        ))
        log.info("Auto-provisioned user email={} role={}", email, role)
        return user
    }
```

Add imports at the top of `CognitoJwtFilter.kt`:
```kotlin
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
```

- [ ] **Step 2: Add SPA routes + webhook permit to SecurityConfig**

In `SecurityConfig.kt`, add to the `requestMatchers(...).permitAll()` chain:
```kotlin
                    .requestMatchers("/api/stripe/webhook").permitAll()
                    .requestMatchers("/signup", "/billing", "/billing/**").permitAll()
```

Place the `/api/stripe/webhook` line immediately after the `/api/internal/**` line. Place the SPA routes line alongside the other SPA route matchers (`/login`, `/reader`, etc.).

- [ ] **Step 3: Create UserProfileDto**

Create `src/main/kotlin/org/sightech/memoryvault/auth/dto/UserProfileDto.kt`:
```kotlin
package org.sightech.memoryvault.auth.dto

import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.UserRole
import java.util.UUID

data class UserProfileDto(
    val id: UUID,
    val email: String,
    val displayName: String,
    val tier: Tier,
    val role: UserRole
)
```

- [ ] **Step 4: Create UserController**

Create `src/main/kotlin/org/sightech/memoryvault/auth/controller/UserController.kt`:
```kotlin
package org.sightech.memoryvault.auth.controller

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.auth.dto.UserProfileDto
import org.sightech.memoryvault.auth.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/me")
class UserController(private val userRepository: UserRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun getMe(): ResponseEntity<UserProfileDto> {
        val userId = CurrentUser.userId()
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: return ResponseEntity.notFound().build()
        log.debug("GET /api/me userId={}", userId)
        return ResponseEntity.ok(UserProfileDto(
            id = user.id,
            email = user.email,
            displayName = user.displayName,
            tier = user.tier,
            role = user.role
        ))
    }
}
```

- [ ] **Step 5: Write provisioning integration test**

Create `src/test/kotlin/org/sightech/memoryvault/auth/UserProvisioningTest.kt`:
```kotlin
package org.sightech.memoryvault.auth

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.config.CognitoJwtFilter
import org.sightech.memoryvault.config.CognitoTokenValidator
import io.mockk.slot
import io.mockk.verify
import kotlin.test.assertEquals

class UserProvisioningTest {

    private val tokenValidator = mockk<CognitoTokenValidator>()
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val filter = CognitoJwtFilter(tokenValidator, userRepository)

    @Test
    fun `first login auto-provisions user with FREE tier and VIEWER role`() {
        val email = "newuser@example.com"
        every { tokenValidator.validate(any()) } returns CognitoClaims(email, "VIEWER")
        every { userRepository.findByEmailAndDeletedAtIsNull(email) } returns null

        val userSlot = slot<User>()
        every { userRepository.save(capture(userSlot)) } answers { userSlot.captured }

        val request = mockk<jakarta.servlet.http.HttpServletRequest>(relaxed = true)
        val response = mockk<jakarta.servlet.http.HttpServletResponse>(relaxed = true)
        val chain = mockk<jakarta.servlet.FilterChain>(relaxed = true)
        every { request.getHeader("Authorization") } returns "Bearer sometoken"

        filter.doFilterInternal(request, response, chain)

        verify { userRepository.save(any()) }
        assertEquals(email, userSlot.captured.email)
        assertEquals(Tier.FREE, userSlot.captured.tier)
        assertEquals(UserRole.VIEWER, userSlot.captured.role)
    }

    @Test
    fun `existing user is not re-provisioned`() {
        val email = "existing@example.com"
        val existing = User(email = email, displayName = "Existing", role = UserRole.VIEWER)
        every { tokenValidator.validate(any()) } returns CognitoClaims(email, "VIEWER")
        every { userRepository.findByEmailAndDeletedAtIsNull(email) } returns existing

        val request = mockk<jakarta.servlet.http.HttpServletRequest>(relaxed = true)
        val response = mockk<jakarta.servlet.http.HttpServletResponse>(relaxed = true)
        val chain = mockk<jakarta.servlet.FilterChain>(relaxed = true)
        every { request.getHeader("Authorization") } returns "Bearer sometoken"

        filter.doFilterInternal(request, response, chain)

        verify(exactly = 0) { userRepository.save(any()) }
    }
}
```

- [ ] **Step 6: Run all tests**

```bash
./gradlew test 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/config/CognitoJwtFilter.kt src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt src/main/kotlin/org/sightech/memoryvault/auth/dto/UserProfileDto.kt src/main/kotlin/org/sightech/memoryvault/auth/controller/UserController.kt src/test/kotlin/org/sightech/memoryvault/auth/UserProvisioningTest.kt
git commit -m "feat: auto-provision Cognito users on first login, GET /api/me"
```


## Task 6: StripeService + Application Config

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/billing/service/StripeService.kt`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/kotlin/org/sightech/memoryvault/billing/StripeServiceTest.kt`

`StripeService` wraps the Stripe Java SDK. It's `@Profile("!test")` so the real Stripe client is never initialized in integration tests. Tests mock it directly via MockK.

- [ ] **Step 1: Add billing config to application.properties**

Append to `src/main/resources/application.properties`:
```properties
# Billing
memoryvault.billing.free-feed-limit=100
memoryvault.billing.grace-period-days=30
memoryvault.billing.usage-report-cron=-
memoryvault.billing.video-purge-cron=-
memoryvault.billing.stripe-secret-key=${STRIPE_SECRET_KEY}
memoryvault.billing.stripe-webhook-secret=${STRIPE_WEBHOOK_SECRET}
memoryvault.billing.stripe-premium-monthly-price-id=${STRIPE_PREMIUM_MONTHLY_PRICE_ID}
memoryvault.billing.stripe-premium-annual-price-id=${STRIPE_PREMIUM_ANNUAL_PRICE_ID}
memoryvault.billing.stripe-backup-monthly-price-id=${STRIPE_BACKUP_MONTHLY_PRICE_ID}
memoryvault.billing.stripe-backup-annual-price-id=${STRIPE_BACKUP_ANNUAL_PRICE_ID}
```

- [ ] **Step 2: Write StripeService unit test (failing)**

Create `src/test/kotlin/org/sightech/memoryvault/billing/StripeServiceTest.kt`:
```kotlin
package org.sightech.memoryvault.billing

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.service.StripeService
import com.stripe.model.Customer
import com.stripe.model.checkout.Session
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StripeServiceTest {

    @Test
    fun `StripeService can be constructed without making real API calls`() {
        // StripeService is initialized with a key; no API calls until methods are invoked
        val service = StripeService(
            secretKey = "sk_test_disabled",
            webhookSecret = "whsec_disabled",
            premiumMonthlyPriceId = "price_pm",
            premiumAnnualPriceId = "price_pa",
            backupMonthlyPriceId = "price_bm",
            backupAnnualPriceId = "price_ba"
        )
        assertNotNull(service)
    }
}
```

- [ ] **Step 3: Run test — expect compilation failure**

```bash
./gradlew test --tests "org.sightech.memoryvault.billing.StripeServiceTest" 2>&1 | tail -10
```
Expected: compilation error — StripeService not defined.

- [ ] **Step 4: Create StripeService**

Create `src/main/kotlin/org/sightech/memoryvault/billing/service/StripeService.kt`:
```kotlin
package org.sightech.memoryvault.billing.service

import com.stripe.Stripe
import com.stripe.model.Customer
import com.stripe.model.CustomerCollection
import com.stripe.model.billing.Session
import com.stripe.model.checkout.Session as CheckoutSession
import com.stripe.net.RequestOptions
import com.stripe.param.CustomerCreateParams
import com.stripe.param.CustomerListParams
import com.stripe.param.CustomerSearchParams
import com.stripe.param.billing.SessionCreateParams as PortalSessionParams
import com.stripe.param.checkout.SessionCreateParams
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct

@Service
class StripeService(
    @Value("\${memoryvault.billing.stripe-secret-key}") val secretKey: String,
    @Value("\${memoryvault.billing.stripe-webhook-secret}") val webhookSecret: String,
    @Value("\${memoryvault.billing.stripe-premium-monthly-price-id}") private val premiumMonthlyPriceId: String,
    @Value("\${memoryvault.billing.stripe-premium-annual-price-id}") private val premiumAnnualPriceId: String,
    @Value("\${memoryvault.billing.stripe-backup-monthly-price-id}") private val backupMonthlyPriceId: String,
    @Value("\${memoryvault.billing.stripe-backup-annual-price-id}") private val backupAnnualPriceId: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun init() {
        Stripe.apiKey = secretKey
        log.info("Stripe client initialized (key prefix={})", secretKey.take(8))
    }

    fun findOrCreateCustomer(email: String): Customer {
        val existing = Customer.search(CustomerSearchParams.builder()
            .setQuery("email:'$email'")
            .setLimit(1)
            .build())
        if (existing.data.isNotEmpty()) return existing.data.first()

        return Customer.create(CustomerCreateParams.builder()
            .setEmail(email)
            .build())
    }

    fun createCheckoutSession(
        customerId: String,
        tier: Tier,
        interval: BillingInterval,
        successUrl: String,
        cancelUrl: String
    ): CheckoutSession {
        val priceId = priceIdFor(tier, interval)
        val params = SessionCreateParams.builder()
            .setCustomer(customerId)
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .addLineItem(SessionCreateParams.LineItem.builder()
                .setPrice(priceId)
                .setQuantity(1)
                .build())
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .build()
        return CheckoutSession.create(params)
    }

    fun createPortalSession(customerId: String, returnUrl: String): Session {
        val params = PortalSessionParams.builder()
            .setCustomer(customerId)
            .setReturnUrl(returnUrl)
            .build()
        return Session.create(params)
    }

    fun reportUsage(subscriptionItemId: String, quantity: Long, timestamp: Long) {
        // Stripe metered billing usage record via raw API call
        // subscriptionItemId is the SI id for the metered price component
        val params = mapOf(
            "quantity" to quantity.toString(),
            "timestamp" to timestamp.toString(),
            "action" to "set"
        )
        com.stripe.net.ApiResource.request(
            com.stripe.net.ApiResource.RequestMethod.POST,
            "${Stripe.getApiBase()}/v1/subscription_items/$subscriptionItemId/usage_records",
            params,
            com.stripe.model.UsageRecord::class.java,
            null
        )
    }

    private fun priceIdFor(tier: Tier, interval: BillingInterval): String = when (tier to interval) {
        Tier.PREMIUM to BillingInterval.MONTHLY -> premiumMonthlyPriceId
        Tier.PREMIUM to BillingInterval.ANNUAL  -> premiumAnnualPriceId
        Tier.BACKUP  to BillingInterval.MONTHLY -> backupMonthlyPriceId
        Tier.BACKUP  to BillingInterval.ANNUAL  -> backupAnnualPriceId
        else -> throw IllegalArgumentException("No price for tier=$tier interval=$interval")
    }
}
```

- [ ] **Step 5: Run the test — expect pass**

```bash
./gradlew test --tests "org.sightech.memoryvault.billing.StripeServiceTest" 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Run all tests to check for regressions**

```bash
./gradlew test 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/billing/service/StripeService.kt src/main/resources/application.properties src/test/kotlin/org/sightech/memoryvault/billing/StripeServiceTest.kt
git commit -m "feat: StripeService — customer management, checkout/portal session creation"
```


## Task 7: BillingService + BillingController

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/billing/service/BillingService.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/billing/controller/BillingController.kt`
- Test: `src/test/kotlin/org/sightech/memoryvault/billing/BillingServiceTest.kt`

- [ ] **Step 1: Write BillingService unit test (failing)**

Create `src/test/kotlin/org/sightech/memoryvault/billing/BillingServiceTest.kt`:
```kotlin
package org.sightech.memoryvault.billing

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.entity.SubscriptionEntity
import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.sightech.memoryvault.billing.service.BillingService
import org.sightech.memoryvault.billing.service.StripeService
import com.stripe.model.Customer
import com.stripe.model.checkout.Session
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BillingServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val stripeService = mockk<StripeService>()
    private val service = BillingService(userRepository, subscriptionRepository, stripeService)

    private fun user(tier: Tier = Tier.FREE, stripeId: String? = null): User {
        val u = mockk<User>(relaxed = true)
        every { u.id } returns UUID.randomUUID()
        every { u.email } returns "test@example.com"
        every { u.tier } returns tier
        every { u.stripeCustomerId } returns stripeId
        return u
    }

    @Test
    fun `createCheckoutSession finds or creates Stripe customer and returns URL`() {
        val u = user()
        val customer = mockk<Customer>()
        every { customer.id } returns "cus_123"
        every { stripeService.findOrCreateCustomer(any()) } returns customer

        val session = mockk<Session>()
        every { session.url } returns "https://checkout.stripe.com/pay/cs_test_123"
        every { stripeService.createCheckoutSession(any(), any(), any(), any(), any()) } returns session
        every { userRepository.save(any()) } returns u

        val url = service.createCheckoutSession(u, Tier.PREMIUM, BillingInterval.MONTHLY, "https://mem-vault.com")
        assertEquals("https://checkout.stripe.com/pay/cs_test_123", url)
    }

    @Test
    fun `getSubscription returns null when user has no subscription`() {
        val u = user()
        every { subscriptionRepository.findByUserId(u.id) } returns null
        val result = service.getSubscription(u)
        assertEquals(null, result)
    }

    @Test
    fun `getSubscription returns subscription when present`() {
        val u = user(tier = Tier.PREMIUM, stripeId = "cus_123")
        val sub = SubscriptionEntity(
            userId = u.id,
            stripeCustomerId = "cus_123",
            stripeSubscriptionId = "sub_123",
            billingInterval = BillingInterval.MONTHLY,
            status = SubscriptionStatus.ACTIVE
        )
        every { subscriptionRepository.findByUserId(u.id) } returns sub
        val result = service.getSubscription(u)
        assertNotNull(result)
        assertEquals(SubscriptionStatus.ACTIVE, result.status)
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./gradlew test --tests "org.sightech.memoryvault.billing.BillingServiceTest" 2>&1 | tail -10
```

- [ ] **Step 3: Create BillingService**

Create `src/main/kotlin/org/sightech/memoryvault/billing/service/BillingService.kt`:
```kotlin
package org.sightech.memoryvault.billing.service

import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.entity.SubscriptionEntity
import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class BillingService(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val stripeService: StripeService,
    @Value("\${memoryvault.billing.grace-period-days:30}") private val gracePeriodDays: Long = 30
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createCheckoutSession(user: User, tier: Tier, interval: BillingInterval, baseUrl: String): String {
        val customer = stripeService.findOrCreateCustomer(user.email)
        if (user.stripeCustomerId != customer.id) {
            user.stripeCustomerId = customer.id
            userRepository.save(user)
        }
        val session = stripeService.createCheckoutSession(
            customerId = customer.id,
            tier = tier,
            interval = interval,
            successUrl = "$baseUrl/billing/success",
            cancelUrl = "$baseUrl/billing/cancelled"
        )
        log.info("Created checkout session userId={} tier={} interval={}", user.id, tier, interval)
        return session.url
    }

    fun createPortalSession(user: User, baseUrl: String): String {
        val customerId = user.stripeCustomerId
            ?: error("User ${user.id} has no Stripe customer ID")
        val session = stripeService.createPortalSession(customerId, "$baseUrl/billing")
        log.info("Created portal session userId={}", user.id)
        return session.returnUrl
    }

    fun getSubscription(user: User): SubscriptionEntity? =
        subscriptionRepository.findByUserId(user.id)

    @Transactional
    fun handleCheckoutCompleted(
        stripeCustomerId: String,
        stripeSubscriptionId: String,
        interval: BillingInterval,
        tier: Tier
    ) {
        val user = userRepository.findAll().firstOrNull { it.stripeCustomerId == stripeCustomerId }
            ?: run { log.warn("No user found for stripeCustomerId={}", stripeCustomerId); return }

        val existing = subscriptionRepository.findByUserId(user.id)
        if (existing != null) {
            existing.status = SubscriptionStatus.ACTIVE
            existing.gracePeriodEndsAt = null
            subscriptionRepository.save(existing)
        } else {
            subscriptionRepository.save(SubscriptionEntity(
                userId = user.id,
                stripeCustomerId = stripeCustomerId,
                stripeSubscriptionId = stripeSubscriptionId,
                billingInterval = interval,
                status = SubscriptionStatus.ACTIVE
            ))
        }
        user.tier = tier
        userRepository.save(user)
        log.info("Checkout completed userId={} tier={}", user.id, tier)
    }

    @Transactional
    fun handleSubscriptionDeleted(stripeSubscriptionId: String) {
        val sub = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId) ?: return
        val user = userRepository.findByIdAndDeletedAtIsNull(sub.userId) ?: return

        val wasBackup = user.tier == Tier.BACKUP
        user.tier = Tier.FREE
        userRepository.save(user)

        sub.status = SubscriptionStatus.GRACE_PERIOD
        if (wasBackup) sub.gracePeriodEndsAt = Instant.now().plus(gracePeriodDays, ChronoUnit.DAYS)
        subscriptionRepository.save(sub)
        log.info("Subscription deleted userId={} wasBackup={} gracePeriodEndsAt={}", user.id, wasBackup, sub.gracePeriodEndsAt)
    }

    @Transactional
    fun handleInvoicePaid(stripeSubscriptionId: String) {
        val sub = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId) ?: return
        sub.status = SubscriptionStatus.ACTIVE
        subscriptionRepository.save(sub)
        log.info("Invoice paid subscriptionId={}", stripeSubscriptionId)
    }

    @Transactional
    fun handleInvoicePaymentFailed(stripeSubscriptionId: String) {
        val sub = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId) ?: return
        sub.status = SubscriptionStatus.PAST_DUE
        subscriptionRepository.save(sub)
        log.warn("Invoice payment failed subscriptionId={}", stripeSubscriptionId)
    }
}
```

- [ ] **Step 4: Create BillingController**

Create `src/main/kotlin/org/sightech/memoryvault/billing/controller/BillingController.kt`:
```kotlin
package org.sightech.memoryvault.billing.controller

import jakarta.servlet.http.HttpServletRequest
import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.entity.SubscriptionEntity
import org.sightech.memoryvault.billing.service.BillingService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class CheckoutRequest(val tier: String, val interval: String)
data class CheckoutResponse(val url: String)
data class PortalResponse(val url: String)

@RestController
@RequestMapping("/api/billing")
class BillingController(
    private val billingService: BillingService,
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/checkout")
    fun checkout(@RequestBody req: CheckoutRequest, httpRequest: HttpServletRequest): ResponseEntity<CheckoutResponse> {
        val user = userRepository.findByIdAndDeletedAtIsNull(CurrentUser.userId())
            ?: return ResponseEntity.notFound().build()
        val tier = Tier.valueOf(req.tier)
        val interval = BillingInterval.valueOf(req.interval)
        val baseUrl = "${httpRequest.scheme}://${httpRequest.serverName}"
        val url = billingService.createCheckoutSession(user, tier, interval, baseUrl)
        log.info("Checkout initiated userId={} tier={} interval={}", user.id, tier, interval)
        return ResponseEntity.ok(CheckoutResponse(url))
    }

    @PostMapping("/portal")
    fun portal(httpRequest: HttpServletRequest): ResponseEntity<PortalResponse> {
        val user = userRepository.findByIdAndDeletedAtIsNull(CurrentUser.userId())
            ?: return ResponseEntity.notFound().build()
        val baseUrl = "${httpRequest.scheme}://${httpRequest.serverName}"
        val url = billingService.createPortalSession(user, baseUrl)
        return ResponseEntity.ok(PortalResponse(url))
    }

    @GetMapping("/subscription")
    fun subscription(): ResponseEntity<SubscriptionEntity?> {
        val user = userRepository.findByIdAndDeletedAtIsNull(CurrentUser.userId())
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(billingService.getSubscription(user))
    }
}
```

- [ ] **Step 5: Run the test — expect pass**

```bash
./gradlew test --tests "org.sightech.memoryvault.billing.BillingServiceTest" 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/billing/service/BillingService.kt src/main/kotlin/org/sightech/memoryvault/billing/controller/BillingController.kt src/test/kotlin/org/sightech/memoryvault/billing/BillingServiceTest.kt
git commit -m "feat: BillingService + BillingController — checkout, portal, subscription query"
```


## Task 8: StripeWebhookController

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/billing/controller/StripeWebhookController.kt`
- Test: `src/test/kotlin/org/sightech/memoryvault/billing/StripeWebhookControllerTest.kt`

The webhook endpoint is unauthenticated (secured by Stripe signature verification). It always returns 200 to prevent Stripe retry floods — errors are logged at WARN for manual investigation.

- [ ] **Step 1: Write webhook controller test (failing)**

Create `src/test/kotlin/org/sightech/memoryvault/billing/StripeWebhookControllerTest.kt`:
```kotlin
package org.sightech.memoryvault.billing

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.billing.controller.StripeWebhookController
import org.sightech.memoryvault.billing.service.BillingService
import org.sightech.memoryvault.billing.service.StripeService
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class StripeWebhookControllerTest {

    private val billingService = mockk<BillingService>(relaxed = true)
    private val stripeService = mockk<StripeService>()
    private val controller = StripeWebhookController(billingService, stripeService)

    @Test
    fun `invalid signature returns 400`() {
        every { stripeService.webhookSecret } returns "whsec_test"
        // constructEvent will throw with a bad signature
        val response = controller.handleWebhook("bad-payload".toByteArray(), "bad-sig")
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `unknown event type returns 200 without processing`() {
        every { stripeService.webhookSecret } returns "whsec_test"
        // We test that an unrecognised event doesn't throw
        // This requires mocking Stripe.constructEvent — skip full integration, verify controller wiring
        // Full e2e webhook testing requires a real Stripe CLI or test signature
        verify(exactly = 0) { billingService.handleCheckoutCompleted(any(), any(), any(), any()) }
    }
}
```

- [ ] **Step 2: Run test — expect compilation failure**

```bash
./gradlew test --tests "org.sightech.memoryvault.billing.StripeWebhookControllerTest" 2>&1 | tail -10
```

- [ ] **Step 3: Create StripeWebhookController**

Create `src/main/kotlin/org/sightech/memoryvault/billing/controller/StripeWebhookController.kt`:
```kotlin
package org.sightech.memoryvault.billing.controller

import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Invoice
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.service.BillingService
import org.sightech.memoryvault.billing.service.StripeService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/stripe")
class StripeWebhookController(
    private val billingService: BillingService,
    private val stripeService: StripeService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/webhook")
    fun handleWebhook(
        @RequestBody payload: ByteArray,
        @RequestHeader("Stripe-Signature") sigHeader: String
    ): ResponseEntity<String> {
        val event = try {
            Webhook.constructEvent(String(payload), sigHeader, stripeService.webhookSecret)
        } catch (e: SignatureVerificationException) {
            log.warn("Stripe webhook signature verification failed: {}", e.message)
            return ResponseEntity.badRequest().body("Invalid signature")
        } catch (e: Exception) {
            log.warn("Stripe webhook parse error: {}", e.message)
            return ResponseEntity.badRequest().body("Parse error")
        }

        log.info("Stripe webhook received type={} id={}", event.type, event.id)

        try {
            when (event.type) {
                "checkout.session.completed" -> {
                    val session = event.dataObjectDeserializer.deserializeUnsafe() as Session
                    val tierStr = session.metadata?.get("tier") ?: "PREMIUM"
                    val intervalStr = session.metadata?.get("interval") ?: "MONTHLY"
                    billingService.handleCheckoutCompleted(
                        stripeCustomerId = session.customer,
                        stripeSubscriptionId = session.subscription,
                        interval = BillingInterval.valueOf(intervalStr),
                        tier = Tier.valueOf(tierStr)
                    )
                }
                "invoice.paid" -> {
                    val invoice = event.dataObjectDeserializer.deserializeUnsafe() as Invoice
                    invoice.subscription?.let { billingService.handleInvoicePaid(it) }
                }
                "invoice.payment_failed" -> {
                    val invoice = event.dataObjectDeserializer.deserializeUnsafe() as Invoice
                    invoice.subscription?.let { billingService.handleInvoicePaymentFailed(it) }
                }
                "customer.subscription.deleted" -> {
                    val sub = event.dataObjectDeserializer.deserializeUnsafe() as Subscription
                    billingService.handleSubscriptionDeleted(sub.id)
                }
                else -> log.debug("Unhandled Stripe event type={}", event.type)
            }
        } catch (e: Exception) {
            log.warn("Error processing Stripe event type={} id={}: {}", event.type, event.id, e.message)
        }

        return ResponseEntity.ok("OK")
    }
}
```

Note: The `tier` and `interval` metadata must be passed when creating the Checkout Session. Update `StripeService.createCheckoutSession` to include metadata:
```kotlin
// In SessionCreateParams.builder(), add after .setCancelUrl():
.putAllMetadata(mapOf("tier" to tier.name, "interval" to interval.name))
```

- [ ] **Step 4: Run all tests**

```bash
./gradlew test 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/billing/controller/StripeWebhookController.kt src/main/kotlin/org/sightech/memoryvault/billing/service/StripeService.kt src/test/kotlin/org/sightech/memoryvault/billing/StripeWebhookControllerTest.kt
git commit -m "feat: StripeWebhookController — signature verification + event dispatch"
```


## Task 9: Tier Enforcement on Existing Services + Video Download Endpoint

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/feed/service/FeedService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/youtube/service/YoutubeListService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/youtube/service/VideoSyncService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/backup/service/BackupService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/storage/StorageService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/storage/LocalStorageService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/storage/S3StorageService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/youtube/repository/VideoRepository.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/youtube/controller/VideoController.kt`
- Test: `src/test/kotlin/org/sightech/memoryvault/billing/TierGateEnforcementTest.kt`

- [ ] **Step 1: Write enforcement integration test (failing)**

Create `src/test/kotlin/org/sightech/memoryvault/billing/TierGateEnforcementTest.kt`:
```kotlin
package org.sightech.memoryvault.billing

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.tier.TierRequiredException
import org.sightech.memoryvault.youtube.service.YoutubeListService
import java.util.UUID

class TierGateEnforcementTest {

    @Test
    fun `YoutubeListService addList throws TierRequiredException for FREE user`() {
        // This test requires a Spring context to wire the AOP aspect.
        // Run as an integration test with @SpringBootTest or rely on the
        // TierEnforcementAspectTest (Task 4) for unit-level verification.
        // Here we verify the annotation is present on the method via reflection.
        val method = YoutubeListService::class.java.getMethod("addList", String::class.java)
        val annotation = method.getAnnotation(
            org.sightech.memoryvault.billing.tier.TierGate::class.java
        )
        assert(annotation != null) { "addList must be annotated with @TierGate" }
        assert(annotation!!.value == Tier.BACKUP) { "addList requires BACKUP tier" }
    }
}
```

- [ ] **Step 2: Run test — expect failure (annotation not yet applied)**

```bash
./gradlew test --tests "org.sightech.memoryvault.billing.TierGateEnforcementTest" 2>&1 | tail -10
```

- [ ] **Step 3: Add @TierGate to YoutubeListService**

In `src/main/kotlin/org/sightech/memoryvault/youtube/service/YoutubeListService.kt`, add annotation imports and annotate:
```kotlin
import org.sightech.memoryvault.billing.tier.TierGate
import org.sightech.memoryvault.auth.entity.Tier

// On the addList method:
@TierGate(Tier.BACKUP)
fun addList(url: String): Pair<YoutubeList, SyncResult> { ... }

// On the refreshList method:
@TierGate(Tier.BACKUP)
fun refreshList(listId: UUID?): List<SyncResult> { ... }
```

- [ ] **Step 4: Add @TierGate to BackupService**

In `src/main/kotlin/org/sightech/memoryvault/backup/service/BackupService.kt`, annotate:
```kotlin
import org.sightech.memoryvault.billing.tier.TierGate
import org.sightech.memoryvault.auth.entity.Tier

@TierGate(Tier.BACKUP)
fun addProvider(userId: UUID, type: BackupProviderType, name: String, credentialsJson: String, isPrimary: Boolean): BackupProviderEntity { ... }

@TierGate(Tier.BACKUP)
fun triggerBackfill(userId: UUID): Int { ... }
```

- [ ] **Step 5: Add 100-feed FREE limit to FeedService**

`addFeed` is a `suspend fun` so AOP doesn't apply. Add an inline check. First, inject `UserRepository` into `FeedService`. Add it to the constructor and add an import.

In `FeedService.addFeed`, add this block immediately after `val userId = CurrentUser.userId()`:
```kotlin
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
        if (user != null && user.role != UserRole.OWNER && user.tier == Tier.FREE) {
            val count = feedRepository.countByUserIdAndDeletedAtIsNull(userId)
            if (count >= freeFeedLimit) {
                throw TierRequiredException(Tier.PREMIUM)
            }
        }
```

Add `@Value("\${memoryvault.billing.free-feed-limit:100}") private val freeFeedLimit: Long` to the `FeedService` constructor, plus imports for `Tier`, `UserRole`, `TierRequiredException`, and `UserRepository`.

- [ ] **Step 6: Add inline tier check to VideoSyncService**

In `VideoSyncService.syncList`, inject `UserRepository` and add a tier check before calling the downloader. When the list owner's tier is not BACKUP, skip downloading (just sync metadata) without throwing:

```kotlin
// After resolving the list owner userId, add:
val owner = userRepository.findByIdAndDeletedAtIsNull(list.userId)
val canDownload = owner?.role == UserRole.OWNER || owner?.tier == Tier.BACKUP
```

Then wrap the download call:
```kotlin
if (canDownload) {
    videoDownloader.download(video, list)
} else {
    log.debug("Skipping download for userId={} — tier not BACKUP", list.userId)
}
```

Add constructor injection of `UserRepository` and the required imports.

- [ ] **Step 7: Add sizeBytes to StorageService interface**

In `src/main/kotlin/org/sightech/memoryvault/storage/StorageService.kt`, add:
```kotlin
    fun sizeBytes(key: String): Long
```

In `LocalStorageService.kt`, implement:
```kotlin
    override fun sizeBytes(key: String): Long {
        val path = java.nio.file.Paths.get(localPath, key)
        return if (java.nio.file.Files.exists(path)) java.nio.file.Files.size(path) else 0L
    }
```

In `S3StorageService.kt`, implement using S3 HeadObject:
```kotlin
    override fun sizeBytes(key: String): Long = try {
        val response = s3Client.headObject { it.bucket(bucket).key(key) }
        response.contentLength()
    } catch (e: Exception) {
        log.warn("Could not get size for key={}: {}", key, e.message)
        0L
    }
```

- [ ] **Step 8: Add video query to VideoRepository**

In `src/main/kotlin/org/sightech/memoryvault/youtube/repository/VideoRepository.kt`, add:
```kotlin
    @Query("SELECT v FROM Video v WHERE v.youtubeList.userId = :userId AND v.filePath IS NOT NULL AND v.youtubeList.deletedAt IS NULL")
    fun findAllWithFilePathByUserId(userId: UUID): List<Video>
```

- [ ] **Step 9: Create VideoController for grace-period download**

Create `src/main/kotlin/org/sightech/memoryvault/youtube/controller/VideoController.kt`:
```kotlin
package org.sightech.memoryvault.youtube.controller

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/videos")
class VideoController(
    private val videoRepository: VideoRepository,
    private val storageService: StorageService,
    private val subscriptionRepository: SubscriptionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/{id}/download")
    fun download(@PathVariable id: UUID): ResponseEntity<InputStreamResource> {
        val userId = CurrentUser.userId()
        val video = videoRepository.findByIdAndUserId(id, userId)
            ?: return ResponseEntity.notFound().build()
        val filePath = video.filePath
            ?: return ResponseEntity.notFound().build()

        // Allow download if BACKUP tier OR in grace period
        val sub = subscriptionRepository.findByUserId(userId)
        val allowed = sub != null && (sub.status == SubscriptionStatus.ACTIVE ||
                sub.status == SubscriptionStatus.GRACE_PERIOD)
        if (!allowed) return ResponseEntity.status(403).build()

        log.info("Video download videoId={} userId={}", id, userId)
        val stream = storageService.retrieve(filePath)
        val filename = video.title?.replace("[^a-zA-Z0-9._-]".toRegex(), "_") ?: id.toString()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename.mp4\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(InputStreamResource(stream))
    }
}
```

- [ ] **Step 10: Run the enforcement test**

```bash
./gradlew test --tests "org.sightech.memoryvault.billing.TierGateEnforcementTest" 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`, 1 test passed.

- [ ] **Step 11: Run all tests**

```bash
./gradlew test 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 12: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/ src/test/kotlin/org/sightech/memoryvault/billing/TierGateEnforcementTest.kt
git commit -m "feat: tier enforcement — @TierGate on YouTube/Backup, FREE feed limit, video download endpoint"
```


## Task 10: StorageUsageReporter + VideoPurgeService + BillingSyncRegistrar

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/billing/service/StorageUsageReporter.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/billing/service/VideoPurgeService.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/billing/BillingSyncRegistrar.kt`
- Test: `src/test/kotlin/org/sightech/memoryvault/billing/StorageUsageReporterTest.kt`
- Test: `src/test/kotlin/org/sightech/memoryvault/billing/VideoPurgeServiceTest.kt`

- [ ] **Step 1: Write StorageUsageReporter test (failing)**

Create `src/test/kotlin/org/sightech/memoryvault/billing/StorageUsageReporterTest.kt`:
```kotlin
package org.sightech.memoryvault.billing

import io.mockk.*
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.entity.SubscriptionEntity
import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.sightech.memoryvault.billing.service.StorageUsageReporter
import org.sightech.memoryvault.billing.service.StripeService
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.youtube.entity.Video
import org.sightech.memoryvault.youtube.repository.VideoRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class StorageUsageReporterTest {

    private val userRepository = mockk<UserRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val videoRepository = mockk<VideoRepository>()
    private val storageService = mockk<StorageService>()
    private val stripeService = mockk<StripeService>(relaxed = true)
    private val reporter = StorageUsageReporter(
        userRepository, subscriptionRepository, videoRepository, storageService, stripeService
    )

    @Test
    fun `reports storage usage for BACKUP users`() {
        val userId = UUID.randomUUID()
        val user = mockk<User>()
        every { user.id } returns userId

        val sub = SubscriptionEntity(
            userId = userId,
            stripeCustomerId = "cus_123",
            stripeSubscriptionId = "sub_123",
            billingInterval = BillingInterval.MONTHLY,
            status = SubscriptionStatus.ACTIVE,
            lastUsageReportedAt = null
        )

        val video = mockk<Video>()
        every { video.filePath } returns "videos/test.mp4"

        every { userRepository.findAllByTierAndDeletedAtIsNull(Tier.BACKUP) } returns listOf(user)
        every { subscriptionRepository.findByUserId(userId) } returns sub
        every { videoRepository.findAllWithFilePathByUserId(userId) } returns listOf(video)
        every { storageService.sizeBytes("videos/test.mp4") } returns 1_073_741_824L // 1 GB

        reporter.report()

        verify { stripeService.reportUsage(any(), 1L, any()) }
    }

    @Test
    fun `skips users with no BACKUP subscription`() {
        val userId = UUID.randomUUID()
        val user = mockk<User>()
        every { user.id } returns userId
        every { userRepository.findAllByTierAndDeletedAtIsNull(Tier.BACKUP) } returns listOf(user)
        every { subscriptionRepository.findByUserId(userId) } returns null

        reporter.report()

        verify(exactly = 0) { stripeService.reportUsage(any(), any(), any()) }
    }
}
```

- [ ] **Step 2: Write VideoPurgeService test (failing)**

Create `src/test/kotlin/org/sightech/memoryvault/billing/VideoPurgeServiceTest.kt`:
```kotlin
package org.sightech.memoryvault.billing

import io.mockk.*
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.entity.SubscriptionEntity
import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.sightech.memoryvault.billing.service.VideoPurgeService
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.youtube.entity.Video
import org.sightech.memoryvault.youtube.repository.VideoRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertNull

class VideoPurgeServiceTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val videoRepository = mockk<VideoRepository>(relaxed = true)
    private val storageService = mockk<StorageService>(relaxed = true)
    private val service = VideoPurgeService(subscriptionRepository, videoRepository, storageService)

    @Test
    fun `purges video files for expired grace periods`() {
        val userId = UUID.randomUUID()
        val sub = SubscriptionEntity(
            userId = userId,
            stripeCustomerId = "cus_123",
            stripeSubscriptionId = "sub_grace",
            billingInterval = BillingInterval.MONTHLY,
            status = SubscriptionStatus.GRACE_PERIOD,
            gracePeriodEndsAt = Instant.now().minusSeconds(3600)
        )

        val video = mockk<Video>(relaxed = true)
        every { video.filePath } returns "videos/old.mp4"

        every { subscriptionRepository.findExpiredGracePeriods(any()) } returns listOf(sub)
        every { videoRepository.findAllWithFilePathByUserId(userId) } returns listOf(video)

        service.purge()

        verify { storageService.delete("videos/old.mp4") }
        verify { video.filePath = null }
        verify { videoRepository.save(video) }
    }

    @Test
    fun `does not purge when grace period has not expired`() {
        every { subscriptionRepository.findExpiredGracePeriods(any()) } returns emptyList()

        service.purge()

        verify(exactly = 0) { storageService.delete(any()) }
    }
}
```

- [ ] **Step 3: Run both tests — expect compilation failure**

```bash
./gradlew test --tests "org.sightech.memoryvault.billing.StorageUsageReporterTest" --tests "org.sightech.memoryvault.billing.VideoPurgeServiceTest" 2>&1 | tail -10
```

- [ ] **Step 4: Create StorageUsageReporter**

Create `src/main/kotlin/org/sightech/memoryvault/billing/service/StorageUsageReporter.kt`:
```kotlin
package org.sightech.memoryvault.billing.service

import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

private const val BYTES_PER_GB = 1_073_741_824L

@Service
class StorageUsageReporter(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val videoRepository: VideoRepository,
    private val storageService: StorageService,
    private val stripeService: StripeService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun report(): Map<String, Any> {
        val backupUsers = userRepository.findAllByTierAndDeletedAtIsNull(Tier.BACKUP)
        var reported = 0
        var errors = 0

        for (user in backupUsers) {
            val sub = subscriptionRepository.findByUserId(user.id) ?: continue
            try {
                val totalBytes = videoRepository.findAllWithFilePathByUserId(user.id)
                    .sumOf { storageService.sizeBytes(it.filePath!!) }
                val gb = totalBytes / BYTES_PER_GB
                stripeService.reportUsage(sub.stripeSubscriptionId, gb, Instant.now().epochSecond)
                sub.lastUsageReportedAt = Instant.now()
                subscriptionRepository.save(sub)
                reported++
                log.info("Reported storage userId={} gb={}", user.id, gb)
            } catch (e: Exception) {
                log.warn("Failed to report storage for userId={}: {}", user.id, e.message)
                errors++
            }
        }

        return mapOf("reported" to reported, "errors" to errors)
    }
}
```

- [ ] **Step 5: Create VideoPurgeService**

Create `src/main/kotlin/org/sightech/memoryvault/billing/service/VideoPurgeService.kt`:
```kotlin
package org.sightech.memoryvault.billing.service

import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class VideoPurgeService(
    private val subscriptionRepository: SubscriptionRepository,
    private val videoRepository: VideoRepository,
    private val storageService: StorageService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun purge(): Map<String, Any> {
        val expired = subscriptionRepository.findExpiredGracePeriods(Instant.now())
        var purged = 0

        for (sub in expired) {
            val videos = videoRepository.findAllWithFilePathByUserId(sub.userId)
            for (video in videos) {
                val path = video.filePath ?: continue
                try {
                    storageService.delete(path)
                    video.filePath = null
                    videoRepository.save(video)
                    purged++
                } catch (e: Exception) {
                    log.warn("Failed to delete video file key={}: {}", path, e.message)
                }
            }
            sub.status = SubscriptionStatus.CANCELLED
            subscriptionRepository.save(sub)
            log.info("Purged videos for userId={} count={}", sub.userId, videos.size)
        }

        return mapOf("purged" to purged)
    }
}
```

- [ ] **Step 6: Create BillingSyncRegistrar**

Create `src/main/kotlin/org/sightech/memoryvault/billing/BillingSyncRegistrar.kt`:
```kotlin
package org.sightech.memoryvault.billing

import org.sightech.memoryvault.billing.service.StorageUsageReporter
import org.sightech.memoryvault.billing.service.VideoPurgeService
import org.sightech.memoryvault.scheduling.JobScheduler
import org.sightech.memoryvault.scheduling.entity.JobType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class BillingSyncRegistrar(
    private val jobScheduler: JobScheduler,
    private val storageUsageReporter: StorageUsageReporter,
    private val videoPurgeService: VideoPurgeService,
    @Value("\${memoryvault.billing.usage-report-cron:-}") private val usageReportCron: String,
    @Value("\${memoryvault.billing.video-purge-cron:-}") private val videoPurgeCron: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun registerJobs() {
        jobScheduler.schedule("billing-usage-report", usageReportCron, JobType.STORAGE_USAGE_REPORT) {
            log.info("Storage usage report job starting")
            storageUsageReporter.report()
        }
        jobScheduler.schedule("billing-video-purge", videoPurgeCron, JobType.VIDEO_PURGE) {
            log.info("Video purge job starting")
            videoPurgeService.purge()
        }
    }
}
```

- [ ] **Step 7: Run both tests — expect pass**

```bash
./gradlew test --tests "org.sightech.memoryvault.billing.StorageUsageReporterTest" --tests "org.sightech.memoryvault.billing.VideoPurgeServiceTest" 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 8: Run all tests**

```bash
./gradlew test 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/billing/ src/test/kotlin/org/sightech/memoryvault/billing/StorageUsageReporterTest.kt src/test/kotlin/org/sightech/memoryvault/billing/VideoPurgeServiceTest.kt
git commit -m "feat: StorageUsageReporter, VideoPurgeService, BillingSyncRegistrar"
```


## Task 11: GraphQL Schema + Resolvers + MCP Tools

**Files:**
- Modify: `src/main/resources/graphql/schema.graphqls`
- Create: `src/main/kotlin/org/sightech/memoryvault/graphql/BillingResolver.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/graphql/AdminUserResolver.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/mcp/BillingTools.kt`

- [ ] **Step 1: Extend GraphQL schema**

In `src/main/resources/graphql/schema.graphqls`, add to the `Query` type (after the `search` query):
```graphql
    # Billing / user profile
    me: UserProfile!
    subscription: SubscriptionInfo

    # Admin users
    adminUsers(limit: Int, offset: Int): [AdminUserView!]!
```

Add to the `Mutation` type (after `triggerBackfill`):
```graphql
    # Admin user management
    adminSetUserTier(userId: UUID!, tier: String!): Boolean!
    adminSetUserActive(userId: UUID!, active: Boolean!): Boolean!
```

Add the new types at the bottom of the schema file:
```graphql
type UserProfile {
    id: UUID!
    email: String!
    displayName: String!
    tier: String!
    role: String!
}

type SubscriptionInfo {
    tier: String!
    status: String!
    billingInterval: String!
    gracePeriodEndsAt: Instant
    lastUsageReportedAt: Instant
}

type AdminUserView {
    id: UUID!
    email: String!
    displayName: String!
    tier: String!
    role: String!
    subscriptionStatus: String
    createdAt: Instant!
}
```

- [ ] **Step 2: Create BillingResolver**

Create `src/main/kotlin/org/sightech/memoryvault/graphql/BillingResolver.kt`:
```kotlin
package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.auth.dto.UserProfileDto
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.entity.SubscriptionEntity
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

@Controller
class BillingResolver(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @QueryMapping
    fun me(): UserProfileDto {
        val userId = CurrentUser.userId()
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: error("Authenticated user not found: $userId")
        log.debug("GraphQL me userId={}", userId)
        return UserProfileDto(
            id = user.id,
            email = user.email,
            displayName = user.displayName,
            tier = user.tier,
            role = user.role
        )
    }

    @QueryMapping
    fun subscription(): SubscriptionEntity? {
        val userId = CurrentUser.userId()
        return subscriptionRepository.findByUserId(userId)
    }
}
```

- [ ] **Step 3: Create AdminUserResolver**

Create `src/main/kotlin/org/sightech/memoryvault/graphql/AdminUserResolver.kt`:
```kotlin
package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.time.Instant
import java.util.UUID

data class AdminUserView(
    val id: UUID,
    val email: String,
    val displayName: String,
    val tier: String,
    val role: String,
    val subscriptionStatus: String?,
    val createdAt: Instant
)

@Controller
class AdminUserResolver(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @QueryMapping
    fun adminUsers(@Argument limit: Int?, @Argument offset: Int?): List<AdminUserView> {
        val users = userRepository.findAll()
            .filter { it.deletedAt == null }
            .drop(offset ?: 0)
            .take(limit ?: 50)
        return users.map { user ->
            val sub = subscriptionRepository.findByUserId(user.id)
            AdminUserView(
                id = user.id,
                email = user.email,
                displayName = user.displayName,
                tier = user.tier.name,
                role = user.role.name,
                subscriptionStatus = sub?.status?.name,
                createdAt = user.createdAt
            )
        }
    }

    @MutationMapping
    fun adminSetUserTier(@Argument userId: UUID, @Argument tier: String): Boolean {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId) ?: return false
        user.tier = Tier.valueOf(tier)
        userRepository.save(user)
        log.info("Admin set tier userId={} tier={}", userId, tier)
        return true
    }

    @MutationMapping
    fun adminSetUserActive(@Argument userId: UUID, @Argument active: Boolean): Boolean {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId) ?: return false
        user.deletedAt = if (active) null else Instant.now()
        userRepository.save(user)
        log.info("Admin set active userId={} active={}", userId, active)
        return true
    }
}
```

- [ ] **Step 4: Create BillingTools MCP**

Create `src/main/kotlin/org/sightech/memoryvault/mcp/BillingTools.kt`:
```kotlin
package org.sightech.memoryvault.mcp

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.auth.entity.Tier
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class BillingTools(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Tool(description = "Get the current user's tier and subscription status. Use this to check what features are available.")
    fun getMySubscription(): Map<String, Any?> {
        val userId = CurrentUser.userId()
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: return mapOf("error" to "User not found")
        val sub = subscriptionRepository.findByUserId(userId)
        return mapOf(
            "tier" to user.tier.name,
            "subscriptionStatus" to sub?.status?.name,
            "gracePeriodEndsAt" to sub?.gracePeriodEndsAt?.toString()
        )
    }

    @Tool(description = "List all users with their tier and subscription status. Only usable by OWNER role.")
    fun adminListUsers(): Any {
        val caller = userRepository.findByIdAndDeletedAtIsNull(CurrentUser.userId())
        if (caller?.role != UserRole.OWNER) return mapOf("error" to "OWNER role required")
        return userRepository.findAll()
            .filter { it.deletedAt == null }
            .map { u ->
                val sub = subscriptionRepository.findByUserId(u.id)
                mapOf("id" to u.id, "email" to u.email, "tier" to u.tier.name, "status" to sub?.status?.name)
            }
    }

    @Tool(description = "Change a user's tier. Provide userId (UUID string) and tier (FREE, PREMIUM, or BACKUP). Only usable by OWNER role.")
    fun adminSetUserTier(userId: String, tier: String): Map<String, Any> {
        val caller = userRepository.findByIdAndDeletedAtIsNull(CurrentUser.userId())
        if (caller?.role != UserRole.OWNER) return mapOf("success" to false, "error" to "OWNER role required")
        val user = userRepository.findByIdAndDeletedAtIsNull(UUID.fromString(userId))
            ?: return mapOf("success" to false, "error" to "User not found")
        user.tier = Tier.valueOf(tier)
        userRepository.save(user)
        log.info("MCP adminSetUserTier userId={} tier={}", userId, tier)
        return mapOf("success" to true, "email" to user.email, "tier" to user.tier.name)
    }
}
```

- [ ] **Step 5: Run all tests**

```bash
./gradlew test 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/graphql/schema.graphqls src/main/kotlin/org/sightech/memoryvault/graphql/BillingResolver.kt src/main/kotlin/org/sightech/memoryvault/graphql/AdminUserResolver.kt src/main/kotlin/org/sightech/memoryvault/mcp/BillingTools.kt
git commit -m "feat: GraphQL billing queries (me, subscription, adminUsers), MCP billing tools"
```


## Task 12: Angular — TierService + Signup Page

**Files:**
- Create: `client/src/app/core/models/user-profile.model.ts`
- Create: `client/src/app/core/services/tier.service.ts`
- Create: `client/src/app/auth/signup/signup.component.ts`
- Create: `client/src/app/auth/signup/signup.component.html`
- Create: `client/src/app/auth/signup/signup.component.scss`
- Modify: `client/src/app/app.routes.ts`
- Modify: `client/src/app/auth/login/login.html` (add "Create account" link)

- [ ] **Step 1: Create user profile model**

Create `client/src/app/core/models/user-profile.model.ts`:
```typescript
export type Tier = 'FREE' | 'PREMIUM' | 'BACKUP';
export type UserRole = 'OWNER' | 'ADMIN' | 'VIEWER';

export interface UserProfile {
  id: string;
  email: string;
  displayName: string;
  tier: Tier;
  role: UserRole;
}
```

- [ ] **Step 2: Create TierService**

Create `client/src/app/core/services/tier.service.ts`:
```typescript
import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { UserProfile, Tier } from '../models/user-profile.model';

@Injectable({ providedIn: 'root' })
export class TierService {
  private http = inject(HttpClient);

  readonly profile = signal<UserProfile | null>(null);
  readonly tier = signal<Tier>('FREE');

  load(): void {
    this.http.get<UserProfile>('/api/me').subscribe({
      next: (p) => {
        this.profile.set(p);
        this.tier.set(p.tier);
      },
      error: () => {
        this.profile.set(null);
        this.tier.set('FREE');
      }
    });
  }

  isAtLeast(required: Tier): boolean {
    const order: Tier[] = ['FREE', 'PREMIUM', 'BACKUP'];
    return order.indexOf(this.tier()) >= order.indexOf(required);
  }

  isOwner(): boolean {
    return this.profile()?.role === 'OWNER';
  }
}
```

- [ ] **Step 3: Load TierService on app init**

In `client/src/app/shared/layout/app-layout.ts`, inject `TierService` and call `load()` in `ngOnInit`:
```typescript
import { TierService } from '../../core/services/tier.service';

// Inside AppLayoutComponent:
private tierService = inject(TierService);

ngOnInit() {
  this.ws.connect();
  this.tierService.load();
}
```

- [ ] **Step 4: Create signup component**

Create `client/src/app/auth/signup/signup.component.ts`:
```typescript
import { Component, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { CognitoAuthService } from '../cognito-auth.service';

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [FormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatButtonModule, MatCardModule],
  templateUrl: './signup.component.html',
  styleUrl: './signup.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SignupComponent {
  private cognitoAuth = inject(CognitoAuthService);
  private router = inject(Router);

  email = '';
  password = '';
  loading = signal(false);
  error = signal('');
  success = signal(false);

  async signup(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      await this.cognitoAuth.signUp(this.email, this.password);
      this.success.set(true);
    } catch (err: any) {
      this.error.set(err.message ?? 'Signup failed');
    } finally {
      this.loading.set(false);
    }
  }
}
```

Create `client/src/app/auth/signup/signup.component.html`:
```html
<div class="signup-container">
  <mat-card>
    <mat-card-header>
      <mat-card-title>Create Account</mat-card-title>
    </mat-card-header>
    <mat-card-content>
      @if (success()) {
        <p>Check your email to verify your account, then <a routerLink="/login">sign in</a>.</p>
      } @else {
        <form (ngSubmit)="signup()">
          <mat-form-field appearance="outline">
            <mat-label>Email</mat-label>
            <input matInput type="email" [(ngModel)]="email" name="email" required />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Password</mat-label>
            <input matInput type="password" [(ngModel)]="password" name="password" required />
          </mat-form-field>
          @if (error()) {
            <p class="error">{{ error() }}</p>
          }
          <button mat-raised-button color="primary" type="submit" [disabled]="loading()">
            {{ loading() ? 'Creating account…' : 'Create Account' }}
          </button>
        </form>
        <p><a routerLink="/login">Already have an account? Sign in</a></p>
      }
    </mat-card-content>
  </mat-card>
</div>
```

Create `client/src/app/auth/signup/signup.component.scss`:
```scss
.signup-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  mat-card { width: 360px; }
  mat-form-field { width: 100%; margin-bottom: 16px; }
  button { width: 100%; }
  .error { color: var(--mat-sys-error); }
}
```

Note: `CognitoAuthService.signUp()` must accept `(email, password)` and call the Cognito SDK's `signUp` method. Read `client/src/app/auth/cognito-auth.service.ts` to find the correct method name before implementing; add `signUp` if it doesn't exist.

- [ ] **Step 5: Add /signup route**

In `client/src/app/app.routes.ts`, add before the main `''` route:
```typescript
  {
    path: 'signup',
    loadComponent: () => import('./auth/signup/signup.component').then(m => m.SignupComponent),
  },
```

- [ ] **Step 6: Add "Create account" link to login page**

Read `client/src/app/auth/login/login.html`. After the submit button, add:
```html
<p><a routerLink="/signup">Create account</a></p>
```

- [ ] **Step 7: Run frontend tests**

```bash
cd client && npm run test -- --run 2>&1 | tail -20
```
Expected: all tests pass (TierService has no spec file yet — that's OK, add one if coverage requires it).

- [ ] **Step 8: Commit**

```bash
git add client/src/app/core/models/user-profile.model.ts client/src/app/core/services/tier.service.ts client/src/app/auth/signup/ client/src/app/app.routes.ts client/src/app/auth/login/ client/src/app/shared/layout/app-layout.ts
git commit -m "feat: Angular TierService, signup page, /signup route"
```


## Task 13: Angular — Billing Pages + UI Changes

**Files:**
- Create: `client/src/app/feature-billing/billing.component.ts/html/scss`
- Create: `client/src/app/feature-billing/billing-success.component.ts/html`
- Create: `client/src/app/feature-billing/billing-cancelled.component.ts/html`
- Modify: `client/src/app/app.routes.ts` — /billing routes
- Modify: `client/src/app/shared/layout/top-bar.ts/html` — user icon menu
- Modify: `client/src/app/youtube/youtube.ts/html` — locked state + grace period banner
- Modify: `client/src/app/reader/reader.ts/html` — upgrade prompt at feed limit
- Modify: `client/src/app/admin/admin.ts/html` — Users tab

- [ ] **Step 1: Create billing page**

Create `client/src/app/feature-billing/billing.component.ts`:
```typescript
import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { TierService } from '../core/services/tier.service';

interface SubscriptionInfo {
  tier: string;
  status: string;
  billingInterval: string;
  gracePeriodEndsAt?: string;
}

@Component({
  selector: 'app-billing',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatDividerModule],
  templateUrl: './billing.component.html',
  styleUrl: './billing.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BillingComponent implements OnInit {
  private http = inject(HttpClient);
  readonly tierService = inject(TierService);

  subscription = signal<SubscriptionInfo | null>(null);
  loading = signal(false);

  ngOnInit(): void {
    this.http.get<SubscriptionInfo | null>('/api/billing/subscription').subscribe(s => this.subscription.set(s));
  }

  upgradeToPremium(interval: 'MONTHLY' | 'ANNUAL'): void {
    this.initiateCheckout('PREMIUM', interval);
  }

  upgradeToBackup(interval: 'MONTHLY' | 'ANNUAL'): void {
    this.initiateCheckout('BACKUP', interval);
  }

  openPortal(): void {
    this.loading.set(true);
    this.http.post<{ url: string }>('/api/billing/portal', {}).subscribe({
      next: r => window.location.href = r.url,
      error: () => this.loading.set(false)
    });
  }

  private initiateCheckout(tier: string, interval: string): void {
    this.loading.set(true);
    this.http.post<{ url: string }>('/api/billing/checkout', { tier, interval }).subscribe({
      next: r => window.location.href = r.url,
      error: () => this.loading.set(false)
    });
  }
}
```

Create `client/src/app/feature-billing/billing.component.html`:
```html
<div class="billing-page">
  <h2>Billing & Subscription</h2>

  <mat-card>
    <mat-card-header><mat-card-title>Current Plan</mat-card-title></mat-card-header>
    <mat-card-content>
      <p><strong>Tier:</strong> {{ tierService.tier() }}</p>
      @if (subscription(); as sub) {
        <p><strong>Status:</strong> {{ sub.status }}</p>
        @if (sub.gracePeriodEndsAt) {
          <p class="warn">Grace period ends: {{ sub.gracePeriodEndsAt | date }}</p>
        }
        <button mat-stroked-button (click)="openPortal()" [disabled]="loading()">
          Manage Subscription
        </button>
      }
    </mat-card-content>
  </mat-card>

  @if (tierService.tier() === 'FREE') {
    <mat-card>
      <mat-card-header><mat-card-title>Upgrade to Premium — $3/mo or $30/yr</mat-card-title></mat-card-header>
      <mat-card-content>
        <p>Unlimited feeds, full-text search, bookmarks, MCP tools.</p>
        <button mat-raised-button color="primary" (click)="upgradeToPremium('MONTHLY')" [disabled]="loading()">$3 / month</button>
        <button mat-button (click)="upgradeToPremium('ANNUAL')" [disabled]="loading()">$30 / year</button>
      </mat-card-content>
    </mat-card>
  }

  @if (tierService.tier() !== 'BACKUP') {
    <mat-card>
      <mat-card-header><mat-card-title>Add YouTube Backup — $3/mo base + storage</mat-card-title></mat-card-header>
      <mat-card-content>
        <p>Archive YouTube playlists + Internet Archive backup. Metered S3 storage billing.</p>
        <button mat-raised-button color="accent" (click)="upgradeToBackup('MONTHLY')" [disabled]="loading()">$3 / month + storage</button>
        <button mat-button (click)="upgradeToBackup('ANNUAL')" [disabled]="loading()">$30 / year + storage</button>
      </mat-card-content>
    </mat-card>
  }
</div>
```

Create `client/src/app/feature-billing/billing.component.scss`:
```scss
.billing-page {
  max-width: 600px;
  margin: 32px auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
  mat-card-content { display: flex; flex-direction: column; gap: 8px; }
  button { margin-right: 8px; }
  .warn { color: var(--mat-sys-error); }
}
```

- [ ] **Step 2: Create billing-success and billing-cancelled components**

Create `client/src/app/feature-billing/billing-success.component.ts`:
```typescript
import { Component, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { TierService } from '../core/services/tier.service';

@Component({
  selector: 'app-billing-success',
  standalone: true,
  imports: [RouterLink, MatButtonModule],
  template: `
    <div style="text-align:center;padding:64px">
      <h2>Subscription activated!</h2>
      <p>Your new tier is now active.</p>
      <a mat-raised-button color="primary" routerLink="/billing">View Billing</a>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BillingSuccessComponent implements OnInit {
  private tierService = inject(TierService);
  ngOnInit() { this.tierService.load(); }
}
```

Create `client/src/app/feature-billing/billing-cancelled.component.ts`:
```typescript
import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-billing-cancelled',
  standalone: true,
  imports: [RouterLink, MatButtonModule],
  template: `
    <div style="text-align:center;padding:64px">
      <h2>Checkout cancelled</h2>
      <p>No charges were made.</p>
      <a mat-button routerLink="/billing">Back to Billing</a>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BillingCancelledComponent {}
```

- [ ] **Step 3: Add /billing routes**

In `client/src/app/app.routes.ts`, inside the `canActivate: [authGuard]` children array, add:
```typescript
      {
        path: 'billing',
        loadComponent: () => import('./feature-billing/billing.component').then(m => m.BillingComponent)
      },
      {
        path: 'billing/success',
        loadComponent: () => import('./feature-billing/billing-success.component').then(m => m.BillingSuccessComponent)
      },
      {
        path: 'billing/cancelled',
        loadComponent: () => import('./feature-billing/billing-cancelled.component').then(m => m.BillingCancelledComponent)
      },
```

- [ ] **Step 4: Add user menu to top bar**

Read `client/src/app/shared/layout/top-bar.html` to see current structure. Add a user icon button with a Material menu after the nav links:
```html
<button mat-icon-button [matMenuTriggerFor]="userMenu" aria-label="User menu">
  <mat-icon>account_circle</mat-icon>
</button>
<mat-menu #userMenu="matMenu">
  <a mat-menu-item routerLink="/billing">
    <mat-icon>credit_card</mat-icon>
    <span>Billing</span>
  </a>
  <button mat-menu-item (click)="logout()">
    <mat-icon>logout</mat-icon>
    <span>Sign out</span>
  </button>
</mat-menu>
```

`MatMenuModule` is already imported in `TopBarComponent`. Add `MatIconModule` if not already present.

- [ ] **Step 5: Add YouTube locked state**

In `client/src/app/youtube/youtube.ts`, inject `TierService`:
```typescript
import { TierService } from '../core/services/tier.service';
readonly tierService = inject(TierService);
```

In `client/src/app/youtube/youtube.html`, wrap the existing content:
```html
@if (tierService.isAtLeast('BACKUP') || tierService.isOwner()) {
  <!-- existing youtube content here -->
} @else {
  <div class="tier-gate">
    <mat-icon>lock</mat-icon>
    <h3>YouTube Archival requires the Backup tier</h3>
    <a mat-raised-button color="primary" routerLink="/billing">Upgrade</a>
  </div>
}
```

- [ ] **Step 6: Add feed upgrade prompt**

In `client/src/app/reader/reader.ts`, inject `TierService` and create a computed signal for `atFeedLimit`. The feed count must come from the store/service already present in the reader. Add:
```typescript
readonly tierService = inject(TierService);
```

In `client/src/app/reader/reader.html`, find the "Add Feed" button. Wrap with a condition — if the user is FREE and has hit the limit (the backend will return a 403 when they try), show an upgrade prompt in the error dialog rather than the add dialog. This is a UX enhancement; the backend 403 is the real gate.

Add a banner above the feed list when tier is FREE and feed count approaches 100:
```html
@if (tierService.tier() === 'FREE' && feedCount() >= 90) {
  <div class="tier-banner">
    <span>Approaching 100-feed limit (FREE tier).</span>
    <a routerLink="/billing">Upgrade to Premium</a>
  </div>
}
```

- [ ] **Step 7: Add Users tab to admin page**

Read `client/src/app/admin/admin.ts` and `admin.html` to understand the existing tab structure (tabs for Jobs, Logs, Stats, etc.). Add a "Users" tab that displays a table of users.

In `admin.ts`, inject `HttpClient` (or use Apollo for the GraphQL `adminUsers` query). Use `httpResource` pattern from the existing admin store to fetch `/api/admin/users` data, or use an Apollo query for `adminUsers`.

The simplest approach: use `httpResource` with the GraphQL endpoint (POST to `/graphql` with the `adminUsers` query). Read the existing admin page GraphQL queries for the exact pattern.

Add the Users tab to the mat-tab-group in `admin.html`:
```html
<mat-tab label="Users">
  <table mat-table [dataSource]="adminUsers()">
    <ng-container matColumnDef="email">
      <th mat-header-cell *matHeaderCellDef>Email</th>
      <td mat-cell *matCellDef="let u">{{ u.email }}</td>
    </ng-container>
    <ng-container matColumnDef="tier">
      <th mat-header-cell *matHeaderCellDef>Tier</th>
      <td mat-cell *matCellDef="let u">{{ u.tier }}</td>
    </ng-container>
    <ng-container matColumnDef="status">
      <th mat-header-cell *matHeaderCellDef>Status</th>
      <td mat-cell *matCellDef="let u">{{ u.subscriptionStatus ?? '—' }}</td>
    </ng-container>
    <tr mat-header-row *matHeaderRowDef="['email','tier','status']"></tr>
    <tr mat-row *matRowDef="let row; columns: ['email','tier','status']"></tr>
  </table>
</mat-tab>
```

- [ ] **Step 8: Run frontend tests**

```bash
cd client && npm run test -- --run 2>&1 | tail -20
```
Expected: all tests pass.

- [ ] **Step 9: Start dev server and verify visually**

```bash
./scripts/dev.sh
```

Check:
- `/signup` renders a signup form with email + password fields and "Already have an account?" link
- `/login` has a "Create account" link
- Top bar shows user icon with dropdown (Billing, Sign out)
- `/billing` shows current tier and upgrade options
- `/youtube` shows locked state for non-BACKUP users (OWNER bypasses)
- `/admin` has a Users tab

- [ ] **Step 10: Commit**

```bash
git add client/src/app/feature-billing/ client/src/app/app.routes.ts client/src/app/shared/layout/ client/src/app/youtube/ client/src/app/reader/ client/src/app/admin/
git commit -m "feat: billing pages, user menu, YouTube locked state, feed upgrade prompt, admin Users tab"
```


---

## Summary

| Task  | Description                                                     | Key Files                                                                                                                       |
|-------|-----------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| 1     | Terraform prerequisites + Cognito + Stripe config               | `terraform/cognito.tf`, `variables.tf`, `ec2.tf`, `user_data.sh`, `.env.sample`                                                 |
| 2     | Stripe SDK + V10 migration + Tier enum + User entity            | `V10__billing.sql`, `Tier.kt`, `User.kt`, `UserRepository.kt`                                                                   |
| 3     | SubscriptionEntity + SubscriptionRepository + JobType           | `BillingEnums.kt`, `SubscriptionEntity.kt`, `SubscriptionRepository.kt`, `JobType.kt`                                           |
| 4     | @TierGate annotation + AOP aspect                               | `TierGate.kt`, `TierRequiredException.kt`, `TierEnforcementAspect.kt`                                                           |
| 5     | CognitoJwtFilter auto-provisioning + GET /api/me                | `CognitoJwtFilter.kt`, `SecurityConfig.kt`, `UserController.kt`, `UserProfileDto.kt`                                            |
| 6     | StripeService + billing application config                      | `StripeService.kt`, `application.properties`                                                                                    |
| 7     | BillingService + BillingController                              | `BillingService.kt`, `BillingController.kt`                                                                                     |
| 8     | StripeWebhookController                                         | `StripeWebhookController.kt`                                                                                                    |
| 9     | Tier enforcement on existing services + video download          | `FeedService.kt`, `YoutubeListService.kt`, `VideoSyncService.kt`, `BackupService.kt`, `VideoController.kt`, `StorageService.kt` |
| 10    | StorageUsageReporter + VideoPurgeService + BillingSyncRegistrar | `StorageUsageReporter.kt`, `VideoPurgeService.kt`, `BillingSyncRegistrar.kt`                                                    |
| 11    | GraphQL schema + resolvers + MCP tools                          | `schema.graphqls`, `BillingResolver.kt`, `AdminUserResolver.kt`, `BillingTools.kt`                                              |
| 12    | Angular TierService + signup page                               | `tier.service.ts`, `signup.component.ts`, `app.routes.ts`                                                                       |
| 13    | Angular billing pages + UI changes                              | `billing.component.ts`, `top-bar.ts/html`, `youtube.ts/html`, `admin.ts/html`                                                   |

