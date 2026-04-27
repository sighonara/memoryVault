# Phase 11 — Public Self-Signup & Subscription Billing

**Goal**: Open MemoryVault to public self-service signup with a tiered subscription model (Free / Premium / Backup), Stripe billing integration, metered storage billing for YouTube archival, and admin user management.

**Architecture**: Cognito handles signup and email verification. Stripe handles all payment processing via Checkout Sessions (no card forms in our UI). Tier enforcement at the service layer via `@TierGate` annotation. A daily storage usage reporter feeds metered billing to Stripe. A daily purge job cleans up expired video files after a 30-day grace period.

---

## Tier Model & Pricing

| Tier | Price | Includes |
|------|-------|----------|
| **Free** | $0 | Up to 100 feeds, unlimited bookmarks, full-text search, MCP tools. No YouTube archival. |
| **Premium** | $3/mo or $30/yr | Unlimited feeds, everything in Free. No YouTube archival. |
| **Backup** | $3/mo or $30/yr base + metered per-GB-month (S3 cost + 10%) | Everything in Premium, plus YouTube archival + Internet Archive backup. Billed monthly based on actual S3 storage usage. |

- Backup includes Premium — it's one subscription, not an add-on on top of a separate Premium subscription.
- Upgrading from Premium to Backup swaps the Stripe subscription (Stripe handles prorating).
- Downgrading from Backup to Premium triggers a 30-day grace period for videos.
- The existing OWNER user is unaffected — implicit unlimited tier, no restrictions enforced.

### Stripe Products & Prices

Created once in Stripe dashboard or via seed script:

- **Product: "MemoryVault Premium"**
  - Price: $3/month recurring
  - Price: $30/year recurring
- **Product: "MemoryVault Backup"**
  - Price: $3/month base + metered component (per-GB-month)
  - Price: $30/year base + metered component (per-GB-month)

### Feed Limit Enforcement

Free tier users are capped at 100 feeds. `FeedService.addFeed()` checks the user's tier and current feed count, throwing `TierRequiredException` if they're FREE and at the limit. Backend enforcement — the UI also disables "Add Feed" but the backend is the real gate.

### Backup Cancellation & Grace Period

When a Backup subscription is cancelled (or payment fails and Stripe deletes the subscription):

1. User's tier is downgraded to Premium (or Free if Premium also cancelled).
2. `grace_period_ends_at` is set to 30 days from cancellation.
3. **Days 1–30:** No new downloads or archival. Existing videos remain on S3. User can download any of their videos locally via `GET /api/videos/{id}/download`.
4. **Day 31:** `VideoPurgeJob` deletes video files from S3, clears `filePath` on Video records. BackupRecords and Video metadata remain (soft preservation of history).

---

## Signup & User Provisioning Flow

1. **Cognito config change:** `allow_admin_create_user_only = false` in `terraform/cognito.tf`.
2. **Angular signup page:** New `/signup` route with email + password form. Calls Cognito directly via `amazon-cognito-identity-js` (same library already used for login). Shows "check your email" on success.
3. **Email verification:** Cognito sends verification email automatically. User clicks link, Cognito marks email verified.
4. **First login → auto-provision:** User logs in, gets a Cognito JWT. `CognitoJwtFilter` sees a valid JWT but no matching `users` row. Auto-creates a `users` row with `tier = FREE`, `role = VIEWER`, email from JWT claims.
5. **Existing OWNER user** is unaffected — row already exists, keeps OWNER role, implicit unlimited tier.

### Local Dev

The local dev flow (`@Profile("local")`) stays unchanged — seed user, no Cognito. Tier enforcement applies locally for testing, but the seed user gets implicit unlimited tier (OWNER role bypasses tier checks).

---

## Stripe Integration

### Checkout Flow — Upgrade to Premium

1. User clicks "Upgrade" on billing page.
2. `POST /api/billing/checkout` with `{ tier: "PREMIUM", interval: "MONTHLY" }`.
3. Backend creates a Stripe Customer (if not exists), then a Checkout Session for Premium.
4. Returns Stripe Checkout URL → frontend redirects user.
5. User completes payment on Stripe-hosted page.
6. Stripe fires `checkout.session.completed` webhook → backend creates `Subscription` record, updates `users.tier` to `PREMIUM`.
7. User is redirected to `/billing/success`.

### Checkout Flow — Upgrade to Backup

1. Premium user clicks "Add YouTube Backup" on billing page.
2. `POST /api/billing/checkout` with `{ tier: "BACKUP", interval: "MONTHLY" }`.
3. Backend swaps the existing Premium subscription to Backup in Stripe (or creates new if coming from Free). Stripe prorates automatically.
4. Same webhook flow → updates `users.tier` to `BACKUP`.

### Metered Storage Reporting

`StorageUsageReporter` runs daily:

1. For each BACKUP user, calculate S3 storage in GB.
2. Check `last_usage_reported_at` on the subscription record.
3. If more than 1 day since last report, submit usage records for each missed day (Stripe accepts backdated `timestamp` parameter). This handles server downtime — if down for 3 days, the next run submits 3 records per user.
4. Update `last_usage_reported_at` on success.

### Webhook Handler

`POST /api/stripe/webhook` — unauthenticated, verified via `Stripe-Signature` header + webhook secret.

Events handled:

| Event | Action |
|-------|--------|
| `checkout.session.completed` | Create `Subscription` record, update `users.tier` |
| `invoice.paid` | Update subscription status to `ACTIVE` |
| `invoice.payment_failed` | Mark subscription `PAST_DUE`, publish notification |
| `customer.subscription.deleted` | Downgrade tier, set `grace_period_ends_at` if Backup cancelled |

Returns 200 even on processing errors (prevents Stripe retry floods). Errors logged at WARN for manual investigation.

### Stripe Customer Portal

`POST /api/billing/portal` creates a Stripe Customer Portal session for the authenticated user. The portal lets users manage payment methods, view invoices, and cancel subscriptions — no custom UI needed for these.

---

## Data Model

### V10 Migration

**Users table changes:**
```sql
ALTER TABLE users ADD COLUMN tier VARCHAR(20) NOT NULL DEFAULT 'FREE';
ALTER TABLE users ADD COLUMN stripe_customer_id VARCHAR(255);
```

**New `subscriptions` table:**
```sql
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

### Enums

```kotlin
enum class Tier { FREE, PREMIUM, BACKUP }
enum class BillingInterval { MONTHLY, ANNUAL }
enum class SubscriptionStatus { ACTIVE, PAST_DUE, CANCELLED, GRACE_PERIOD }
```

### JobType Additions

`STORAGE_USAGE_REPORT`, `VIDEO_PURGE` added to existing `JobType` enum.

### Existing OWNER User

The V10 migration sets the existing seed user's tier to `FREE` by default. The OWNER role bypasses tier checks entirely, so the tier value is irrelevant for OWNER users.

---

## Backend API

### Billing Endpoints

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `POST /api/billing/checkout` | Authenticated | Creates Stripe Checkout Session. Body: `{ tier: "PREMIUM" \| "BACKUP", interval: "MONTHLY" \| "ANNUAL" }`. Returns `{ url: "https://checkout.stripe.com/..." }`. |
| `POST /api/billing/portal` | Authenticated | Creates Stripe Customer Portal session. Returns `{ url: "..." }`. |
| `GET /api/billing/subscription` | Authenticated | Returns current subscription state: tier, status, next billing date, storage usage (for Backup users). |
| `POST /api/stripe/webhook` | Unauthenticated (Stripe signature) | Handles Stripe webhook events. |

### User Endpoints

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `GET /api/me` | Authenticated | Returns user profile: email, tier, role, display name. |
| `GET /api/videos/{id}/download` | Authenticated + owns video | Streams video file from storage. Available during grace period. |

### Admin Endpoints

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `GET /api/admin/users` | OWNER/ADMIN | List users with tier, status, storage used, signup date. |
| `PUT /api/admin/users/{id}/tier` | OWNER/ADMIN | Manually change a user's tier. |
| `PUT /api/admin/users/{id}/active` | OWNER/ADMIN | Activate/deactivate a user (soft delete). |
| `POST /api/admin/users/{id}/refund` | OWNER | Trigger refund via Stripe API. |

### Tier Enforcement

`@TierGate(Tier.X)` annotation on service methods. A Spring AOP aspect intercepts annotated methods, reads the authenticated user's tier from the `users` table, and throws `TierRequiredException` (maps to 403) if insufficient.

| Service Method | Required Tier |
|---------------|---------------|
| `FeedService.addFeed()` | FREE (but blocked at 100 feeds — custom check, not annotation) |
| `YoutubeListService.addList()` | BACKUP |
| `YoutubeListService.refreshList()` | BACKUP |
| `VideoSyncService` download operations | BACKUP |
| `BackupService` operations (add provider, backfill) | BACKUP |

OWNER role bypasses all tier checks.

### Scheduled Jobs

- **`StorageUsageReporter`** — daily, calculates each Backup user's S3 storage in GB, reports to Stripe with backfill for missed days. Registered via `BillingSyncRegistrar` on `ApplicationReadyEvent`.
- **`VideoPurgeJob`** — daily, finds subscriptions with `grace_period_ends_at` in the past, deletes video files from storage, clears `filePath` on Video records. Logs purged count at INFO.

---

## Angular UI

### New Pages

- **`/signup`** — email + password form calling Cognito. Link from login page. Redirects to login after "check your email" confirmation.
- **`/billing`** — current tier display, upgrade/downgrade buttons, active subscription details (next billing date, storage usage for Backup), link to Stripe Customer Portal for managing payment/cancellation.
- **`/billing/success`** — post-checkout success page, reloads subscription state.
- **`/billing/cancelled`** — post-checkout cancellation page (user cancelled at Stripe, not a subscription cancel).

### Existing Page Changes

- **Top bar** — user menu dropdown (icon/avatar) with "Billing" and "Sign out". Currently only has nav links.
- **YouTube page** — if tier is not BACKUP: locked state with upgrade prompt instead of list/video UI. During grace period: videos visible with "download locally" button, banner showing days remaining.
- **Feed reader** — if FREE and at 100 feeds, "Add Feed" shows upgrade prompt instead of add dialog.
- **Admin page** — new "Users" tab: user list (email, tier, signup date, storage used, subscription status), action buttons (activate/deactivate, change tier, refund).

### Tier Service

`TierService` in Angular exposes the current user's tier as a signal. Populated from `GET /api/me`. Components check it to show/hide/disable features. Refreshed after checkout success.

---

## GraphQL Changes

### New Queries

- `me: UserProfile!` — email, tier, role, display name.
- `subscription: SubscriptionInfo` — nullable, returns current subscription details.
- `adminUsers(limit: Int, offset: Int): [AdminUserView!]!` — paginated user list for admin.

### New Mutations

Checkout and portal sessions use REST (they return redirect URLs, not graph data — see Backend API section). GraphQL mutations are for admin operations only:

- `adminSetUserTier(userId: UUID!, tier: String!): Boolean!`
- `adminSetUserActive(userId: UUID!, active: Boolean!): Boolean!`
- `adminRefundUser(userId: UUID!): Boolean!`

### New Types

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
  billingInterval: String!
  currentPeriodEnd: Instant
  storageUsedBytes: Long
  gracePeriodEndsAt: Instant
}

type AdminUserView {
  id: UUID!
  email: String!
  tier: String!
  role: String!
  subscriptionStatus: String
  storageUsedBytes: Long
  createdAt: Instant!
}
```

---

## MCP Tools

- `getMySubscription()` — returns current tier, subscription status, storage usage.
- `adminListUsers()` — list users with tier/status (OWNER only).
- `adminSetUserTier(userId, tier)` — change a user's tier (OWNER only).

---

## Package Structure

```
src/main/kotlin/org/sightech/memoryvault/
├── billing/
│   ├── entity/         SubscriptionEntity.kt, BillingEnums.kt
│   ├── repository/     SubscriptionRepository.kt
│   ├── service/        BillingService.kt, StripeService.kt, StorageUsageReporter.kt, VideoPurgeService.kt
│   ├── controller/     StripeWebhookController.kt
│   └── tier/           TierGate.kt (annotation), TierEnforcementAspect.kt, TierRequiredException.kt
├── graphql/            BillingResolver.kt, AdminUserResolver.kt
└── mcp/                BillingTools.kt
```

---

## Configuration

### application.properties

```properties
memoryvault.billing.free-feed-limit=100
memoryvault.billing.grace-period-days=30
memoryvault.billing.usage-report-cron=-
memoryvault.billing.video-purge-cron=-
```

### Environment Variables (secrets)

Added to `.env.sample`, `terraform/variables.tf`, `terraform/templates/user_data.sh`:

- `STRIPE_SECRET_KEY` — Stripe API secret key
- `STRIPE_WEBHOOK_SECRET` — Stripe webhook signing secret

### Stripe Product/Price IDs

Stored as env vars or Spring properties (not hardcoded):

- `STRIPE_PREMIUM_MONTHLY_PRICE_ID`
- `STRIPE_PREMIUM_ANNUAL_PRICE_ID`
- `STRIPE_BACKUP_MONTHLY_PRICE_ID`
- `STRIPE_BACKUP_ANNUAL_PRICE_ID`

---

## Cross-Cutting Concerns

### Security
- Stripe webhook verified via `Stripe-Signature` header + webhook secret. Reject if invalid.
- Stripe keys (`STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`) stored as env vars, never logged, never returned in API responses.
- Admin user management endpoints restricted to OWNER/ADMIN role.
- Video download endpoint validates user owns the video.
- `@TierGate` enforcement at service layer — frontend checks are cosmetic, backend is authoritative.

### Threading
- Both new jobs use existing `JobScheduler` pattern (registered on `ApplicationReadyEvent`). No `@Async`.
- Stripe webhook handler is synchronous — processes events inline. If latency becomes an issue, can be moved to async later.

### Data Integrity
- `users.tier` is the single source of truth for tier. Updated atomically by webhook handlers.
- `grace_period_ends_at` set on cancellation, cleared on resubscription.
- `StorageUsageReporter` tracks `last_usage_reported_at` to backfill missed days on server downtime.
- `VideoPurgeJob` only purges files, never deletes Video or BackupRecord rows (preserves history).

### Observability
- All billing mutations log at INFO with user ID and context.
- Stripe webhook events log at INFO (event type, subscription ID).
- Tier check failures log at WARN.
- Secrets never logged.
- `VIDEO_PURGE` and `STORAGE_USAGE_REPORT` jobs tracked in `sync_jobs` table via existing audit trail.

### Error Handling
- Stripe API failures in `StorageUsageReporter` retry on next daily run (backfill mechanism covers gaps).
- Webhook handler returns 200 on all events (even processing errors) to prevent Stripe retry floods. Errors logged at WARN.
- `TierRequiredException` maps to 403 with a clear message indicating required tier.
- Invalid Stripe signatures return 400.

### Terraform Changes
- `cognito.tf`: `allow_admin_create_user_only = false`
- `variables.tf`: `stripe_secret_key`, `stripe_webhook_secret` (sensitive)
- `ec2.tf`: pass new vars to user_data template
- `user_data.sh`: add `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` to env file
- **Note:** Cognito change requires `terraform apply` before deploying the app code.
