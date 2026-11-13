# Phase 11 — Public Self-Signup & Subscription Billing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Open MemoryVault to public self-service signup (Cognito) with tiered subscription billing (Stripe). Free / Premium / Backup tiers with metered YouTube archival storage. Admin user management. Full backend enforcement via `@TierGate`.

**Architecture:** Cognito handles signup and email verification. Stripe handles all payment via Checkout Sessions (no card forms in our UI). `@TierGate` annotation + Spring AOP enforces tier at the service layer. `StorageUsageReporter` submits daily S3 usage records to Stripe for metered Backup billing. `VideoPurgeJob` cleans expired files 30 days after Backup cancellation. Angular TierService exposes the current tier as a signal consumed by gated components.

**Tech Stack:** Spring Boot 4.x, Kotlin, Spring AOP, Stripe Java SDK, Flyway, Angular 21 + NgRx Signal Store, `amazon-cognito-identity-js` (already installed).

**Spec:** `docs/plans/2026-04-26-phase-11-public-self-signup-design.md`

**Key discoveries:**
- `spring-boot-starter-aop` is not yet in `build.gradle.kts` — required for `@TierGate` to work.
- `/api/stripe/webhook` must be added to `SecurityConfig`'s `permitAll` list. Stripe signature verification replaces JWT auth on that endpoint.
- `CognitoJwtFilter` currently logs a warning and does not authenticate when no DB user exists. Task 8 changes that to auto-provision a `users` row with `tier = FREE`, `role = VIEWER`.
- V10 is the next available migration version.
- Stripe Java SDK: `com.stripe:stripe-java:26.7.0`.

---

## File Structure

### New Files — Backend

| File | Responsibility |
|------|---------------|
| `src/main/resources/db/migration/V10__billing.sql` | Migration: add `tier`/`stripe_customer_id` to `users`; new `subscriptions` table |
| `.../billing/entity/BillingEnums.kt` | `Tier`, `BillingInterval`, `SubscriptionStatus` enums |
| `.../billing/entity/SubscriptionEntity.kt` | JPA entity for `subscriptions` table |
| `.../billing/repository/SubscriptionRepository.kt` | Spring Data repository |
| `.../billing/service/StripeService.kt` | Thin Stripe API wrapper (createCustomer, createCheckoutSession, createPortalSession, reportUsage) |
| `.../billing/service/BillingService.kt` | Business logic: checkout flow, webhook handling, tier transitions, grace period |
| `.../billing/service/StorageUsageReporter.kt` | Daily Stripe metered usage reporting for BACKUP users |
| `.../billing/service/VideoPurgeService.kt` | Daily video file purge after grace period ends |
| `.../billing/BillingSyncRegistrar.kt` | Registers `StorageUsageReporter` + `VideoPurgeService` on `ApplicationReadyEvent` |
| `.../billing/tier/TierGate.kt` | `@TierGate(Tier.X)` annotation |
| `.../billing/tier/TierRequiredException.kt` | Exception (maps to HTTP 403) |
| `.../billing/tier/TierEnforcementAspect.kt` | Spring AOP aspect — reads user tier, throws if insufficient |
| `.../billing/controller/BillingController.kt` | REST: `POST /api/billing/checkout`, `POST /api/billing/portal`, `GET /api/billing/subscription` |
| `.../billing/controller/StripeWebhookController.kt` | REST: `POST /api/stripe/webhook` (unauthenticated, Stripe-signature verified) |
| `.../billing/controller/AdminUserController.kt` | REST: `GET /api/admin/users`, `PUT /api/admin/users/{id}/tier`, `PUT /api/admin/users/{id}/active`, `POST /api/admin/users/{id}/refund` |
| `.../billing/controller/VideoDownloadController.kt` | REST: `GET /api/videos/{id}/download` — stream video from storage (grace period access) |
| `.../billing/dto/BillingDtos.kt` | DTOs: `CheckoutRequest`, `CheckoutResponse`, `SubscriptionInfo`, `AdminUserView` |
| `.../graphql/BillingResolver.kt` | GraphQL: `me`, `subscription`, `adminUsers` queries; `adminSetUserTier`, `adminSetUserActive`, `adminRefundUser` mutations |
| `src/main/resources/graphql/billing.graphqls` | `UserProfile`, `SubscriptionInfo`, `AdminUserView` GraphQL types |
| `.../mcp/BillingTools.kt` | MCP tools: `getMySubscription`, `adminListUsers`, `adminSetUserTier` |
| `src/test/kotlin/.../billing/service/StripeServiceTest.kt` | Unit tests for StripeService |
| `src/test/kotlin/.../billing/service/BillingServiceTest.kt` | Unit tests for BillingService |
| `src/test/kotlin/.../billing/tier/TierEnforcementAspectTest.kt` | Unit tests for AOP aspect |
| `src/test/kotlin/.../billing/controller/BillingControllerTest.kt` | Integration tests for billing endpoints |
| `src/test/kotlin/.../billing/controller/StripeWebhookControllerTest.kt` | Unit tests for webhook handler |

### New Files — Frontend

| File | Responsibility |
|------|---------------|
| `client/src/app/auth/signup/signup.ts` | Signup form component (Cognito `signUp`) |
| `client/src/app/auth/signup/index.ts` | Barrel export |
| `client/src/app/billing/billing.ts` | Billing page (tier display, upgrade/portal buttons) |
| `client/src/app/billing/billing-success.ts` | Post-checkout success page |
| `client/src/app/billing/billing-cancelled.ts` | Post-checkout cancel page |
| `client/src/app/billing/index.ts` | Barrel export |
| `client/src/app/core/tier.service.ts` | `TierService` — signal-based current tier + subscription info |

### Modified Files

| File | Change |
|------|--------|
| `build.gradle.kts` | Add `stripe-java`, `spring-boot-starter-aop` |
| `src/main/kotlin/.../scheduling/entity/SyncJob.kt` | Add `STORAGE_USAGE_REPORT`, `VIDEO_PURGE` to `JobType` enum |
| `src/main/kotlin/.../auth/entity/User.kt` | Add `tier: Tier`, `stripeCustomerId: String?` fields |
| `src/main/kotlin/.../auth/service/UserService.kt` | Add `provisionFromCognito`, `findAll`, `setTier`, `setActive` methods |
| `src/main/kotlin/.../config/CognitoJwtFilter.kt` | Auto-provision user rows on first login |
| `src/main/kotlin/.../config/SecurityConfig.kt` | Add `/api/stripe/webhook`, `/signup`, `/billing/**` to permitAll |
| `src/main/kotlin/.../feed/service/FeedService.kt` | Add free-tier feed count check on `addFeed` |
| `src/main/kotlin/.../youtube/service/YoutubeListService.kt` | Add `@TierGate(Tier.BACKUP)` on `addList`, `refreshList` |
| `src/main/resources/application.properties` | Add billing + Stripe config properties |
| `.env.sample` | Add `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, Stripe price IDs |
| `src/main/resources/graphql/schema.graphqls` | Add `me`, `subscription`, `adminUsers` queries; admin mutations |
| `client/src/app/app.routes.ts` | Add `/signup`, `/billing/**` routes |
| `client/src/app/shared/layout/app-layout.ts` | Add user menu dropdown (Billing, Sign out) |
| `client/src/app/youtube/youtube.ts` | Locked state for non-BACKUP + grace period banner |
| `client/src/app/reader/reader.ts` | Feed limit upgrade prompt for FREE tier at cap |
| `client/src/app/admin/admin.ts` | Add Users tab |
| `client/src/app/admin/admin.store.ts` | Add billing/admin-users state + methods |
| `client/src/app/admin/admin.graphql` | Add `me`, `adminUsers`, admin mutations |
| `client/src/app/auth/auth.service.ts` | Expose `getDisplayName`, `getRole` from token |

All paths below use `...` as shorthand for `src/main/kotlin/org/sightech/memoryvault` (source) or `src/test/kotlin/org/sightech/memoryvault` (test).

---

## Task 1: Gradle Dependencies + V10 Migration + Enums + Config

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/main/resources/db/migration/V10__billing.sql`
- Create: `.../billing/entity/BillingEnums.kt`
- Modify: `.../scheduling/entity/SyncJob.kt`
- Modify: `src/main/resources/application.properties`
- Modify: `.env.sample`

- [ ] **Step 1: Add Stripe SDK and Spring AOP to build.gradle.kts**

In `build.gradle.kts`, after the `// JWT` block, add:

```kotlin
// Billing
implementation("com.stripe:stripe-java:26.7.0")

// AOP (required for @TierGate)
implementation("org.springframework.boot:spring-boot-starter-aop")
```

- [ ] **Step 2: Create V10 migration**

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
CREATE INDEX idx_subscriptions_status ON subscriptions(status);
```

- [ ] **Step 3: Create BillingEnums.kt**

Create `src/main/kotlin/org/sightech/memoryvault/billing/entity/BillingEnums.kt`:

```kotlin
package org.sightech.memoryvault.billing.entity

enum class Tier { FREE, PREMIUM, BACKUP }

enum class BillingInterval { MONTHLY, ANNUAL }

enum class SubscriptionStatus { ACTIVE, PAST_DUE, CANCELLED, GRACE_PERIOD }
```

- [ ] **Step 4: Add STORAGE_USAGE_REPORT and VIDEO_PURGE to JobType enum**

In `src/main/kotlin/org/sightech/memoryvault/scheduling/entity/SyncJob.kt`, change:

```kotlin
enum class JobType { RSS_FETCH, YT_SYNC, BOOKMARK_ARCHIVE, BACKUP_UPLOAD, BACKUP_HEALTH_CHECK }
```

to:

```kotlin
enum class JobType { RSS_FETCH, YT_SYNC, BOOKMARK_ARCHIVE, BACKUP_UPLOAD, BACKUP_HEALTH_CHECK, STORAGE_USAGE_REPORT, VIDEO_PURGE }
```

- [ ] **Step 5: Add billing config to application.properties**

Append to `src/main/resources/application.properties`:

```properties
# Billing
memoryvault.billing.free-feed-limit=100
memoryvault.billing.grace-period-days=30
memoryvault.billing.usage-report-cron=-
memoryvault.billing.video-purge-cron=-

# Stripe (set via environment variables)
stripe.secret.key=${STRIPE_SECRET_KEY:}
stripe.webhook.secret=${STRIPE_WEBHOOK_SECRET:}
stripe.price.premium.monthly=${STRIPE_PREMIUM_MONTHLY_PRICE_ID:}
stripe.price.premium.annual=${STRIPE_PREMIUM_ANNUAL_PRICE_ID:}
stripe.price.backup.monthly=${STRIPE_BACKUP_MONTHLY_PRICE_ID:}
stripe.price.backup.annual=${STRIPE_BACKUP_ANNUAL_PRICE_ID:}
```

- [ ] **Step 6: Update .env.sample**

Append to `.env.sample`:

```
# Stripe billing (Phase 11)
STRIPE_SECRET_KEY=
STRIPE_WEBHOOK_SECRET=
STRIPE_PREMIUM_MONTHLY_PRICE_ID=
STRIPE_PREMIUM_ANNUAL_PRICE_ID=
STRIPE_BACKUP_MONTHLY_PRICE_ID=
STRIPE_BACKUP_ANNUAL_PRICE_ID=
```

- [ ] **Step 7: Verify migration compiles**

Run: `./gradlew test --tests "*MemoryVaultApplicationTests*"`

Expected: PASS (Flyway applies V10 against TestContainers PostgreSQL)

- [ ] **Step 8: Commit**

```bash
git add build.gradle.kts src/main/resources/db/migration/V10__billing.sql src/main/kotlin/org/sightech/memoryvault/billing/entity/BillingEnums.kt src/main/kotlin/org/sightech/memoryvault/scheduling/entity/SyncJob.kt src/main/resources/application.properties .env.sample
git commit -m "feat(billing): V10 migration, Stripe SDK, AOP dependency, billing enums, config"
```

---

## Task 2: User Entity Update + Subscription Entity + Repositories

**Files:**
- Modify: `.../auth/entity/User.kt`
- Create: `.../billing/entity/SubscriptionEntity.kt`
- Create: `.../billing/repository/SubscriptionRepository.kt`
- Modify: `.../auth/repository/UserRepository.kt`

- [ ] **Step 1: Add tier and stripeCustomerId to User entity**

In `src/main/kotlin/org/sightech/memoryvault/auth/entity/User.kt`, add the import and two fields. Add after the existing `role` field:

Add import at top:
```kotlin
import org.sightech.memoryvault.billing.entity.Tier
```

Add field after `role`:
```kotlin
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
var tier: Tier = Tier.FREE,

@Column(name = "stripe_customer_id", length = 255)
var stripeCustomerId: String? = null,
```

- [ ] **Step 2: Create SubscriptionEntity**

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

    @Column(name = "stripe_customer_id", nullable = false, length = 255)
    val stripeCustomerId: String,

    @Column(name = "stripe_subscription_id", nullable = false, unique = true, length = 255)
    val stripeSubscriptionId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 10)
    val billingInterval: BillingInterval,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SubscriptionStatus = SubscriptionStatus.ACTIVE,

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

- [ ] **Step 3: Create SubscriptionRepository**

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

    @Query("SELECT s FROM SubscriptionEntity s WHERE s.status = 'GRACE_PERIOD' AND s.gracePeriodEndsAt < :now")
    fun findExpiredGracePeriods(now: Instant): List<SubscriptionEntity>

    @Query("SELECT s FROM SubscriptionEntity s WHERE s.status = 'ACTIVE' AND s.billingInterval = 'MONTHLY'")
    fun findActiveBackupSubscriptions(): List<SubscriptionEntity>
}
```

- [ ] **Step 4: Add findAllActive to UserRepository**

In `src/main/kotlin/org/sightech/memoryvault/auth/repository/UserRepository.kt`, add:

```kotlin
@Query("SELECT u FROM User u WHERE u.deletedAt IS NULL ORDER BY u.createdAt DESC")
fun findAllActive(): List<User>
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew compileKotlin`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/auth/entity/User.kt src/main/kotlin/org/sightech/memoryvault/billing/entity/SubscriptionEntity.kt src/main/kotlin/org/sightech/memoryvault/billing/repository/SubscriptionRepository.kt src/main/kotlin/org/sightech/memoryvault/auth/repository/UserRepository.kt
git commit -m "feat(billing): add tier/stripeCustomerId to User, SubscriptionEntity and repository"
```

---

## Task 3: StripeService

**Files:**
- Create: `.../billing/service/StripeService.kt`
- Create: `src/test/kotlin/.../billing/service/StripeServiceTest.kt`

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/org/sightech/memoryvault/billing/service/StripeServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.billing.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.sightech.memoryvault.billing.entity.BillingInterval
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StripeServiceTest {

    private val service = StripeService(
        secretKey = "sk_test_fake",
        webhookSecret = "whsec_fake",
        premiumMonthlyPriceId = "price_premium_monthly",
        premiumAnnualPriceId = "price_premium_annual",
        backupMonthlyPriceId = "price_backup_monthly",
        backupAnnualPriceId = "price_backup_annual"
    )

    @Test
    fun `resolvePriceId returns premium monthly price`() {
        assertEquals("price_premium_monthly",
            service.resolvePriceId("PREMIUM", BillingInterval.MONTHLY))
    }

    @Test
    fun `resolvePriceId returns premium annual price`() {
        assertEquals("price_premium_annual",
            service.resolvePriceId("PREMIUM", BillingInterval.ANNUAL))
    }

    @Test
    fun `resolvePriceId returns backup monthly price`() {
        assertEquals("price_backup_monthly",
            service.resolvePriceId("BACKUP", BillingInterval.MONTHLY))
    }

    @Test
    fun `resolvePriceId returns backup annual price`() {
        assertEquals("price_backup_annual",
            service.resolvePriceId("BACKUP", BillingInterval.ANNUAL))
    }

    @Test
    fun `resolvePriceId throws for unknown tier`() {
        assertThrows<IllegalArgumentException> {
            service.resolvePriceId("FREE", BillingInterval.MONTHLY)
        }
    }

    @Test
    fun `StripeService is constructable with config values`() {
        assertNotNull(service)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*StripeServiceTest*"`

Expected: FAIL (class not found)

- [ ] **Step 3: Implement StripeService**

Create `src/main/kotlin/org/sightech/memoryvault/billing/service/StripeService.kt`:

```kotlin
package org.sightech.memoryvault.billing.service

import com.stripe.Stripe
import com.stripe.model.Customer
import com.stripe.model.Subscription
import com.stripe.model.UsageRecord
import com.stripe.model.billing_portal.Session as PortalSession
import com.stripe.model.checkout.Session as CheckoutSession
import com.stripe.net.Webhook
import com.stripe.param.CustomerCreateParams
import com.stripe.param.checkout.SessionCreateParams
import com.stripe.param.billing_portal.SessionCreateParams as PortalSessionCreateParams
import com.stripe.param.UsageRecordCreateOnSubscriptionItemParams
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct
import java.time.Instant

@Service
class StripeService(
    @Value("\${stripe.secret.key:}") private val secretKey: String,
    @Value("\${stripe.webhook.secret:}") val webhookSecret: String,
    @Value("\${stripe.price.premium.monthly:}") private val premiumMonthlyPriceId: String,
    @Value("\${stripe.price.premium.annual:}") private val premiumAnnualPriceId: String,
    @Value("\${stripe.price.backup.monthly:}") private val backupMonthlyPriceId: String,
    @Value("\${stripe.price.backup.annual:}") private val backupAnnualPriceId: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun init() {
        if (secretKey.isNotBlank()) {
            Stripe.apiKey = secretKey
            log.info("Stripe SDK initialized")
        } else {
            log.info("Stripe secret key not configured — billing calls will fail")
        }
    }

    fun resolvePriceId(tier: String, interval: BillingInterval): String = when ("$tier:$interval") {
        "PREMIUM:MONTHLY" -> premiumMonthlyPriceId
        "PREMIUM:ANNUAL" -> premiumAnnualPriceId
        "BACKUP:MONTHLY" -> backupMonthlyPriceId
        "BACKUP:ANNUAL" -> backupAnnualPriceId
        else -> throw IllegalArgumentException("No price ID for tier=$tier interval=$interval")
    }

    fun createCustomer(email: String): Customer {
        val params = CustomerCreateParams.builder().setEmail(email).build()
        return Customer.create(params)
    }

    fun createCheckoutSession(
        customerId: String,
        priceId: String,
        successUrl: String,
        cancelUrl: String
    ): CheckoutSession {
        val params = SessionCreateParams.builder()
            .setCustomer(customerId)
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1L)
                    .build()
            )
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .build()
        return CheckoutSession.create(params)
    }

    fun createPortalSession(customerId: String, returnUrl: String): PortalSession {
        val params = PortalSessionCreateParams.builder()
            .setCustomer(customerId)
            .setReturnUrl(returnUrl)
            .build()
        return PortalSession.create(params)
    }

    fun retrieveSubscription(stripeSubscriptionId: String): Subscription =
        Subscription.retrieve(stripeSubscriptionId)

    fun reportUsage(subscriptionItemId: String, quantity: Long, timestamp: Instant) {
        val params = UsageRecordCreateOnSubscriptionItemParams.builder()
            .setQuantity(quantity)
            .setTimestamp(timestamp.epochSecond)
            .setAction(UsageRecordCreateOnSubscriptionItemParams.Action.SET)
            .build()
        UsageRecord.createOnSubscriptionItem(subscriptionItemId, params, null)
    }

    fun constructEvent(payload: String, sigHeader: String) =
        Webhook.constructEvent(payload, sigHeader, webhookSecret)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "*StripeServiceTest*"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/billing/service/StripeService.kt src/test/kotlin/org/sightech/memoryvault/billing/service/StripeServiceTest.kt
git commit -m "feat(billing): add StripeService with Stripe SDK wiring and price resolution"
```

---

## Task 4: BillingService

**Files:**
- Create: `.../billing/service/BillingService.kt`
- Create: `src/test/kotlin/.../billing/service/BillingServiceTest.kt`

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/org/sightech/memoryvault/billing/service/BillingServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.billing.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.entity.*
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BillingServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val stripeService = mockk<StripeService>()

    private val service = BillingService(userRepository, subscriptionRepository, stripeService, 30)

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val user = User(
        id = userId, email = "test@example.com", displayName = "Test",
        role = UserRole.VIEWER, tier = Tier.FREE
    )

    @BeforeEach
    fun setUp() {
        every { userRepository.save(any()) } answers { firstArg() }
        every { subscriptionRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `setUserTier updates user tier and saves`() {
        every { userRepository.findByIdAndDeletedAtIsNull(userId) } returns user

        service.setUserTier(userId, Tier.PREMIUM)

        assertEquals(Tier.PREMIUM, user.tier)
        verify { userRepository.save(user) }
    }

    @Test
    fun `setUserTier returns false when user not found`() {
        every { userRepository.findByIdAndDeletedAtIsNull(userId) } returns null

        val result = service.setUserTier(userId, Tier.PREMIUM)

        assertEquals(false, result)
    }

    @Test
    fun `handleCheckoutCompleted creates subscription and upgrades tier`() {
        every { userRepository.findByIdAndDeletedAtIsNull(userId) } returns user
        every { subscriptionRepository.findByUserId(userId) } returns null

        service.handleCheckoutCompleted(
            userId = userId,
            stripeCustomerId = "cus_abc",
            stripeSubscriptionId = "sub_abc",
            tier = Tier.PREMIUM,
            interval = BillingInterval.MONTHLY
        )

        assertEquals(Tier.PREMIUM, user.tier)
        assertEquals("cus_abc", user.stripeCustomerId)
        verify { subscriptionRepository.save(any()) }
        verify { userRepository.save(user) }
    }

    @Test
    fun `handleSubscriptionDeleted sets grace period for BACKUP cancellation`() {
        val sub = SubscriptionEntity(
            userId = userId,
            stripeCustomerId = "cus_abc",
            stripeSubscriptionId = "sub_abc",
            billingInterval = BillingInterval.MONTHLY,
            status = SubscriptionStatus.ACTIVE
        )
        every { subscriptionRepository.findByStripeSubscriptionId("sub_abc") } returns sub
        every { userRepository.findByIdAndDeletedAtIsNull(userId) } returns user.apply { tier = Tier.BACKUP }

        service.handleSubscriptionDeleted("sub_abc")

        assertEquals(SubscriptionStatus.GRACE_PERIOD, sub.status)
        assertNotNull(sub.gracePeriodEndsAt)
        assertEquals(Tier.FREE, user.tier)
    }

    @Test
    fun `handleSubscriptionDeleted is no-op when subscription not found`() {
        every { subscriptionRepository.findByStripeSubscriptionId("sub_unknown") } returns null

        service.handleSubscriptionDeleted("sub_unknown")

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `getSubscriptionInfo returns null when no subscription exists`() {
        every { subscriptionRepository.findByUserId(userId) } returns null

        assertNull(service.getSubscriptionInfo(userId))
    }

    @Test
    fun `getSubscriptionInfo returns active subscription data`() {
        val sub = SubscriptionEntity(
            userId = userId,
            stripeCustomerId = "cus_abc",
            stripeSubscriptionId = "sub_abc",
            billingInterval = BillingInterval.MONTHLY,
            status = SubscriptionStatus.ACTIVE
        )
        every { subscriptionRepository.findByUserId(userId) } returns sub
        every { userRepository.findByIdAndDeletedAtIsNull(userId) } returns user.apply { tier = Tier.PREMIUM }

        val info = service.getSubscriptionInfo(userId)

        assertNotNull(info)
        assertEquals("PREMIUM", info.tier)
        assertEquals("ACTIVE", info.status)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*BillingServiceTest*"`

Expected: FAIL (class not found)

- [ ] **Step 3: Implement BillingService**

Create `src/main/kotlin/org/sightech/memoryvault/billing/service/BillingService.kt`:

```kotlin
package org.sightech.memoryvault.billing.service

import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.entity.*
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class SubscriptionInfo(
    val tier: String,
    val status: String,
    val billingInterval: String?,
    val gracePeriodEndsAt: Instant?,
    val lastUsageReportedAt: Instant?
)

@Service
class BillingService(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val stripeService: StripeService,
    @Value("\${memoryvault.billing.grace-period-days:30}") private val gracePeriodDays: Long
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun setUserTier(userId: UUID, tier: Tier): Boolean {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId) ?: return false
        user.tier = tier
        user.updatedAt = Instant.now()
        userRepository.save(user)
        log.info("Tier updated userId={} tier={}", userId, tier)
        return true
    }

    fun handleCheckoutCompleted(
        userId: UUID,
        stripeCustomerId: String,
        stripeSubscriptionId: String,
        tier: Tier,
        interval: BillingInterval
    ) {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId) ?: run {
            log.warn("Checkout completed but user not found userId={}", userId)
            return
        }

        user.tier = tier
        user.stripeCustomerId = stripeCustomerId
        user.updatedAt = Instant.now()
        userRepository.save(user)

        val existing = subscriptionRepository.findByUserId(userId)
        if (existing != null) {
            existing.status = SubscriptionStatus.ACTIVE
            existing.gracePeriodEndsAt = null
            existing.updatedAt = Instant.now()
            subscriptionRepository.save(existing)
        } else {
            subscriptionRepository.save(
                SubscriptionEntity(
                    userId = userId,
                    stripeCustomerId = stripeCustomerId,
                    stripeSubscriptionId = stripeSubscriptionId,
                    billingInterval = interval,
                    status = SubscriptionStatus.ACTIVE
                )
            )
        }
        log.info("Checkout completed userId={} tier={}", userId, tier)
    }

    fun handleInvoicePaid(stripeSubscriptionId: String) {
        val sub = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId) ?: return
        sub.status = SubscriptionStatus.ACTIVE
        sub.updatedAt = Instant.now()
        subscriptionRepository.save(sub)
        log.info("Invoice paid stripeSubscriptionId={}", stripeSubscriptionId)
    }

    fun handleInvoicePaymentFailed(stripeSubscriptionId: String) {
        val sub = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId) ?: return
        sub.status = SubscriptionStatus.PAST_DUE
        sub.updatedAt = Instant.now()
        subscriptionRepository.save(sub)
        log.warn("Invoice payment failed stripeSubscriptionId={}", stripeSubscriptionId)
    }

    fun handleSubscriptionDeleted(stripeSubscriptionId: String) {
        val sub = subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId) ?: run {
            log.warn("Subscription deleted event for unknown sub={}", stripeSubscriptionId)
            return
        }
        val user = userRepository.findByIdAndDeletedAtIsNull(sub.userId)
        val wasBackup = user?.tier == Tier.BACKUP

        sub.status = SubscriptionStatus.GRACE_PERIOD
        sub.gracePeriodEndsAt = Instant.now().plus(gracePeriodDays, ChronoUnit.DAYS)
        sub.updatedAt = Instant.now()
        subscriptionRepository.save(sub)

        if (user != null) {
            user.tier = Tier.FREE
            user.updatedAt = Instant.now()
            userRepository.save(user)
        }

        log.info("Subscription deleted stripeSubscriptionId={} wasBackup={} gracePeriodEndsAt={}",
            stripeSubscriptionId, wasBackup, sub.gracePeriodEndsAt)
    }

    fun getSubscriptionInfo(userId: UUID): SubscriptionInfo? {
        val sub = subscriptionRepository.findByUserId(userId) ?: return null
        val user = userRepository.findByIdAndDeletedAtIsNull(userId) ?: return null
        return SubscriptionInfo(
            tier = user.tier.name,
            status = sub.status.name,
            billingInterval = sub.billingInterval.name,
            gracePeriodEndsAt = sub.gracePeriodEndsAt,
            lastUsageReportedAt = sub.lastUsageReportedAt
        )
    }

    fun ensureStripeCustomer(userId: UUID, email: String): String {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: throw IllegalStateException("User not found: $userId")
        if (user.stripeCustomerId != null) return user.stripeCustomerId!!

        val customer = stripeService.createCustomer(email)
        user.stripeCustomerId = customer.id
        user.updatedAt = Instant.now()
        userRepository.save(user)
        log.info("Created Stripe customer userId={} customerId={}", userId, customer.id)
        return customer.id
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "*BillingServiceTest*"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/billing/service/BillingService.kt src/test/kotlin/org/sightech/memoryvault/billing/service/BillingServiceTest.kt
git commit -m "feat(billing): add BillingService with tier transitions, checkout, and subscription lifecycle"
```

---

## Task 5: @TierGate Annotation + TierEnforcementAspect

**Files:**
- Create: `.../billing/tier/TierGate.kt`
- Create: `.../billing/tier/TierRequiredException.kt`
- Create: `.../billing/tier/TierEnforcementAspect.kt`
- Create: `src/test/kotlin/.../billing/tier/TierEnforcementAspectTest.kt`
- Modify: `.../youtube/service/YoutubeListService.kt` (add `@TierGate`)
- Modify: `.../feed/service/FeedService.kt` (add free-tier feed count check)

- [ ] **Step 1: Create TierGate annotation**

Create `src/main/kotlin/org/sightech/memoryvault/billing/tier/TierGate.kt`:

```kotlin
package org.sightech.memoryvault.billing.tier

import org.sightech.memoryvault.billing.entity.Tier

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TierGate(val value: Tier)
```

- [ ] **Step 2: Create TierRequiredException**

Create `src/main/kotlin/org/sightech/memoryvault/billing/tier/TierRequiredException.kt`:

```kotlin
package org.sightech.memoryvault.billing.tier

import org.sightech.memoryvault.billing.entity.Tier
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.FORBIDDEN)
class TierRequiredException(required: Tier) :
    RuntimeException("This feature requires the ${required.name} tier or higher")
```

- [ ] **Step 3: Write failing tests**

Create `src/test/kotlin/org/sightech/memoryvault/billing/tier/TierEnforcementAspectTest.kt`:

```kotlin
package org.sightech.memoryvault.billing.tier

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.entity.Tier
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class TierEnforcementAspectTest {

    private val userRepository = mockk<UserRepository>()
    private val aspect = TierEnforcementAspect(userRepository)

    private fun setSecurityContext(userId: UUID, role: UserRole) {
        val auth = UsernamePasswordAuthenticationToken(
            userId.toString(), null,
            listOf(SimpleGrantedAuthority("ROLE_${role.name}"))
        )
        SecurityContextHolder.getContext().authentication = auth
    }

    @Test
    fun `OWNER bypasses tier check`() {
        val userId = UUID.randomUUID()
        setSecurityContext(userId, UserRole.OWNER)
        val annotation = object : TierGate {
            override val value = Tier.BACKUP
            override fun annotationType() = TierGate::class.java
        }
        aspect.checkTier(annotation)
        // no exception expected
    }

    @Test
    fun `BACKUP user passes BACKUP gate`() {
        val userId = UUID.randomUUID()
        setSecurityContext(userId, UserRole.VIEWER)
        every { userRepository.findByIdAndDeletedAtIsNull(userId) } returns User(
            id = userId, email = "test@test.com", displayName = "Test",
            role = UserRole.VIEWER, tier = Tier.BACKUP
        )
        val annotation = object : TierGate {
            override val value = Tier.BACKUP
            override fun annotationType() = TierGate::class.java
        }
        aspect.checkTier(annotation)
        // no exception
    }

    @Test
    fun `FREE user fails BACKUP gate`() {
        val userId = UUID.randomUUID()
        setSecurityContext(userId, UserRole.VIEWER)
        every { userRepository.findByIdAndDeletedAtIsNull(userId) } returns User(
            id = userId, email = "test@test.com", displayName = "Test",
            role = UserRole.VIEWER, tier = Tier.FREE
        )
        val annotation = object : TierGate {
            override val value = Tier.BACKUP
            override fun annotationType() = TierGate::class.java
        }
        assertThrows<TierRequiredException> {
            aspect.checkTier(annotation)
        }
    }

    @Test
    fun `PREMIUM user fails BACKUP gate`() {
        val userId = UUID.randomUUID()
        setSecurityContext(userId, UserRole.VIEWER)
        every { userRepository.findByIdAndDeletedAtIsNull(userId) } returns User(
            id = userId, email = "test@test.com", displayName = "Test",
            role = UserRole.VIEWER, tier = Tier.PREMIUM
        )
        val annotation = object : TierGate {
            override val value = Tier.BACKUP
            override fun annotationType() = TierGate::class.java
        }
        assertThrows<TierRequiredException> {
            aspect.checkTier(annotation)
        }
    }

    @Test
    fun `PREMIUM user passes PREMIUM gate`() {
        val userId = UUID.randomUUID()
        setSecurityContext(userId, UserRole.VIEWER)
        every { userRepository.findByIdAndDeletedAtIsNull(userId) } returns User(
            id = userId, email = "test@test.com", displayName = "Test",
            role = UserRole.VIEWER, tier = Tier.PREMIUM
        )
        val annotation = object : TierGate {
            override val value = Tier.PREMIUM
            override fun annotationType() = TierGate::class.java
        }
        aspect.checkTier(annotation)
        // no exception
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew test --tests "*TierEnforcementAspectTest*"`

Expected: FAIL (class not found)

- [ ] **Step 5: Implement TierEnforcementAspect**

Create `src/main/kotlin/org/sightech/memoryvault/billing/tier/TierEnforcementAspect.kt`:

```kotlin
package org.sightech.memoryvault.billing.tier

import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.entity.Tier
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

@Aspect
@Component
class TierEnforcementAspect(private val userRepository: UserRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Before("@annotation(tierGate)")
    fun checkTier(tierGate: TierGate) {
        val auth = SecurityContextHolder.getContext().authentication ?: return
        val isOwner = auth.authorities.any { it.authority == "ROLE_OWNER" }
        if (isOwner) return

        val userId = try {
            UUID.fromString(auth.principal as? String ?: return)
        } catch (e: IllegalArgumentException) {
            return
        }

        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
        val userTier = user?.tier ?: Tier.FREE

        val passes = when (tierGate.value) {
            Tier.FREE -> true
            Tier.PREMIUM -> userTier == Tier.PREMIUM || userTier == Tier.BACKUP
            Tier.BACKUP -> userTier == Tier.BACKUP
        }

        if (!passes) {
            log.warn("Tier gate denied userId={} required={} actual={}", userId, tierGate.value, userTier)
            throw TierRequiredException(tierGate.value)
        }
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests "*TierEnforcementAspectTest*"`

Expected: PASS

- [ ] **Step 7: Add @TierGate to YoutubeListService**

In `src/main/kotlin/org/sightech/memoryvault/youtube/service/YoutubeListService.kt`, add the import and annotation on `addList` and `refreshList`:

```kotlin
import org.sightech.memoryvault.billing.tier.TierGate
import org.sightech.memoryvault.billing.entity.Tier
```

Add `@TierGate(Tier.BACKUP)` above `fun addList(...)` and above `fun refreshList(...)`.

- [ ] **Step 8: Add free-tier feed count guard to FeedService**

In `src/main/kotlin/org/sightech/memoryvault/feed/service/FeedService.kt`, inject the limit and add the check at the start of `addFeed`:

Add constructor parameter:
```kotlin
@Value("\${memoryvault.billing.free-feed-limit:100}") private val freeFeedLimit: Int
```

Add import:
```kotlin
import org.sightech.memoryvault.billing.entity.Tier
import org.sightech.memoryvault.billing.tier.TierRequiredException
```

At the top of `addFeed`, before any existing logic:
```kotlin
val user = userRepository.findByIdAndDeletedAtIsNull(userId)
val isOwner = user?.role?.name == "OWNER"
if (!isOwner && user?.tier == Tier.FREE) {
    val feedCount = feedRepository.countByUserIdAndDeletedAtIsNull(userId)
    if (feedCount >= freeFeedLimit) {
        throw TierRequiredException(Tier.PREMIUM)
    }
}
```

Add `countByUserIdAndDeletedAtIsNull(userId: UUID): Long` to `FeedRepository` if not already present.

- [ ] **Step 9: Run all tests**

Run: `./gradlew test`

Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/billing/tier/ src/test/kotlin/org/sightech/memoryvault/billing/tier/ src/main/kotlin/org/sightech/memoryvault/youtube/service/YoutubeListService.kt src/main/kotlin/org/sightech/memoryvault/feed/service/FeedService.kt
git commit -m "feat(billing): add @TierGate AOP enforcement, wire into YoutubeListService and FeedService"
```

---

## Task 6: BillingController + StripeWebhookController + SecurityConfig

**Files:**
- Create: `.../billing/dto/BillingDtos.kt`
- Create: `.../billing/controller/BillingController.kt`
- Create: `.../billing/controller/StripeWebhookController.kt`
- Create: `src/test/kotlin/.../billing/controller/StripeWebhookControllerTest.kt`
- Modify: `.../config/SecurityConfig.kt`

- [ ] **Step 1: Create BillingDtos.kt**

Create `src/main/kotlin/org/sightech/memoryvault/billing/dto/BillingDtos.kt`:

```kotlin
package org.sightech.memoryvault.billing.dto

data class CheckoutRequest(val tier: String, val interval: String)
data class CheckoutResponse(val url: String)
data class PortalResponse(val url: String)
```

- [ ] **Step 2: Create BillingController**

Create `src/main/kotlin/org/sightech/memoryvault/billing/controller/BillingController.kt`:

```kotlin
package org.sightech.memoryvault.billing.controller

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.dto.CheckoutRequest
import org.sightech.memoryvault.billing.dto.CheckoutResponse
import org.sightech.memoryvault.billing.dto.PortalResponse
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.service.BillingService
import org.sightech.memoryvault.billing.service.StripeService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/billing")
class BillingController(
    private val billingService: BillingService,
    private val stripeService: StripeService,
    private val userRepository: UserRepository,
    @Value("\${memoryvault.frontend.url:http://localhost:4200}") private val frontendUrl: String
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/checkout")
    fun createCheckout(@RequestBody request: CheckoutRequest): ResponseEntity<CheckoutResponse> {
        val userId = CurrentUser.userId()
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: return ResponseEntity.notFound().build()

        val interval = BillingInterval.valueOf(request.interval.uppercase())
        val priceId = stripeService.resolvePriceId(request.tier.uppercase(), interval)
        val customerId = billingService.ensureStripeCustomer(userId, user.email)

        val session = stripeService.createCheckoutSession(
            customerId = customerId,
            priceId = priceId,
            successUrl = "$frontendUrl/billing/success",
            cancelUrl = "$frontendUrl/billing/cancelled"
        )
        log.info("Checkout session created userId={} tier={}", userId, request.tier)
        return ResponseEntity.ok(CheckoutResponse(session.url))
    }

    @PostMapping("/portal")
    fun createPortal(): ResponseEntity<PortalResponse> {
        val userId = CurrentUser.userId()
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: return ResponseEntity.notFound().build()
        val customerId = user.stripeCustomerId
            ?: return ResponseEntity.badRequest().build()

        val session = stripeService.createPortalSession(customerId, "$frontendUrl/billing")
        log.info("Portal session created userId={}", userId)
        return ResponseEntity.ok(PortalResponse(session.url))
    }

    @GetMapping("/subscription")
    fun getSubscription(): ResponseEntity<Any> {
        val userId = CurrentUser.userId()
        val info = billingService.getSubscriptionInfo(userId)
            ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(info)
    }
}
```

- [ ] **Step 3: Create StripeWebhookController**

Create `src/main/kotlin/org/sightech/memoryvault/billing/controller/StripeWebhookController.kt`:

```kotlin
package org.sightech.memoryvault.billing.controller

import com.stripe.model.checkout.Session
import com.stripe.model.Invoice
import com.stripe.net.Webhook
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.entity.BillingInterval
import org.sightech.memoryvault.billing.entity.Tier
import org.sightech.memoryvault.billing.service.BillingService
import org.sightech.memoryvault.billing.service.StripeService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/stripe")
class StripeWebhookController(
    private val billingService: BillingService,
    private val stripeService: StripeService,
    private val userRepository: UserRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/webhook")
    fun handleWebhook(
        @RequestBody payload: String,
        @RequestHeader("Stripe-Signature") sigHeader: String
    ): ResponseEntity<String> {
        val event = try {
            stripeService.constructEvent(payload, sigHeader)
        } catch (e: Exception) {
            log.warn("Stripe webhook signature validation failed: {}", e.message)
            return ResponseEntity.badRequest().body("Invalid signature")
        }

        log.info("Stripe webhook received type={}", event.type)

        try {
            when (event.type) {
                "checkout.session.completed" -> {
                    val session = event.dataObjectDeserializer.`object`.orElse(null) as? Session ?: return ResponseEntity.ok("ok")
                    val clientRefId = session.clientReferenceId ?: return ResponseEntity.ok("ok")
                    val userId = UUID.fromString(clientRefId)
                    val metadata = session.metadata ?: emptyMap()
                    val tier = Tier.valueOf(metadata["tier"] ?: "PREMIUM")
                    val interval = BillingInterval.valueOf(metadata["interval"] ?: "MONTHLY")
                    billingService.handleCheckoutCompleted(
                        userId = userId,
                        stripeCustomerId = session.customer,
                        stripeSubscriptionId = session.subscription,
                        tier = tier,
                        interval = interval
                    )
                }
                "invoice.paid" -> {
                    val invoice = event.dataObjectDeserializer.`object`.orElse(null) as? Invoice ?: return ResponseEntity.ok("ok")
                    invoice.subscription?.let { billingService.handleInvoicePaid(it) }
                }
                "invoice.payment_failed" -> {
                    val invoice = event.dataObjectDeserializer.`object`.orElse(null) as? Invoice ?: return ResponseEntity.ok("ok")
                    invoice.subscription?.let { billingService.handleInvoicePaymentFailed(it) }
                }
                "customer.subscription.deleted" -> {
                    val sub = event.dataObjectDeserializer.`object`.orElse(null) as? com.stripe.model.Subscription ?: return ResponseEntity.ok("ok")
                    billingService.handleSubscriptionDeleted(sub.id)
                }
                else -> log.debug("Unhandled Stripe event type={}", event.type)
            }
        } catch (e: Exception) {
            log.warn("Stripe webhook processing error type={}: {}", event.type, e.message, e)
        }

        return ResponseEntity.ok("ok")
    }
}
```

- [ ] **Step 4: Write webhook controller tests**

Create `src/test/kotlin/org/sightech/memoryvault/billing/controller/StripeWebhookControllerTest.kt`:

```kotlin
package org.sightech.memoryvault.billing.controller

import io.mockk.*
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.service.BillingService
import org.sightech.memoryvault.billing.service.StripeService
import kotlin.test.assertEquals

class StripeWebhookControllerTest {

    private val billingService = mockk<BillingService>(relaxed = true)
    private val stripeService = mockk<StripeService>()
    private val userRepository = mockk<UserRepository>()
    private val controller = StripeWebhookController(billingService, stripeService, userRepository)

    @Test
    fun `returns 400 on invalid signature`() {
        every { stripeService.constructEvent(any(), any()) } throws Exception("Invalid signature")

        val response = controller.handleWebhook("{}", "bad-sig")

        assertEquals(400, response.statusCode.value())
        verify(exactly = 0) { billingService.handleCheckoutCompleted(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `returns 200 on processing error without retrying`() {
        val mockEvent = mockk<com.stripe.model.Event> {
            every { type } returns "checkout.session.completed"
            every { dataObjectDeserializer } returns mockk { every { `object` } returns java.util.Optional.empty() }
        }
        every { stripeService.constructEvent(any(), any()) } returns mockEvent

        val response = controller.handleWebhook("{}", "valid-sig")

        assertEquals(200, response.statusCode.value())
    }
}
```

- [ ] **Step 5: Update SecurityConfig to allow webhook and billing/signup routes**

In `src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt`, in the `authorizeHttpRequests` block, add after the existing `.requestMatchers("/api/internal/**").permitAll()` line:

```kotlin
.requestMatchers("/api/stripe/webhook").permitAll()
```

And add `/signup`, `/billing`, `/billing/**` to the Angular SPA route permit list:

```kotlin
.requestMatchers("/login", "/signup", "/billing", "/billing/**", "/reader", "/bookmarks/**", "/youtube/**", "/admin/**", "/search/**").permitAll()
```

Also add `/api/me` mapping note — `GET /api/me` is added in Task 10; it requires authentication (no special permit needed, already covered by `anyRequest().authenticated()`).

- [ ] **Step 6: Run all tests**

Run: `./gradlew test`

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/billing/dto/ src/main/kotlin/org/sightech/memoryvault/billing/controller/ src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt src/test/kotlin/org/sightech/memoryvault/billing/controller/StripeWebhookControllerTest.kt
git commit -m "feat(billing): add BillingController, StripeWebhookController, SecurityConfig permit rules"
```

---

## Task 7: StorageUsageReporter + VideoPurgeService + BillingSyncRegistrar

**Files:**
- Create: `.../billing/service/StorageUsageReporter.kt`
- Create: `.../billing/service/VideoPurgeService.kt`
- Create: `.../billing/BillingSyncRegistrar.kt`

- [ ] **Step 1: Create StorageUsageReporter**

Create `src/main/kotlin/org/sightech/memoryvault/billing/service/StorageUsageReporter.kt`:

```kotlin
package org.sightech.memoryvault.billing.service

import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import org.sightech.memoryvault.billing.entity.Tier
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.sightech.memoryvault.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class StorageUsageReporter(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val stripeService: StripeService,
    private val storageService: StorageService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun reportUsageForAllBackupUsers(): Map<String, Any> {
        val backupUsers = userRepository.findAllActive().filter { it.tier == Tier.BACKUP }
        var reported = 0
        var skipped = 0
        var errors = 0

        for (user in backupUsers) {
            try {
                val sub = subscriptionRepository.findByUserId(user.id) ?: continue
                if (sub.status != SubscriptionStatus.ACTIVE) continue

                val stripeSubscription = stripeService.retrieveSubscription(sub.stripeSubscriptionId)
                val meteredItem = stripeSubscription.items?.data?.firstOrNull { item ->
                    item.price?.recurring?.usageType == "metered"
                } ?: continue

                val lastReported = sub.lastUsageReportedAt
                val now = Instant.now()
                val storageBytes = storageService.getStorageUsedBytes(user.id)
                val storageGb = (storageBytes / (1024L * 1024 * 1024)).coerceAtLeast(0L)

                if (lastReported == null || ChronoUnit.HOURS.between(lastReported, now) >= 20) {
                    stripeService.reportUsage(meteredItem.id, storageGb, now)
                    sub.lastUsageReportedAt = now
                    sub.updatedAt = now
                    subscriptionRepository.save(sub)
                    log.info("Reported usage userId={} storageGb={}", user.id, storageGb)
                    reported++
                } else {
                    skipped++
                }
            } catch (e: Exception) {
                log.warn("Usage report failed userId={}: {}", user.id, e.message)
                errors++
            }
        }

        return mapOf("reported" to reported, "skipped" to skipped, "errors" to errors)
    }
}
```

- [ ] **Step 2: Add getStorageUsedBytes to StorageService**

In `src/main/kotlin/org/sightech/memoryvault/storage/StorageService.kt`, add the interface method:

```kotlin
fun getStorageUsedBytes(userId: UUID): Long
```

In `LocalStorageService`, implement it by summing file sizes under the user's local storage directory (or return 0 if not applicable locally). In `S3StorageService`, implement it by listing objects with the user prefix and summing sizes.

`LocalStorageService` stub:
```kotlin
override fun getStorageUsedBytes(userId: UUID): Long = 0L
```

`S3StorageService` (adds to existing AWS profile implementation):
```kotlin
override fun getStorageUsedBytes(userId: UUID): Long {
    var total = 0L
    var continuationToken: String? = null
    do {
        val request = ListObjectsV2Request.builder()
            .bucket(bucketName)
            .prefix("videos/$userId/")
            .continuationToken(continuationToken)
            .build()
        val response = s3Client.listObjectsV2(request)
        total += response.contents().sumOf { it.size() }
        continuationToken = if (response.isTruncated) response.nextContinuationToken() else null
    } while (continuationToken != null)
    return total
}
```

- [ ] **Step 3: Create VideoPurgeService**

Create `src/main/kotlin/org/sightech/memoryvault/billing/service/VideoPurgeService.kt`:

```kotlin
package org.sightech.memoryvault.billing.service

import org.sightech.memoryvault.billing.entity.SubscriptionStatus
import org.sightech.memoryvault.billing.repository.SubscriptionRepository
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class VideoPurgeService(
    private val subscriptionRepository: SubscriptionRepository,
    private val videoRepository: VideoRepository,
    private val storageService: StorageService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun purgeExpiredVideoFiles(): Map<String, Any> {
        val expired = subscriptionRepository.findExpiredGracePeriods(Instant.now())
        var purgedFiles = 0
        var purgedUsers = 0

        for (sub in expired) {
            try {
                val videos = videoRepository.findByUserIdAndFilePathNotNull(sub.userId)
                for (video in videos) {
                    video.filePath?.let { path ->
                        try {
                            storageService.delete(path)
                            video.filePath = null
                            video.updatedAt = Instant.now()
                            videoRepository.save(video)
                            purgedFiles++
                        } catch (e: Exception) {
                            log.warn("Failed to delete video file videoId={} path={}: {}", video.id, path, e.message)
                        }
                    }
                }
                sub.status = SubscriptionStatus.CANCELLED
                sub.updatedAt = Instant.now()
                subscriptionRepository.save(sub)
                purgedUsers++
                log.info("Grace period expired purged userId={} files={}", sub.userId, videos.size)
            } catch (e: Exception) {
                log.warn("Purge failed for userId={}: {}", sub.userId, e.message)
            }
        }

        return mapOf("purgedUsers" to purgedUsers, "purgedFiles" to purgedFiles)
    }
}
```

Add `findByUserIdAndFilePathNotNull(userId: UUID): List<Video>` to `VideoRepository` if not already present.

Also add `delete(key: String)` to `StorageService` interface if not already present, with local and S3 implementations.

- [ ] **Step 4: Create BillingSyncRegistrar**

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
    fun registerBillingJobs() {
        jobScheduler.schedule("storage-usage-report", usageReportCron, JobType.STORAGE_USAGE_REPORT) {
            log.info("Storage usage report job starting")
            val result = storageUsageReporter.reportUsageForAllBackupUsers()
            log.info("Storage usage report complete: {}", result)
            @Suppress("UNCHECKED_CAST")
            result as Map<String, Any>
        }

        jobScheduler.schedule("video-purge", videoPurgeCron, JobType.VIDEO_PURGE) {
            log.info("Video purge job starting")
            val result = videoPurgeService.purgeExpiredVideoFiles()
            log.info("Video purge complete: {}", result)
            @Suppress("UNCHECKED_CAST")
            result as Map<String, Any>
        }
    }
}
```

- [ ] **Step 5: Run all tests**

Run: `./gradlew test`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/billing/service/StorageUsageReporter.kt src/main/kotlin/org/sightech/memoryvault/billing/service/VideoPurgeService.kt src/main/kotlin/org/sightech/memoryvault/billing/BillingSyncRegistrar.kt src/main/kotlin/org/sightech/memoryvault/storage/
git commit -m "feat(billing): add StorageUsageReporter, VideoPurgeService, and BillingSyncRegistrar"
```

---

## Task 8: User Auto-Provisioning (CognitoJwtFilter)

**Files:**
- Modify: `.../auth/service/UserService.kt`
- Modify: `.../config/CognitoJwtFilter.kt`

- [ ] **Step 1: Add provisionFromCognito to UserService**

In `src/main/kotlin/org/sightech/memoryvault/auth/service/UserService.kt`, add:

```kotlin
import org.sightech.memoryvault.auth.entity.UserRole
import org.sightech.memoryvault.billing.entity.Tier

fun provisionFromCognito(email: String): User {
    val user = User(
        email = email,
        displayName = email.substringBefore('@'),
        role = UserRole.VIEWER,
        tier = Tier.FREE
    )
    log.info("Auto-provisioning Cognito user email={}", email)
    return userRepository.save(user)
}

fun findAll(): List<User> = userRepository.findAllActive()

fun setActive(id: UUID, active: Boolean): Boolean {
    val user = userRepository.findByIdAndDeletedAtIsNull(id) ?: return false
    if (active) {
        user.deletedAt = null
    } else {
        user.deletedAt = java.time.Instant.now()
    }
    user.updatedAt = java.time.Instant.now()
    userRepository.save(user)
    log.info("User active status changed id={} active={}", id, active)
    return true
}
```

- [ ] **Step 2: Update CognitoJwtFilter to auto-provision**

In `src/main/kotlin/org/sightech/memoryvault/config/CognitoJwtFilter.kt`, inject `UserService` and replace the "user not found" warning with auto-provisioning:

Change constructor from:
```kotlin
class CognitoJwtFilter(
    private val tokenValidator: CognitoTokenValidator,
    private val userRepository: UserRepository
) : AppAuthenticationFilter() {
```

to:
```kotlin
class CognitoJwtFilter(
    private val tokenValidator: CognitoTokenValidator,
    private val userRepository: UserRepository,
    private val userService: org.sightech.memoryvault.auth.service.UserService
) : AppAuthenticationFilter() {
```

Change the user-not-found branch from:
```kotlin
} else {
    log.warn("Cognito user {} not found in database", claims.email)
}
```

to:
```kotlin
} else {
    val provisioned = userService.provisionFromCognito(claims.email)
    val authorities = listOf(SimpleGrantedAuthority("ROLE_VIEWER"))
    val auth = UsernamePasswordAuthenticationToken(provisioned.id.toString(), null, authorities)
    SecurityContextHolder.getContext().authentication = auth
}
```

- [ ] **Step 3: Run all tests**

Run: `./gradlew test`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/auth/service/UserService.kt src/main/kotlin/org/sightech/memoryvault/config/CognitoJwtFilter.kt
git commit -m "feat(billing): auto-provision users from Cognito JWT on first login"
```

---

## Task 9: GraphQL Schema + BillingResolver + AdminUserResolver

**Files:**
- Create: `src/main/resources/graphql/billing.graphqls`
- Create: `.../graphql/BillingResolver.kt`
- Modify: `src/main/resources/graphql/schema.graphqls`

- [ ] **Step 1: Create billing.graphqls**

Create `src/main/resources/graphql/billing.graphqls`:

```graphql
type UserProfile {
    id: UUID!
    email: String!
    tier: String!
    role: String!
    displayName: String
}

type SubscriptionInfo {
    tier: String!
    status: String!
    billingInterval: String
    gracePeriodEndsAt: Instant
    lastUsageReportedAt: Instant
}

type AdminUserView {
    id: UUID!
    email: String!
    tier: String!
    role: String!
    subscriptionStatus: String
    createdAt: Instant!
    deletedAt: Instant
}
```

- [ ] **Step 2: Add billing queries and mutations to schema.graphqls**

In `src/main/resources/graphql/schema.graphqls`, add to the `Query` type:

```graphql
    # Billing
    me: UserProfile!
    subscription: SubscriptionInfo
    adminUsers: [AdminUserView!]!
```

Add to the `Mutation` type:

```graphql
    # Admin billing
    adminSetUserTier(userId: UUID!, tier: String!): Boolean!
    adminSetUserActive(userId: UUID!, active: Boolean!): Boolean!
    adminRefundUser(userId: UUID!): Boolean!
```

- [ ] **Step 3: Create BillingResolver**

Create `src/main/kotlin/org/sightech/memoryvault/graphql/BillingResolver.kt`:

```kotlin
package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.auth.service.UserService
import org.sightech.memoryvault.billing.entity.Tier
import org.sightech.memoryvault.billing.service.BillingService
import org.slf4j.LoggerFactory
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class BillingResolver(
    private val billingService: BillingService,
    private val userService: UserService,
    private val userRepository: UserRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @QueryMapping
    fun me(): Map<String, Any?> {
        val userId = CurrentUser.userId()
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: return mapOf("id" to userId.toString(), "email" to "", "tier" to "FREE", "role" to "VIEWER", "displayName" to null)
        return mapOf(
            "id" to user.id,
            "email" to user.email,
            "tier" to user.tier.name,
            "role" to user.role.name,
            "displayName" to user.displayName
        )
    }

    @QueryMapping
    fun subscription(): Any? {
        val userId = CurrentUser.userId()
        return billingService.getSubscriptionInfo(userId)
    }

    @QueryMapping
    fun adminUsers(): List<Map<String, Any?>> {
        val users = userService.findAll()
        return users.map { user ->
            val sub = billingService.getSubscriptionInfo(user.id)
            mapOf(
                "id" to user.id,
                "email" to user.email,
                "tier" to user.tier.name,
                "role" to user.role.name,
                "subscriptionStatus" to sub?.status,
                "createdAt" to user.createdAt,
                "deletedAt" to user.deletedAt
            )
        }
    }

    @MutationMapping
    fun adminSetUserTier(@Argument userId: UUID, @Argument tier: String): Boolean {
        log.info("Admin setting tier userId={} tier={}", userId, tier)
        return billingService.setUserTier(userId, Tier.valueOf(tier.uppercase()))
    }

    @MutationMapping
    fun adminSetUserActive(@Argument userId: UUID, @Argument active: Boolean): Boolean {
        log.info("Admin setting active userId={} active={}", userId, active)
        return userService.setActive(userId, active)
    }

    @MutationMapping
    fun adminRefundUser(@Argument userId: UUID): Boolean {
        log.info("Admin refund requested for userId={}", userId)
        // Stripe refund via Customer Portal is preferred. Direct refund requires Stripe charge ID.
        // This is a stub — extend when needed.
        return false
    }
}
```

- [ ] **Step 4: Run all tests**

Run: `./gradlew test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/graphql/billing.graphqls src/main/resources/graphql/schema.graphqls src/main/kotlin/org/sightech/memoryvault/graphql/BillingResolver.kt
git commit -m "feat(billing): add billing GraphQL schema, BillingResolver, and admin user mutations"
```

---

## Task 10: REST Additions (GET /api/me + Video Download + Admin Users)

**Files:**
- Create: `.../billing/controller/VideoDownloadController.kt`
- Modify: `.../auth/controller/AuthController.kt` (add GET /api/me)

- [ ] **Step 1: Add GET /api/me to AuthController**

In `src/main/kotlin/org/sightech/memoryvault/auth/controller/AuthController.kt`, add:

```kotlin
@GetMapping("/me")
fun me(): ResponseEntity<Map<String, Any?>> {
    val userId = CurrentUser.userId()
    val user = userService.findById(userId) ?: return ResponseEntity.notFound().build()
    return ResponseEntity.ok(mapOf(
        "id" to user.id,
        "email" to user.email,
        "tier" to user.tier.name,
        "role" to user.role.name,
        "displayName" to user.displayName
    ))
}
```

Adjust the `@RequestMapping` to cover `/api/me` or add `@GetMapping` under `/api/auth/me` — choose based on existing controller structure. If `AuthController` is `@RequestMapping("/api/auth")`, add the endpoint there and the Angular `TierService` calls `/api/auth/me`.

- [ ] **Step 2: Create VideoDownloadController**

Create `src/main/kotlin/org/sightech/memoryvault/billing/controller/VideoDownloadController.kt`:

```kotlin
package org.sightech.memoryvault.billing.controller

import org.sightech.memoryvault.auth.CurrentUser
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
class VideoDownloadController(
    private val videoRepository: VideoRepository,
    private val storageService: StorageService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/{videoId}/download")
    fun download(@PathVariable videoId: UUID): ResponseEntity<InputStreamResource> {
        val userId = CurrentUser.userId()
        val video = videoRepository.findById(videoId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        if (video.youtubeList.userId != userId) {
            log.warn("Unauthorized video download attempt userId={} videoId={}", userId, videoId)
            return ResponseEntity.status(403).build()
        }

        val filePath = video.filePath ?: return ResponseEntity.notFound().build()
        val stream = storageService.retrieve(filePath)
        val filename = "${video.title?.replace(Regex("[^a-zA-Z0-9._-]"), "_") ?: videoId}.mp4"

        log.info("Video download started userId={} videoId={}", userId, videoId)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(InputStreamResource(stream))
    }
}
```

- [ ] **Step 3: Run all tests**

Run: `./gradlew test`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/auth/controller/ src/main/kotlin/org/sightech/memoryvault/billing/controller/VideoDownloadController.kt
git commit -m "feat(billing): add GET /api/me and video download endpoint"
```

---

## Task 11: MCP BillingTools

**Files:**
- Create: `.../mcp/BillingTools.kt`

- [ ] **Step 1: Create BillingTools**

Create `src/main/kotlin/org/sightech/memoryvault/mcp/BillingTools.kt`:

```kotlin
package org.sightech.memoryvault.mcp

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.auth.repository.UserRepository
import org.sightech.memoryvault.billing.entity.Tier
import org.sightech.memoryvault.billing.service.BillingService
import org.sightech.memoryvault.auth.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class BillingTools(
    private val billingService: BillingService,
    private val userService: UserService,
    private val userRepository: UserRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Tool(description = "Get the current user's subscription tier and status. Use when the user asks about their plan, billing status, or what features they have access to.")
    fun getMySubscription(): String {
        val userId = CurrentUser.userId()
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: return "User not found."
        val sub = billingService.getSubscriptionInfo(userId)
        return if (sub != null) {
            "Tier: ${sub.tier}\nStatus: ${sub.status}\nBilling interval: ${sub.billingInterval}\n" +
                (sub.gracePeriodEndsAt?.let { "Grace period ends: $it\n" } ?: "")
        } else {
            "Tier: ${user.tier.name}\nNo active subscription (Free tier or OWNER)."
        }
    }

    @Tool(description = "List all users with their tier and subscription status. Restricted to OWNER role. Use when asked to manage users or check who is on which plan.")
    fun adminListUsers(): String {
        val users = userService.findAll()
        if (users.isEmpty()) return "No users found."
        return users.joinToString("\n") { user ->
            val sub = billingService.getSubscriptionInfo(user.id)
            "- ${user.email} | ${user.tier} | ${sub?.status ?: "no-subscription"} | role=${user.role}"
        }
    }

    @Tool(description = "Change a user's subscription tier. OWNER only. Use when manually overriding a user's access tier.")
    fun adminSetUserTier(
        @ToolParam(description = "UUID of the user to update") userId: String,
        @ToolParam(description = "New tier: FREE, PREMIUM, or BACKUP") tier: String
    ): String {
        val id = UUID.fromString(userId)
        val result = billingService.setUserTier(id, Tier.valueOf(tier.uppercase()))
        return if (result) "Tier updated to $tier for user $userId." else "User not found: $userId."
    }
}
```

- [ ] **Step 2: Run all tests**

Run: `./gradlew test`

Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/mcp/BillingTools.kt
git commit -m "feat(billing): add MCP BillingTools — getMySubscription, adminListUsers, adminSetUserTier"
```

---

## Task 12: Terraform

**Files:**
- Modify: `terraform/cognito.tf`
- Modify: `terraform/variables.tf`
- Modify: `terraform/ec2.tf` (or `templates/user_data.sh`)

> **Sequence note:** Cognito changes require `terraform apply` before the code is deployed. Commit terraform changes, apply, then push the app code. See CLAUDE.md infra-vs-code change rules.

- [ ] **Step 1: Open Cognito signup**

In `terraform/cognito.tf`, change:

```hcl
allow_admin_create_user_only = true
```

to:

```hcl
allow_admin_create_user_only = false
```

- [ ] **Step 2: Add Stripe variables to variables.tf**

In `terraform/variables.tf`, add:

```hcl
variable "stripe_secret_key" {
  description = "Stripe secret API key"
  type        = string
  sensitive   = true
  default     = ""
}

variable "stripe_webhook_secret" {
  description = "Stripe webhook signing secret"
  type        = string
  sensitive   = true
  default     = ""
}

variable "stripe_premium_monthly_price_id" {
  description = "Stripe price ID for Premium monthly"
  type        = string
  default     = ""
}

variable "stripe_premium_annual_price_id" {
  description = "Stripe price ID for Premium annual"
  type        = string
  default     = ""
}

variable "stripe_backup_monthly_price_id" {
  description = "Stripe price ID for Backup monthly"
  type        = string
  default     = ""
}

variable "stripe_backup_annual_price_id" {
  description = "Stripe price ID for Backup annual"
  type        = string
  default     = ""
}
```

- [ ] **Step 3: Pass Stripe vars to EC2 user_data**

In `terraform/templates/user_data.sh` (or wherever the env file is written), add:

```bash
STRIPE_SECRET_KEY="${stripe_secret_key}"
STRIPE_WEBHOOK_SECRET="${stripe_webhook_secret}"
STRIPE_PREMIUM_MONTHLY_PRICE_ID="${stripe_premium_monthly_price_id}"
STRIPE_PREMIUM_ANNUAL_PRICE_ID="${stripe_premium_annual_price_id}"
STRIPE_BACKUP_MONTHLY_PRICE_ID="${stripe_backup_monthly_price_id}"
STRIPE_BACKUP_ANNUAL_PRICE_ID="${stripe_backup_annual_price_id}"
```

In `terraform/ec2.tf`, add the corresponding `templatefile` vars:

```hcl
stripe_secret_key                = var.stripe_secret_key
stripe_webhook_secret            = var.stripe_webhook_secret
stripe_premium_monthly_price_id  = var.stripe_premium_monthly_price_id
stripe_premium_annual_price_id   = var.stripe_premium_annual_price_id
stripe_backup_monthly_price_id   = var.stripe_backup_monthly_price_id
stripe_backup_annual_price_id    = var.stripe_backup_annual_price_id
```

- [ ] **Step 4: Format and validate**

```bash
cd terraform && terraform fmt && terraform validate
```

Expected: `Success! The configuration is valid.`

- [ ] **Step 5: Commit terraform changes**

```bash
git add terraform/cognito.tf terraform/variables.tf terraform/ec2.tf terraform/templates/user_data.sh
git commit -m "feat(billing): terraform — open Cognito signup, add Stripe env vars"
```

- [ ] **Step 6: terraform plan → apply (AWS state change, separate from code push)**

```bash
cd terraform && terraform plan
# Review: Cognito user pool will be modified (not replaced). EC2 may or may not replace — check plan.
terraform apply
```

---

## Task 13: Angular TierService + Signup Page

**Files:**
- Create: `client/src/app/core/tier.service.ts`
- Create: `client/src/app/auth/signup/signup.ts`
- Create: `client/src/app/auth/signup/index.ts`
- Modify: `client/src/app/app.routes.ts`
- Modify: `client/src/app/auth/login/login.ts`

- [ ] **Step 1: Create TierService**

Create `client/src/app/core/tier.service.ts`:

```typescript
import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface UserProfile {
  id: string;
  email: string;
  tier: 'FREE' | 'PREMIUM' | 'BACKUP';
  role: string;
  displayName: string | null;
}

@Injectable({ providedIn: 'root' })
export class TierService {
  private http = inject(HttpClient);

  readonly profile = signal<UserProfile | null>(null);

  load(): void {
    this.http.get<UserProfile>('/api/auth/me').subscribe({
      next: (p) => this.profile.set(p),
      error: () => this.profile.set(null),
    });
  }

  get tier() {
    return this.profile()?.tier ?? 'FREE';
  }

  get isBackup() {
    return this.tier === 'BACKUP';
  }

  get isPremiumOrHigher() {
    return this.tier === 'PREMIUM' || this.tier === 'BACKUP';
  }

  get isOwner() {
    return this.profile()?.role === 'OWNER';
  }

  clear(): void {
    this.profile.set(null);
  }
}
```

- [ ] **Step 2: Load TierService on app init**

In `client/src/app/app.config.ts`, inject `TierService` via `provideAppInitializer` (or call `load()` from the root `AppComponent`/layout on auth state change). The simplest approach: call `tierService.load()` in `AppLayoutComponent.ngOnInit()` so it refreshes on each authenticated navigation, and `tierService.clear()` on logout.

In `client/src/app/auth/auth.service.ts`, call `tierService.clear()` in `logout()`.

- [ ] **Step 3: Create signup component**

Create `client/src/app/auth/signup/signup.ts`:

```typescript
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CognitoAuthService } from '../cognito-auth.service';
import { ConfigService } from '../../shared/config';
import { CognitoUser, CognitoUserPool, CognitoUserAttribute } from 'amazon-cognito-identity-js';

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [FormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatButtonModule, MatProgressSpinnerModule],
  template: `
    <div class="signup-container">
      <div class="signup-card">
        <h1>Create account</h1>
        @if (success()) {
          <p class="success-msg">Account created! Check your email to verify, then <a routerLink="/login">sign in</a>.</p>
        } @else {
          <form (ngSubmit)="submit()" #form="ngForm">
            <mat-form-field appearance="outline">
              <mat-label>Email</mat-label>
              <input matInput type="email" [(ngModel)]="email" name="email" required>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Password</mat-label>
              <input matInput type="password" [(ngModel)]="password" name="password" required minlength="8">
            </mat-form-field>
            @if (error()) {
              <p class="error-msg">{{ error() }}</p>
            }
            <button mat-flat-button color="primary" type="submit" [disabled]="loading()">
              @if (loading()) { <mat-spinner diameter="18"></mat-spinner> } @else { Create account }
            </button>
          </form>
          <p class="login-link">Already have an account? <a routerLink="/login">Sign in</a></p>
        }
      </div>
    </div>
  `,
  styles: [`
    .signup-container { display: flex; justify-content: center; align-items: center; height: 100vh; }
    .signup-card { width: 360px; padding: 32px; border-radius: 8px; box-shadow: 0 2px 12px rgba(0,0,0,.1); }
    h1 { margin: 0 0 24px; font-size: 1.5rem; }
    mat-form-field { width: 100%; margin-bottom: 8px; }
    button { width: 100%; margin-top: 8px; }
    .error-msg { color: #c62828; font-size: 0.875rem; }
    .success-msg { color: #2e7d32; }
    .login-link { margin-top: 16px; font-size: 0.875rem; text-align: center; }
  `],
})
export class SignupComponent {
  private config = inject(ConfigService);

  email = '';
  password = '';
  loading = signal(false);
  error = signal<string | null>(null);
  success = signal(false);

  private get userPool(): CognitoUserPool {
    return new CognitoUserPool({
      UserPoolId: this.config.cognito.userPoolId,
      ClientId: this.config.cognito.clientId,
    });
  }

  submit(): void {
    this.loading.set(true);
    this.error.set(null);

    const attrs = [
      new CognitoUserAttribute({ Name: 'email', Value: this.email }),
    ];

    this.userPool.signUp(this.email, this.password, attrs, [], (err) => {
      this.loading.set(false);
      if (err) {
        this.error.set(err.message || 'Signup failed');
      } else {
        this.success.set(true);
      }
    });
  }
}
```

- [ ] **Step 4: Create barrel export**

Create `client/src/app/auth/signup/index.ts`:

```typescript
export { SignupComponent } from './signup';
```

- [ ] **Step 5: Add /signup route**

In `client/src/app/app.routes.ts`, add before the authenticated root route:

```typescript
{
  path: 'signup',
  loadComponent: () => import('./auth/signup/signup').then(m => m.SignupComponent),
},
```

- [ ] **Step 6: Add link from login page**

In `client/src/app/auth/login/login.ts`, add below the login button:

```html
<p class="signup-link">No account? <a routerLink="/signup">Create one</a></p>
```

- [ ] **Step 7: Run frontend tests and build**

```bash
cd client && npm run test && npx ng build 2>&1 | tail -5
```

Expected: all pass, build succeeds.

- [ ] **Step 8: Commit**

```bash
cd client && git add src/app/core/tier.service.ts src/app/auth/signup/ src/app/app.routes.ts src/app/auth/login/
git commit -m "feat(billing): add TierService, signup page, and /signup route"
```

---

## Task 14: Angular Billing Pages

**Files:**
- Create: `client/src/app/billing/billing.ts`
- Create: `client/src/app/billing/billing-success.ts`
- Create: `client/src/app/billing/billing-cancelled.ts`
- Create: `client/src/app/billing/index.ts`
- Modify: `client/src/app/app.routes.ts`

- [ ] **Step 1: Create billing page**

Create `client/src/app/billing/billing.ts`:

```typescript
import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TierService } from '../core/tier.service';

interface SubscriptionInfo {
  tier: string;
  status: string;
  billingInterval: string | null;
  gracePeriodEndsAt: string | null;
}

@Component({
  selector: 'app-billing',
  standalone: true,
  imports: [MatButtonModule, MatCardModule, MatProgressSpinnerModule],
  template: `
    <div class="billing-page">
      <h2>Billing & Subscription</h2>

      <mat-card class="tier-card">
        <mat-card-header>
          <mat-card-title>Current Plan: {{ tierService.tier }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          @if (subInfo()) {
            <p>Status: {{ subInfo()!.status }}</p>
            @if (subInfo()!.gracePeriodEndsAt) {
              <p class="warn">Grace period ends: {{ subInfo()!.gracePeriodEndsAt | date }}</p>
            }
            <button mat-stroked-button (click)="openPortal()" [disabled]="loading()">
              Manage subscription (Stripe)
            </button>
          }
          @if (!subInfo() && tierService.tier === 'FREE') {
            <p>You're on the Free plan.</p>
          }
        </mat-card-content>
      </mat-card>

      <div class="plan-grid">
        @if (tierService.tier === 'FREE') {
          <button mat-flat-button color="primary" (click)="checkout('PREMIUM', 'MONTHLY')" [disabled]="loading()">
            Upgrade to Premium — $3/mo
          </button>
          <button mat-flat-button color="accent" (click)="checkout('BACKUP', 'MONTHLY')" [disabled]="loading()">
            Upgrade to Backup — $3/mo base + storage
          </button>
        }
        @if (tierService.tier === 'PREMIUM') {
          <button mat-flat-button color="accent" (click)="checkout('BACKUP', 'MONTHLY')" [disabled]="loading()">
            Add YouTube Backup — $3/mo base + storage
          </button>
        }
      </div>

      @if (error()) {
        <p class="error">{{ error() }}</p>
      }
    </div>
  `,
  styles: [`
    .billing-page { padding: 24px; max-width: 600px; margin: 0 auto; }
    .tier-card { margin-bottom: 24px; }
    .plan-grid { display: flex; flex-direction: column; gap: 12px; }
    .warn { color: #f9a825; }
    .error { color: #c62828; }
  `],
})
export class BillingComponent implements OnInit {
  private http = inject(HttpClient);
  readonly tierService = inject(TierService);

  subInfo = signal<SubscriptionInfo | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.http.get<SubscriptionInfo>('/api/billing/subscription').subscribe({
      next: (info) => this.subInfo.set(info),
      error: () => {},
    });
  }

  checkout(tier: string, interval: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.post<{ url: string }>('/api/billing/checkout', { tier, interval }).subscribe({
      next: (res) => window.location.href = res.url,
      error: (e) => { this.loading.set(false); this.error.set(e.error?.message ?? 'Checkout failed'); },
    });
  }

  openPortal(): void {
    this.loading.set(true);
    this.http.post<{ url: string }>('/api/billing/portal', {}).subscribe({
      next: (res) => window.location.href = res.url,
      error: () => this.loading.set(false),
    });
  }
}
```

- [ ] **Step 2: Create billing-success and billing-cancelled pages**

Create `client/src/app/billing/billing-success.ts`:

```typescript
import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TierService } from '../core/tier.service';

@Component({
  selector: 'app-billing-success',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="result">
      <h2>Subscription activated!</h2>
      <p>Your plan has been updated. Enjoy your new features.</p>
      <a routerLink="/">Go to reader</a>
    </div>
  `,
  styles: [`.result { padding: 48px; text-align: center; }`],
})
export class BillingSuccessComponent implements OnInit {
  private tier = inject(TierService);
  ngOnInit() { this.tier.load(); }
}
```

Create `client/src/app/billing/billing-cancelled.ts`:

```typescript
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-billing-cancelled',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="result">
      <h2>Checkout cancelled</h2>
      <p>No changes were made to your plan.</p>
      <a routerLink="/billing">Back to billing</a>
    </div>
  `,
  styles: [`.result { padding: 48px; text-align: center; }`],
})
export class BillingCancelledComponent {}
```

- [ ] **Step 3: Create barrel export**

Create `client/src/app/billing/index.ts`:

```typescript
export { BillingComponent } from './billing';
export { BillingSuccessComponent } from './billing-success';
export { BillingCancelledComponent } from './billing-cancelled';
```

- [ ] **Step 4: Add billing routes**

In `client/src/app/app.routes.ts`, add inside the authenticated children array:

```typescript
{
  path: 'billing',
  loadComponent: () => import('./billing/billing').then(m => m.BillingComponent)
},
{
  path: 'billing/success',
  loadComponent: () => import('./billing/billing-success').then(m => m.BillingSuccessComponent)
},
{
  path: 'billing/cancelled',
  loadComponent: () => import('./billing/billing-cancelled').then(m => m.BillingCancelledComponent)
},
```

- [ ] **Step 5: Build and verify**

```bash
cd client && npx ng build 2>&1 | tail -5
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
cd client && git add src/app/billing/ src/app/app.routes.ts
git commit -m "feat(billing): add billing, billing-success, and billing-cancelled pages"
```

---

## Task 15: Angular Top-Bar User Menu + Page Gating

**Files:**
- Modify: `client/src/app/shared/layout/app-layout.ts`
- Modify: `client/src/app/youtube/youtube.ts`
- Modify: `client/src/app/reader/reader.ts`

- [ ] **Step 1: Add user menu to app-layout**

In `client/src/app/shared/layout/app-layout.ts`, inject `TierService` and `AuthService`. Add a user menu icon button to the toolbar that opens a dropdown with "Billing" and "Sign out" items.

Add imports:
```typescript
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink } from '@angular/router';
import { TierService } from '../../core/tier.service';
import { AuthService } from '../../auth/auth.service';
import { Router } from '@angular/router';
```

Add to the toolbar template (typically after nav links):
```html
<span class="spacer"></span>
<button mat-icon-button [matMenuTriggerFor]="userMenu">
  <mat-icon>account_circle</mat-icon>
</button>
<mat-menu #userMenu="matMenu">
  <div class="user-email" mat-menu-item disabled>{{ tierService.profile()?.email }}</div>
  <div mat-menu-item disabled class="tier-badge">{{ tierService.tier }}</div>
  <mat-divider></mat-divider>
  <button mat-menu-item routerLink="/billing">
    <mat-icon>payments</mat-icon> Billing
  </button>
  <button mat-menu-item (click)="logout()">
    <mat-icon>logout</mat-icon> Sign out
  </button>
</mat-menu>
```

Add class members:
```typescript
readonly tierService = inject(TierService);
private auth = inject(AuthService);
private router = inject(Router);

logout(): void {
  this.auth.logout();
  this.router.navigate(['/login']);
}
```

Call `tierService.load()` in `ngOnInit()`.

- [ ] **Step 2: Gate YouTube page for non-BACKUP users**

In `client/src/app/youtube/youtube.ts`, inject `TierService`. At the top of the template (before the existing content), add:

```html
@if (!tierService.isBackup && !tierService.isOwner) {
  <div class="tier-gate">
    <mat-icon>lock</mat-icon>
    <h3>YouTube archival requires the Backup plan</h3>
    <p>$3/mo base + metered per-GB storage. Includes Internet Archive backup.</p>
    <button mat-flat-button color="primary" routerLink="/billing">Upgrade to Backup</button>
  </div>
} @else {
  <!-- existing youtube content -->
}
```

Also: if `tierService.tier === 'FREE'` and the subscription is in `GRACE_PERIOD`, show a banner above the video list:

```html
@if (gracePeriodActive()) {
  <div class="grace-banner">
    Your Backup subscription ended. Videos are available for download for {{ graceDaysRemaining() }} more days.
  </div>
}
```

Compute `gracePeriodActive` from the `SubscriptionInfo` signal loaded from `GET /api/billing/subscription`.

- [ ] **Step 3: Gate feed "Add" for FREE users at limit**

In `client/src/app/reader/reader.ts` (or the feed management component that shows the add-feed button), inject `TierService`. Check if `tierService.tier === 'FREE'` and feeds count `>= 100`. If so, show an upgrade prompt instead of the add dialog:

```typescript
onAddFeed(): void {
  if (this.tierService.tier === 'FREE' && this.store.feeds().length >= 100) {
    this.router.navigate(['/billing']);
    return;
  }
  // existing add logic
}
```

- [ ] **Step 4: Build and verify**

```bash
cd client && npx ng build 2>&1 | tail -5
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
cd client && git add src/app/shared/layout/ src/app/youtube/youtube.ts src/app/reader/
git commit -m "feat(billing): add user menu, YouTube BACKUP gate, and free-tier feed limit prompt"
```

---

## Task 16: Angular Admin Users Tab

**Files:**
- Modify: `client/src/app/admin/admin.graphql`
- Modify: `client/src/app/admin/admin.store.ts`
- Modify: `client/src/app/admin/admin.ts`
- Create: `client/src/app/admin/users-panel/users-panel.ts`
- Create: `client/src/app/admin/users-panel/index.ts`

- [ ] **Step 1: Add GraphQL operations to admin.graphql**

Append to `client/src/app/admin/admin.graphql`:

```graphql
query GetMe {
  me {
    id
    email
    tier
    role
    displayName
  }
}

query GetAdminUsers {
  adminUsers {
    id
    email
    tier
    role
    subscriptionStatus
    createdAt
    deletedAt
  }
}

mutation AdminSetUserTier($userId: UUID!, $tier: String!) {
  adminSetUserTier(userId: $userId, tier: $tier)
}

mutation AdminSetUserActive($userId: UUID!, $active: Boolean!) {
  adminSetUserActive(userId: $userId, active: $active)
}
```

- [ ] **Step 2: Regenerate GraphQL types**

```bash
cd client && npx graphql-codegen
```

- [ ] **Step 3: Add admin-users state to admin store**

In `client/src/app/admin/admin.store.ts`, add:
- `adminUsers: AdminUserView[] | null` to state + initial state
- `loadAdminUsers` rxMethod (queries `GetAdminUsersDocument`)
- `setUserTier(userId, tier)` method (mutates `AdminSetUserTierDocument`, then reloads)
- `setUserActive(userId, active)` method (mutates `AdminSetUserActiveDocument`, then reloads)

- [ ] **Step 4: Create users-panel component**

Create `client/src/app/admin/users-panel/users-panel.ts` as a table with columns: email, tier (editable via mat-select), role, subscriptionStatus, createdAt, actions (activate/deactivate).

- [ ] **Step 5: Add Users tab to admin.ts**

In `client/src/app/admin/admin.ts`, add a 5th tab:
```html
<mat-tab label="Users">
  <app-users-panel
    [users]="store.adminUsers()"
    (onSetTier)="store.setUserTier($event.userId, $event.tier)"
    (onSetActive)="store.setUserActive($event.userId, $event.active)"
  />
</mat-tab>
```

Load admin users when switching to the Users tab.

- [ ] **Step 6: Build and verify**

```bash
cd client && npm run test && npx ng build 2>&1 | tail -5
```

Expected: PASS

- [ ] **Step 7: Commit**

```bash
cd client && git add src/app/admin/
git commit -m "feat(billing): add Admin Users tab with tier management and activate/deactivate"
```

---

## Task 17: Smoke Tests + Docs Update

**Files:**
- Modify: `scripts/smoke-test.sh`
- Modify: `docs/plans/2026-03-05-tooling-first-design.md`

- [ ] **Step 1: Add billing smoke tests**

In `scripts/smoke-test.sh`, in the authenticated section, add:

```bash
  # GET /api/auth/me
  ME_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    "$BASE_URL/api/auth/me" \
    -H "Authorization: Bearer $TOKEN")
  if [ "$ME_STATUS" -eq 200 ]; then
    echo "  PASS  GET /api/auth/me ($ME_STATUS)"
    PASS=$((PASS + 1))
  else
    echo "  FAIL  GET /api/auth/me (expected 200, got $ME_STATUS)"
    FAIL=$((FAIL + 1))
  fi

  # GET /api/billing/subscription
  SUB_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    "$BASE_URL/api/billing/subscription" \
    -H "Authorization: Bearer $TOKEN")
  if [ "$SUB_STATUS" -eq 200 ] || [ "$SUB_STATUS" -eq 204 ]; then
    echo "  PASS  GET /api/billing/subscription ($SUB_STATUS)"
    PASS=$((PASS + 1))
  else
    echo "  FAIL  GET /api/billing/subscription (expected 200/204, got $SUB_STATUS)"
    FAIL=$((FAIL + 1))
  fi

  # Stripe webhook rejects without signature
  WEBHOOK_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/api/stripe/webhook" \
    -H "Content-Type: application/json" \
    -d '{}')
  if [ "$WEBHOOK_STATUS" -eq 400 ]; then
    echo "  PASS  POST /api/stripe/webhook rejects bad signature ($WEBHOOK_STATUS)"
    PASS=$((PASS + 1))
  else
    echo "  FAIL  POST /api/stripe/webhook (expected 400, got $WEBHOOK_STATUS)"
    FAIL=$((FAIL + 1))
  fi

  # GraphQL me query
  ME_GQL=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/graphql" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"query":"{ me { id email tier role } }"}')
  if [ "$ME_GQL" -eq 200 ]; then
    echo "  PASS  GraphQL me query ($ME_GQL)"
    PASS=$((PASS + 1))
  else
    echo "  FAIL  GraphQL me query (expected 200, got $ME_GQL)"
    FAIL=$((FAIL + 1))
  fi
```

- [ ] **Step 2: Mark Phase 11 underway in master roadmap**

In `docs/plans/2026-03-05-tooling-first-design.md`, update the Phase 11 entry to note implementation is in progress. Add a reference to this plan file.

- [ ] **Step 3: Run full test suite**

```bash
./gradlew test && cd client && npm run test
```

Expected: all pass.

- [ ] **Step 4: Commit**

```bash
git add scripts/smoke-test.sh docs/plans/
git commit -m "feat(billing): smoke tests for billing endpoints, update roadmap"
```

---

## Summary Table

| Task | Description | Key Files |
|------|-------------|-----------|
| 1 | Gradle deps + V10 migration + enums + config | `build.gradle.kts`, `V10__billing.sql`, `BillingEnums.kt` |
| 2 | User entity update + Subscription entity + repos | `User.kt`, `SubscriptionEntity.kt`, `SubscriptionRepository.kt` |
| 3 | StripeService (Stripe API wrapper) | `StripeService.kt` |
| 4 | BillingService (tier transitions, lifecycle) | `BillingService.kt` |
| 5 | @TierGate AOP + wire into services | `TierGate.kt`, `TierEnforcementAspect.kt`, `YoutubeListService.kt`, `FeedService.kt` |
| 6 | BillingController + StripeWebhookController + SecurityConfig | `BillingController.kt`, `StripeWebhookController.kt`, `SecurityConfig.kt` |
| 7 | StorageUsageReporter + VideoPurgeService + BillingSyncRegistrar | `StorageUsageReporter.kt`, `VideoPurgeService.kt`, `BillingSyncRegistrar.kt` |
| 8 | User auto-provisioning from Cognito JWT | `CognitoJwtFilter.kt`, `UserService.kt` |
| 9 | GraphQL schema + BillingResolver | `billing.graphqls`, `schema.graphqls`, `BillingResolver.kt` |
| 10 | GET /api/me + video download endpoint | `AuthController.kt`, `VideoDownloadController.kt` |
| 11 | MCP BillingTools | `BillingTools.kt` |
| 12 | Terraform: Cognito signup + Stripe vars | `cognito.tf`, `variables.tf`, `ec2.tf`, `user_data.sh` |
| 13 | Angular TierService + signup page | `tier.service.ts`, `signup.ts`, `app.routes.ts` |
| 14 | Angular billing pages | `billing.ts`, `billing-success.ts`, `billing-cancelled.ts` |
| 15 | User menu + YouTube gate + feed limit prompt | `app-layout.ts`, `youtube.ts`, `reader.ts` |
| 16 | Admin Users tab | `users-panel.ts`, `admin.ts`, `admin.store.ts`, `admin.graphql` |
| 17 | Smoke tests + docs | `smoke-test.sh`, master roadmap |
