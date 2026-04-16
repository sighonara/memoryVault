# Phase 9E — Lambda Scheduling + Async Video Worker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Spring cron scheduling with AWS EventBridge + Lambda for feed/YouTube sync, and replace the `LambdaVideoDownloader` stub with a local `@Async` dispatch so long-running downloads don't block the sync API response.

**Architecture:**

- **Scheduler:** Two Python Lambda functions triggered by EventBridge. Each Lambda does one thing — POST to an internal REST endpoint on EC2 (`/api/internal/sync/{feeds,youtube}`) guarded by a shared `X-Internal-Key` header. EventBridge owns the cron expressions, so schedules change via Terraform rather than SSH-ing into the EC2.
- **Video worker:** Downloads run on the EC2 (yt-dlp is already installed there, and videos are too large for Lambda's 10GB/15min limits). Dispatch is via Spring `@Async` on a bounded thread pool — no SSM round-trip, no internal HTTP self-call, no extra IAM surface. The EC2 calls its own in-process code, just off the request thread.
- **yt-dlp freshness:** A third, tiny EventBridge rule runs daily and uses SSM to execute `pip install --upgrade yt-dlp` on the EC2. Daily cadence matches YouTube's breakage cadence (rare) and avoids adding latency to every download.

**Rejected alternative (SSM-dispatch for video downloads):** An earlier draft of this plan invoked SSM from a `LambdaVideoDownloader` class on the EC2 to trigger downloads via an internal HTTP endpoint. That added: an AWS SDK dependency, an IAM policy for `ssm:SendCommand`, a video-download internal endpoint, and an EC2-to-EC2 HTTP round-trip — all for bug-for-bug equivalence of `ExecutorService`. We kept the `VideoDownloader` interface abstraction so a future cross-instance worker (SQS/Fargate) can be swapped in as a new impl without touching callers.

**AWS segregation rule:** All AWS-specific code stays behind interfaces (`VideoDownloader`, `StorageService`, `JobScheduler`) with `@Profile("aws")`-gated implementations. No `software.amazon.awssdk.*` imports from domain services or controllers.

**Tech Stack:** AWS Lambda (Python 3.11), EventBridge, SSM (for yt-dlp upgrade only), Terraform, Spring Boot 4.x (Kotlin), Spring `@Async`

**Design Spec:** `docs/plans/2026-04-03-phase-9-infrastructure-design.md` § 9E

---

### Task 1: Internal API Key Security Filter

Create a filter that secures `/api/internal/**` endpoints with a shared API key. The Lambda scheduler calls these endpoints; JWT auth doesn't apply because there's no user session.

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/config/InternalApiKeyFilter.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/config/InternalApiKeyFilterTest.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt`
- Modify: `src/main/resources/application-prod.properties`

- [x] **Step 1: Write failing tests**

Create `src/test/kotlin/org/sightech/memoryvault/config/InternalApiKeyFilterTest.kt`:

```kotlin
package org.sightech.memoryvault.config

import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals

class InternalApiKeyFilterTest {

    private val filterChain = mockk<FilterChain>(relaxed = true)

    @Test
    fun `allows request with valid API key`() {
        val filter = InternalApiKeyFilter("test-key-123")
        val request = MockHttpServletRequest("POST", "/api/internal/sync/feeds")
        request.addHeader("X-Internal-Key", "test-key-123")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        verify { filterChain.doFilter(request, response) }
        assertEquals(200, response.status)
    }

    @Test
    fun `rejects request with invalid API key`() {
        val filter = InternalApiKeyFilter("test-key-123")
        val request = MockHttpServletRequest("POST", "/api/internal/sync/feeds")
        request.addHeader("X-Internal-Key", "wrong-key")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)
        assertEquals(401, response.status)
    }

    @Test
    fun `rejects request with missing API key`() {
        val filter = InternalApiKeyFilter("test-key-123")
        val request = MockHttpServletRequest("POST", "/api/internal/sync/feeds")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)
        assertEquals(401, response.status)
    }

    @Test
    fun `passes through non-internal requests`() {
        val filter = InternalApiKeyFilter("test-key-123")
        val request = MockHttpServletRequest("GET", "/api/auth/login")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, filterChain)

        verify { filterChain.doFilter(request, response) }
        assertEquals(200, response.status)
    }
}
```

- [x] **Step 2: Implement InternalApiKeyFilter**

Create `src/main/kotlin/org/sightech/memoryvault/config/InternalApiKeyFilter.kt`:

```kotlin
package org.sightech.memoryvault.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Profile("aws")
class InternalApiKeyFilter(
    @Value("\${memoryvault.internal.api-key}")
    private val apiKey: String
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.requestURI.startsWith("/api/internal/")) {
            val providedKey = request.getHeader("X-Internal-Key")
            if (providedKey != apiKey) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key")
                return
            }
        }
        filterChain.doFilter(request, response)
    }
}
```

- [x] **Step 3: Update SecurityConfig to permit /api/internal/** (the API key filter handles auth for these, not JWT).

- [x] **Step 4: Add config property to application-prod.properties**

```properties
# Internal API key for Lambda -> EC2 calls
memoryvault.internal.api-key=${INTERNAL_API_KEY}
```

- [x] **Step 5: Run tests**

```bash
./gradlew test --tests "*InternalApiKeyFilterTest"
./gradlew test
```

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/config/InternalApiKeyFilter.kt src/test/kotlin/org/sightech/memoryvault/config/InternalApiKeyFilterTest.kt src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt src/main/resources/application-prod.properties
git commit -m "feat: add InternalApiKeyFilter for Lambda-to-EC2 auth"
```

---

### Task 2: InternalSyncController

REST controller that Lambda functions call to trigger sync operations. Feed + YouTube only — no video download endpoint, because video dispatch is in-process via `@Async`.

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/sync/controller/InternalSyncController.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/sync/controller/InternalSyncControllerTest.kt`

- [ ] **Step 1: Verify service method names**

Before writing tests, confirm the exact method names on `FeedService` and `VideoSyncService` that refresh all feeds and all YouTube lists respectively. If no "refresh all" method exists on one of them, add it — it should iterate its domain entities and call the per-item refresh already in use.

- [ ] **Step 2: Write failing tests**

```kotlin
package org.sightech.memoryvault.sync.controller

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.service.FeedService
import org.sightech.memoryvault.youtube.service.VideoSyncService
import kotlin.test.assertEquals

class InternalSyncControllerTest {

    private val feedService = mockk<FeedService>(relaxed = true)
    private val videoSyncService = mockk<VideoSyncService>(relaxed = true)
    private val controller = InternalSyncController(feedService, videoSyncService)

    @Test
    fun `syncFeeds refreshes all feeds`() {
        val response = controller.syncFeeds()
        verify { feedService.refreshAllFeeds() }
        assertEquals(200, response.statusCode.value())
    }

    @Test
    fun `syncYoutube refreshes all lists`() {
        val response = controller.syncYoutube()
        verify { videoSyncService.refreshAllLists() }
        assertEquals(200, response.statusCode.value())
    }
}
```

Substitute the actual method names verified in Step 1.

- [ ] **Step 3: Implement InternalSyncController — wrap in SyncJobService**

Each endpoint must wrap its work in `SyncJobService.recordStart/recordSuccess/recordFailure` so the admin dashboard's job history stays complete. Without this wrapper, Lambda-triggered syncs bypass the `sync_jobs` audit table entirely.

```kotlin
package org.sightech.memoryvault.sync.controller

import org.sightech.memoryvault.feed.service.FeedService
import org.sightech.memoryvault.sync.service.SyncJobService
import org.sightech.memoryvault.youtube.service.VideoSyncService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("aws")
@RequestMapping("/api/internal")
class InternalSyncController(
    private val feedService: FeedService,
    private val videoSyncService: VideoSyncService,
    private val syncJobService: SyncJobService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/sync/feeds")
    fun syncFeeds(): ResponseEntity<Map<String, String>> {
        log.info("Internal trigger: feed sync")
        val job = syncJobService.recordStart("FEED_SYNC")
        try {
            val metadata = feedService.refreshAllFeeds()  // should return Map<String,Any> per JobScheduler contract
            syncJobService.recordSuccess(job.id, metadata)
            return ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            log.error("Feed sync failed", e)
            syncJobService.recordFailure(job.id, e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    @PostMapping("/sync/youtube")
    fun syncYoutube(): ResponseEntity<Map<String, String>> {
        log.info("Internal trigger: YouTube sync")
        val job = syncJobService.recordStart("YOUTUBE_SYNC")
        try {
            val metadata = videoSyncService.refreshAllLists()
            syncJobService.recordSuccess(job.id, metadata)
            return ResponseEntity.ok(mapOf("status" to "success"))
        } catch (e: Exception) {
            log.error("YouTube sync failed", e)
            syncJobService.recordFailure(job.id, e.message ?: e.javaClass.simpleName)
            throw e
        }
    }
}
```

Key semantics:
- **Synchronous from Lambda's perspective**: the controller blocks until `refreshAll*` returns. The "fast return" comes from individual video downloads being `@Async` (Task 3), not from the top-level call.
- **Note**: This duplicates the wrapping already present in `SpringJobScheduler.schedule(...)`. Rather than wrap twice, the cleanest path is to extract a `SyncJobService.runTracked(jobType, task)` helper and use it from both `SpringJobScheduler` and `InternalSyncController`. Verify before implementing — the helper may already exist.

Tests in Step 2 must also verify `syncJobService.recordStart`/`recordSuccess` are called on happy path and `recordFailure` on exception.

- [ ] **Step 4: Run tests**

```bash
./gradlew test --tests "*InternalSyncControllerTest"
```

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/sync/controller/InternalSyncController.kt src/test/kotlin/org/sightech/memoryvault/sync/controller/InternalSyncControllerTest.kt
git commit -m "feat: add InternalSyncController for Lambda-triggered sync"
```

---

### Task 3: `@Async` Video Dispatch

Make `VideoDownloader.download(...)` dispatch on a bounded Spring `@Async` executor so the caller (usually `VideoSyncService` iterating a list) returns promptly. yt-dlp keeps running on the shared EC2 thread pool.

**Critical semantic change — read first:** the existing `LocalVideoDownloader` returns a `DownloadResult` that callers (VideoSyncService) use to update the `Video` entity (`video.filePath = result.filePath`). With `@Async` the return is discarded and the method returns before the download finishes — if nothing else changes, the Video entity never gets its `filePath`/status updated. **Therefore AsyncVideoDownloader must own its own post-download DB write**, in its own `@Transactional` block: fetch the Video by id → apply filePath/error → save. Callers must stop consuming the return value.

No AWS-specific code. `LocalVideoDownloader` is renamed to `AsyncVideoDownloader` without profile gating — the only impl. `LambdaVideoDownloader` stub is deleted. The `VideoDownloader` interface remains for future cross-instance workers.

**Pre-flight checks (do before coding):**
1. Read `VideoSyncService` to confirm how it consumes `downloader.download(...)` today and which Video fields need updating (`filePath`, `error`, `status`/`downloadedAt`?). Document findings in the commit message.
2. Verify `VideoRepository` has a `findByIdOrNull(UUID)` or equivalent.
3. Verify the existing `AsyncConfig.kt` (Phase 8 WebSocket relay executor) pattern — reuse its `AsyncUncaughtExceptionHandler` if it exists, else add one.

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/config/VideoDownloadAsyncConfig.kt`
- Modify (or create): `src/main/kotlin/org/sightech/memoryvault/config/AsyncConfig.kt` — ensure `AsyncUncaughtExceptionHandler` logs ERROR and optionally records failure on Video
- Modify: `src/main/kotlin/org/sightech/memoryvault/youtube/service/VideoDownloader.kt` (docs only — note the return is fire-and-forget; implementations must persist their own result)
- Rename: `src/main/kotlin/org/sightech/memoryvault/youtube/service/LocalVideoDownloader.kt` → `AsyncVideoDownloader.kt`; inject `VideoRepository`; wrap DB write in its own `@Transactional`
- Delete: `src/main/kotlin/org/sightech/memoryvault/youtube/service/LambdaVideoDownloader.kt` (stub, unused after this change)
- Modify: `src/main/kotlin/org/sightech/memoryvault/youtube/service/VideoSyncService.kt` — stop consuming the return value; it's fire-and-forget now
- Modify: `src/main/kotlin/org/sightech/memoryvault/youtube/service/YtDlpService.kt` (add bounded `Process.waitFor(timeout, unit)` — 30min default, configurable)
- Modify tests: references to `LocalVideoDownloader` → `AsyncVideoDownloader`

- [ ] **Step 1: Add the executor bean with back-pressure**

Create `src/main/kotlin/org/sightech/memoryvault/config/VideoDownloadAsyncConfig.kt`:

```kotlin
package org.sightech.memoryvault.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

@Configuration
class VideoDownloadAsyncConfig {

    /** Bounded executor for yt-dlp downloads. Two concurrent downloads keeps CPU/network
     *  sensible on a t-class EC2 and avoids saturating disk with in-flight temp files.
     *  `CallerRunsPolicy` is deliberate: when the queue is full, the submitting thread runs
     *  the work itself — this back-pressures the caller (Lambda-triggered sync) rather than
     *  silently dropping downloads or surfacing a 500. */
    @Bean(name = ["videoDownloadExecutor"])
    fun videoDownloadExecutor(): Executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 2
        queueCapacity = 100
        setThreadNamePrefix("video-dl-")
        setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(60)
        initialize()
    }
}
```

`setWaitForTasksToCompleteOnShutdown(true)` gives in-flight downloads up to 60s to finish on JVM shutdown. Partial-download cleanup on hard termination is outside scope; temp files live under `/tmp` and get reaped on reboot.

- [ ] **Step 2: Async uncaught exception handler**

In the existing `AsyncConfig.kt` (Phase 8), ensure `AsyncConfigurer.getAsyncUncaughtExceptionHandler()` is implemented and logs at ERROR with the method name and args (videoId in our case). Swallowed exceptions in `@Async void` methods are the #1 cause of silent failures — this is non-negotiable.

```kotlin
override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler {
    return AsyncUncaughtExceptionHandler { ex, method, params ->
        val log = LoggerFactory.getLogger(method.declaringClass)
        log.error("Uncaught exception in @Async {}({}): {}", method.name, params.contentToString(), ex.message, ex)
    }
}
```

- [ ] **Step 3: Rewrite AsyncVideoDownloader to own its DB write**

```kotlin
@Component
class AsyncVideoDownloader(
    private val ytDlpService: YtDlpService,
    private val storageService: StorageService,
    private val videoRepository: VideoRepository
) : VideoDownloader {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async("videoDownloadExecutor")
    @Transactional
    override fun download(youtubeUrl: String, videoId: UUID) {
        val video = videoRepository.findById(videoId).orElse(null) ?: run {
            log.warn("Video {} not found at download time (likely deleted)", videoId)
            return
        }

        // ... existing tempDir + yt-dlp flow ...

        if (!dlResult.success) {
            video.error = dlResult.error
            videoRepository.save(video)
            return
        }

        video.filePath = storageKey
        video.error = null
        videoRepository.save(video)
    }
}
```

Notes:
- Signature changed from `DownloadResult` return to `Unit` — aligns with `@Async void` semantics. Update the `VideoDownloader` interface likewise.
- `@Transactional` opens a fresh transaction on the async thread (caller's transaction has already committed by the time this runs).
- `video.error` / `video.filePath` field names are illustrative — confirm against the entity in pre-flight.
- If the Video was deleted between scheduling and execution (soft-delete), we log and bail — no orphan write.

- [ ] **Step 4: Update VideoSyncService to stop consuming the return**

Find every caller of `videoDownloader.download(...)`. Today, at least one reads the return value and updates the Video. Remove that logic — the downloader owns that write now. The caller just schedules: `videoDownloader.download(url, videoId)` and moves on.

- [ ] **Step 5: Subprocess timeout on yt-dlp**

In `YtDlpService.downloadVideo(...)`, replace `process.waitFor()` with `process.waitFor(timeoutMinutes, TimeUnit.MINUTES)`. On `false` return (timeout), `process.destroyForcibly()` and return `DownloadResult(success=false, error="yt-dlp timed out after ${timeoutMinutes}min")`. Make the timeout configurable via `memoryvault.youtube.download-timeout-minutes` with default `30`.

This is correct independent of `@Async` — a hung yt-dlp should never wedge a thread indefinitely.

- [ ] **Step 6: Tests**

- Update any existing `LocalVideoDownloader*` tests to use `AsyncVideoDownloader`. Mock `VideoRepository`; verify the entity is updated with `filePath` on success and with `error` on failure. Use `CompletableFuture`-based test assertions OR make the test invoke `download(...)` directly (bypass proxy) to keep it synchronous — prefer the latter for unit tests.
- Add a unit test: async method returns before work finishes. Inject a custom `SyncTaskExecutor` in the test context so `@Async` runs synchronously; assert behavior.
- Add a `YtDlpService` timeout test using a mock `ProcessBuilder` that sleeps longer than the timeout.
- Add a `VideoDownloadAsyncConfig` bean wiring test (`@SpringBootTest`, autowire `videoDownloadExecutor`, assert `corePoolSize == 2` and rejection handler is `CallerRunsPolicy`).
- Add a test that exercises `AsyncUncaughtExceptionHandler`: throw from a test `@Async` method, verify log output via a `ListAppender`.

- [ ] **Step 7: Run tests**

```bash
./gradlew test
```

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: @Async video downloads with own-tx DB write, executor back-pressure, yt-dlp timeout"
```

---

### Task 4: Python Lambda Handlers

Two tiny Python handlers. Stdlib only — no `requirements.txt` needed beyond a placeholder.

**Files:**
- Create: `lambdas/feed-sync/handler.py`
- Create: `lambdas/feed-sync/requirements.txt` (empty/comment)
- Create: `lambdas/youtube-sync/handler.py`
- Create: `lambdas/youtube-sync/requirements.txt` (empty/comment)

- [ ] **Step 1: Create feed-sync handler**

`lambdas/feed-sync/handler.py`:

```python
import json
import os
import urllib.request


def handler(event, context):
    """EventBridge-triggered scheduler. POSTs to the EC2 internal sync endpoint."""
    base_url = os.environ["APP_BASE_URL"]
    api_key = os.environ["INTERNAL_API_KEY"]

    url = f"{base_url}/api/internal/sync/feeds"
    req = urllib.request.Request(url, method="POST")
    req.add_header("X-Internal-Key", api_key)
    req.add_header("Content-Type", "application/json")

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = json.loads(resp.read())
            print(f"Feed sync triggered: {body}")
            return {"statusCode": 200, "body": body}
    except Exception as e:
        print(f"Feed sync failed: {e}")
        raise
```

- [ ] **Step 2: Create youtube-sync handler**

`lambdas/youtube-sync/handler.py` — identical to feed-sync but with `/api/internal/sync/youtube`.

- [ ] **Step 3: Commit**

```bash
git add lambdas/
git commit -m "feat: add feed-sync and youtube-sync Lambda handlers"
```

---

### Task 5: Terraform — Lambda (VPC-attached), EventBridge, yt-dlp Upgrade

Three EventBridge rules: feed sync (every 30 min → feed-sync Lambda), YouTube sync (every 6 hours → youtube-sync Lambda), daily yt-dlp upgrade (once per day → SSM RunCommand on EC2).

**Networking decision (security):** Lambdas run **attached to the same VPC and subnet as the EC2**, and `APP_BASE_URL` points to the EC2's **private IP**. This keeps the `X-Internal-Key` header off the public internet entirely — HTTP between Lambda and EC2 is acceptable when it's AWS-backbone-only on a private subnet. A security group on the EC2 opens port 8085 only to the Lambda's security group. No NAT gateway required: the Lambda handlers use `urllib` and don't call any AWS service, so no outbound internet is needed.

**Pre-flight checks before running `terraform apply`:**
1. Verify `terraform/templates/user_data.sh` installs yt-dlp (expected `pip3 install yt-dlp` or `pipx install yt-dlp`). If not, add it to user_data — otherwise AsyncVideoDownloader will fail at runtime.
2. Verify the EC2's instance profile role has `s3:PutObject` (and `s3:PutObjectAcl` if using canned ACLs) on the video bucket. `S3StorageService` needs this to upload downloaded videos. Check `terraform/iam.tf` (or equivalent).
3. If either check fails, fix in the same Terraform apply.

**Files:**
- Create: `terraform/lambda.tf`
- Create: `terraform/yt-dlp-upgrade.tf`
- Modify: `terraform/variables.tf`
- Modify: `terraform/terraform.tfvars.example`
- Modify: `terraform/ec2.tf` or `terraform/network.tf` — add Lambda SG + ingress on EC2 SG from Lambda SG for port 8085
- Possibly modify: `terraform/templates/user_data.sh` (if yt-dlp missing per pre-flight check #1)
- Possibly modify: `terraform/iam.tf` (if S3 perms missing per pre-flight check #2)

- [ ] **Step 1: Lambda scheduler infrastructure (VPC-attached)**

In `terraform/lambda.tf`:
- IAM role for Lambda execution: managed policies `AWSLambdaBasicExecutionRole` (CloudWatch Logs) + `AWSLambdaVPCAccessExecutionRole` (ENI management for VPC-attached Lambdas)
- `aws_security_group "lambda_sync"` — no ingress, all-egress (Lambda needs to reach EC2 private IP on 8085)
- Append to EC2 SG: ingress rule on port 8085 from `aws_security_group.lambda_sync.id` (in addition to whatever public ingress the EC2 already has, or replace it — see pre-flight #1 if we're comfortable closing :8085 to the public entirely, which depends on whether the Angular app talks to :8085 directly or via a different port/proxy)
- `data "archive_file"` for each handler directory → zip
- `aws_lambda_function` × 2 (feed-sync, youtube-sync):
  - `runtime = "python3.11"`
  - `vpc_config { subnet_ids = [aws_subnet.app.id]; security_group_ids = [aws_security_group.lambda_sync.id] }`
  - `environment { variables = { APP_BASE_URL = "http://${aws_instance.app.private_ip}:8085", INTERNAL_API_KEY = random_password.internal_api_key.result } }`
  - `timeout = 60` — Lambda blocks until EC2 finishes the sync; give it room, but not absurd
- `aws_cloudwatch_event_rule` × 2 with `schedule_expression = var.feed_sync_schedule` / `var.youtube_sync_schedule`
- `aws_cloudwatch_event_target` × 2 connecting rules to Lambdas
- `aws_lambda_permission` × 2 allowing EventBridge to invoke

Important: `INTERNAL_API_KEY` is generated by Terraform (`random_password`) and surfaced to both the Lambda env and the EC2 `user_data` env file (same shape as the Cognito pool ID plumbing). See Step 3.

**Cold-start note:** VPC-attached Lambda cold-starts are ~1s longer than non-VPC. For a 30-minute cron this is negligible.

**EC2 replacement note:** Referencing `aws_instance.app.private_ip` in the Lambda env creates a Terraform dependency — if the EC2 is replaced, Terraform will re-deploy the Lambdas with the new IP. Good (no stale IP drift).

- [ ] **Step 2: Daily yt-dlp upgrade**

In `terraform/yt-dlp-upgrade.tf`:
- `aws_cloudwatch_event_rule` `yt_dlp_upgrade` with `schedule_expression = "rate(1 day)"`
- `aws_cloudwatch_event_target` pointing to `arn:aws:ssm:...::document/AWS-RunShellScript`, with input JSON containing the command `pip3 install --upgrade yt-dlp` (or `pipx upgrade yt-dlp` if that's what `user_data.sh` installs; verify first)
- Target includes a `run_command_targets` block selecting the EC2 by tag `Name=memoryvault-app`
- IAM role for EventBridge to invoke SSM with `ssm:SendCommand` on the `AWS-RunShellScript` document and on the tagged instance

- [ ] **Step 3: Plumb INTERNAL_API_KEY to EC2**

- Generate in `terraform/ec2.tf` (or a dedicated secrets file): `resource "random_password" "internal_api_key" { length = 48; special = false }`
- Add `internal_api_key = random_password.internal_api_key.result` to the `templatefile()` vars
- Add `MEMORYVAULT_INTERNAL__API__KEY=${internal_api_key}` to `terraform/templates/user_data.sh`
- Remember: property key in Spring is `memoryvault.internal.api-key`, so env var is `MEMORYVAULT_INTERNAL_API__KEY` (Spring Boot relaxed binding — single dot = single underscore, dash = double underscore). Verify the conversion locally against the Cognito precedent (`memoryvault.cognito.client-id` → `MEMORYVAULT_COGNITO_CLIENT__ID`).
- Mark the random resource output as `sensitive = true`.
- Because this changes `user_data`, the EC2 must be replaced: add `user_data_replace_on_change = true` is already on the instance (from last session). Verify; run `terraform plan` and expect instance replacement.

- [ ] **Step 4: Variables**

```hcl
variable "feed_sync_schedule" {
  description = "EventBridge schedule expression for feed sync"
  type        = string
  default     = "rate(30 minutes)"
}

variable "youtube_sync_schedule" {
  description = "EventBridge schedule expression for YouTube sync"
  type        = string
  default     = "rate(6 hours)"
}
```

- [ ] **Step 5: Validate**

```bash
cd terraform && terraform fmt && terraform validate && terraform plan
```

Plan expectations:
- New Lambda functions, IAM role, 3 EventBridge rules, 3 targets, 2 Lambda permissions, 1 `random_password`
- EC2 replacement due to `user_data` change (brief downtime, same as Phase 9D)

- [ ] **Step 6: Apply and verify**

```bash
cd terraform && terraform apply
```

After apply:
- `aws events list-rules --name-prefix memoryvault`
- Manually invoke each Lambda (`aws lambda invoke`) and verify the EC2 logs show the internal trigger
- Wait for one scheduled firing to confirm EventBridge → Lambda → EC2 is end-to-end healthy
- After a day, verify the yt-dlp upgrade ran (SSM command history or EC2 log)

- [ ] **Step 7: Commit**

```bash
git add terraform/
git commit -m "feat: Terraform Lambda + EventBridge for sync schedules and yt-dlp upgrade"
```

---

### Task 6: Remove Spring cron from AWS profile

The existing `FeedSyncRegistrar` schedules via `memoryvault.feeds.sync-cron`. Under the `aws` profile, we now rely on Lambda — the in-process cron should not also fire.

- [ ] **Step 1:** In `application-prod.properties`, set `memoryvault.feeds.sync-cron=` (empty, disabling in-process cron) and add a comment explaining EventBridge owns this in AWS mode.
- [ ] **Step 2:** Same for any YouTube-side cron if present.
- [ ] **Step 3:** Commit.

---

### Task 7: Smoke-test extension

The existing `scripts/smoke-test.sh` probes public endpoints. Add a local-only check that the internal endpoints are wired (it won't pass on prod because the tester lacks the API key, and that's fine — assert 401 when no key is provided, confirming the filter is live).

- [ ] **Step 1:** In `scripts/smoke-test.sh`, after the existing public checks, add:

```bash
check "Internal endpoint requires API key" "$BASE_URL/api/internal/sync/feeds" 401 "-X POST"
```

`check` already supports an `extra_args` param. Confirm it works with `-X POST`; if not, add a helper.

- [ ] **Step 2:** Commit.

---

## Summary Table

| Task | Description                                               | Key Files                                                                                                                              |
|------|-----------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| 1    | InternalApiKeyFilter (timing-safe comparison)             | `config/InternalApiKeyFilter.kt`, `SecurityConfig.kt`, `application-prod.properties`                                                   |
| 2    | InternalSyncController (feeds + YouTube) w/ SyncJob wrap  | `sync/controller/InternalSyncController.kt`                                                                                            |
| 3    | `@Async` video dispatch, own-tx DB write, back-pressure   | `config/VideoDownloadAsyncConfig.kt`, `config/AsyncConfig.kt`, `AsyncVideoDownloader.kt` (renamed), `YtDlpService.kt`; delete `LambdaVideoDownloader.kt` |
| 4    | Python Lambda handlers                                    | `lambdas/feed-sync/handler.py`, `lambdas/youtube-sync/handler.py`                                                                      |
| 5    | Terraform — VPC-attached Lambda, EventBridge, yt-dlp upgrade | `terraform/lambda.tf`, `terraform/yt-dlp-upgrade.tf`, `terraform/ec2.tf`, `terraform/templates/user_data.sh`, `terraform/variables.tf` |
| 6    | Disable in-process cron under `aws` profile               | `application-prod.properties`                                                                                                          |
| 7    | Smoke-test extension for internal endpoint auth           | `scripts/smoke-test.sh`                                                                                                                |

---

## Concerns deferred to Phase 9F (observability / ops hardening)

Enumerated during design review; not blocking 9E but should be on 9F's list.

- **Rate limiting on `/api/internal/**`** — once 9E lands with VPC-private networking, the attack surface is AWS-internal only. A leaked key within the VPC is still a concern worth a Bucket4j or Resilience4j filter in 9F.
- **Correlation IDs** — `X-Request-Id` from Lambda → internal controller → `@Async` worker, threaded through MDC. Makes multi-hop debugging tractable.
- **CloudWatch alarms** — alarm on: Lambda invocation failures, Lambda throttles, yt-dlp upgrade SSM failures, `sync_jobs` rows with status `FAILED` trending up. Alarms → SNS → email.
- **yt-dlp upgrade retries** — if the daily SSM `pip install --upgrade yt-dlp` fails (PyPI flake), does it retry? EventBridge→SSM has no built-in retry. Consider a tiny Lambda wrapper instead if this becomes a problem.
- **Key rotation** — changing `INTERNAL_API_KEY` forces EC2 replacement (user_data change) + Lambda redeploy. Document as a full-redeploy event. If rotation needs to be routine, move the key to Secrets Manager and have both EC2 and Lambda fetch at startup/invocation.
