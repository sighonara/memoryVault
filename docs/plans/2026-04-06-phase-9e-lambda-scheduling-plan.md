# Phase 9E — Lambda Scheduling + Video Worker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Spring cron scheduling with AWS EventBridge + Lambda for feed/YouTube sync, and implement the LambdaVideoDownloader for EC2-based yt-dlp downloads dispatched via SSM.

**Architecture:** Two Python Lambda functions act as schedulers, calling internal REST endpoints on EC2. Video downloads happen on EC2 (where yt-dlp is installed), triggered by SSM commands from the LambdaVideoDownloader. Internal endpoints are secured by a shared API key header.

**Tech Stack:** AWS Lambda (Python 3.11), EventBridge, SSM, Terraform, Spring Boot (Kotlin), AWS SDK v2

**Design Spec:** `docs/plans/2026-04-03-phase-9-infrastructure-design.md` § 9E

---

### Task 1: Add SSM SDK Dependency

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add SSM dependency**

In `build.gradle.kts`, add after the CloudWatch Logs dependency:

```kotlin
implementation("software.amazon.awssdk:ssm")
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew compileKotlin
```

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts && git commit -m "feat: add AWS SDK SSM dependency"
```

---

### Task 2: Internal API Key Security Filter

Create a filter that secures `/api/internal/**` endpoints with a shared API key.

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/config/InternalApiKeyFilter.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/config/InternalApiKeyFilterTest.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt`

- [ ] **Step 1: Write failing tests**

Create `src/test/kotlin/org/sightech/memoryvault/config/InternalApiKeyFilterTest.kt`:

```kotlin
package org.sightech.memoryvault.config

import io.mockk.mockk
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
        assertEquals(200, response.status)
    }
}
```

- [ ] **Step 2: Implement InternalApiKeyFilter**

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

- [ ] **Step 3: Update SecurityConfig to permit /api/internal/**

Add `/api/internal/**` to the `requestMatchers(...).permitAll()` list in SecurityConfig (the API key filter handles auth for these, not JWT).

- [ ] **Step 4: Add config property to application-prod.properties**

```properties
# Internal API key for Lambda -> EC2 calls
memoryvault.internal.api-key=${INTERNAL_API_KEY}
```

- [ ] **Step 5: Run tests**

```bash
./gradlew test --tests "*InternalApiKeyFilterTest"
./gradlew test
```

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/config/InternalApiKeyFilter.kt src/test/kotlin/org/sightech/memoryvault/config/InternalApiKeyFilterTest.kt src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt src/main/resources/application-prod.properties && git commit -m "feat: add InternalApiKeyFilter for Lambda-to-EC2 auth"
```

---

### Task 3: InternalSyncController

Create the REST controller that Lambda functions call to trigger sync operations.

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/sync/controller/InternalSyncController.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/sync/controller/InternalSyncControllerTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package org.sightech.memoryvault.sync.controller

import io.mockk.coEvery
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
    fun `syncFeeds calls feedService refreshAllFeeds`() {
        val response = controller.syncFeeds()
        verify { feedService.refreshAllFeeds() }
        assertEquals(200, response.statusCode.value())
    }

    @Test
    fun `syncYoutube calls videoSyncService refreshAllLists`() {
        val response = controller.syncYoutube()
        verify { videoSyncService.refreshAllLists() }
        assertEquals(200, response.statusCode.value())
    }
}
```

- [ ] **Step 2: Implement InternalSyncController**

```kotlin
package org.sightech.memoryvault.sync.controller

import org.sightech.memoryvault.feed.service.FeedService
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
    private val videoSyncService: VideoSyncService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/sync/feeds")
    fun syncFeeds(): ResponseEntity<Map<String, String>> {
        log.info("Internal trigger: feed sync")
        feedService.refreshAllFeeds()
        return ResponseEntity.ok(mapOf("status" to "started"))
    }

    @PostMapping("/sync/youtube")
    fun syncYoutube(): ResponseEntity<Map<String, String>> {
        log.info("Internal trigger: YouTube sync")
        videoSyncService.refreshAllLists()
        return ResponseEntity.ok(mapOf("status" to "started"))
    }
}
```

Note: The actual method names (`refreshAllFeeds`, `refreshAllLists`) should match what exists in the codebase. The implementer must read `FeedService` and `VideoSyncService` to confirm the correct method names.

- [ ] **Step 3: Run tests**

```bash
./gradlew test --tests "*InternalSyncControllerTest"
```

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/sync/controller/InternalSyncController.kt src/test/kotlin/org/sightech/memoryvault/sync/controller/InternalSyncControllerTest.kt && git commit -m "feat: add InternalSyncController for Lambda-triggered sync"
```

---

### Task 4: Video Download Internal Endpoint

Add the internal endpoint for video downloads (called via SSM from LambdaVideoDownloader).

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/sync/controller/InternalSyncController.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/sync/controller/InternalVideoDownloadTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package org.sightech.memoryvault.sync.controller

import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.youtube.service.VideoSyncService
import java.util.UUID
import kotlin.test.assertEquals

class InternalVideoDownloadTest {

    private val feedService = mockk<FeedService>(relaxed = true)
    private val videoSyncService = mockk<VideoSyncService>(relaxed = true)
    private val controller = InternalSyncController(feedService, videoSyncService)

    @Test
    fun `downloadVideo triggers download for given video ID`() {
        val videoId = UUID.randomUUID()
        val response = controller.downloadVideo(videoId)
        verify { videoSyncService.downloadVideo(videoId) }
        assertEquals(200, response.statusCode.value())
    }
}
```

- [ ] **Step 2: Add downloadVideo endpoint**

Add to `InternalSyncController`:

```kotlin
@PostMapping("/videos/download")
fun downloadVideo(@RequestParam videoId: UUID): ResponseEntity<Map<String, String>> {
    log.info("Internal trigger: video download for {}", videoId)
    videoSyncService.downloadVideo(videoId)
    return ResponseEntity.ok(mapOf("status" to "started", "videoId" to videoId.toString()))
}
```

Note: The implementer must verify that `VideoSyncService` has a `downloadVideo(videoId)` method, or find the correct method name. If it doesn't exist, it needs to be added (it should delegate to existing `YtDlpService` + `StorageService`).

- [ ] **Step 3: Add yt-dlp auto-update before download**

In the download flow (either in the controller or in `VideoSyncService`), run `pip install --upgrade yt-dlp` before invoking yt-dlp. This is a fast no-op when already current.

- [ ] **Step 4: Run tests**

```bash
./gradlew test
```

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/sync/controller/InternalSyncController.kt src/test/kotlin/org/sightech/memoryvault/sync/controller/InternalVideoDownloadTest.kt && git commit -m "feat: add internal video download endpoint with yt-dlp auto-update"
```

---

### Task 5: LambdaVideoDownloader Implementation

Replace the stub `LambdaVideoDownloader` with a working SSM-based implementation.

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/youtube/service/LambdaVideoDownloader.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/youtube/service/LambdaVideoDownloaderTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package org.sightech.memoryvault.youtube.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.SendCommandRequest
import software.amazon.awssdk.services.ssm.model.SendCommandResponse
import software.amazon.awssdk.services.ssm.model.Command
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LambdaVideoDownloaderTest {

    private val ssmClient = mockk<SsmClient>()
    private lateinit var downloader: LambdaVideoDownloader

    @BeforeEach
    fun setUp() {
        downloader = LambdaVideoDownloader(
            ssmClient = ssmClient,
            instanceId = "i-test123",
            appBaseUrl = "http://localhost:8085",
            internalApiKey = "test-key"
        )
    }

    @Test
    fun `download sends SSM command to EC2 instance`() {
        val videoId = UUID.randomUUID()
        every { ssmClient.sendCommand(any<SendCommandRequest>()) } returns
            SendCommandResponse.builder()
                .command(Command.builder().commandId("cmd-123").build())
                .build() as SendCommandResponse

        downloader.download(videoId)

        val commandSlot = slot<SendCommandRequest>()
        verify { ssmClient.sendCommand(capture(commandSlot)) }
        assertTrue(commandSlot.captured.instanceIds().contains("i-test123"))
    }
}
```

- [ ] **Step 2: Implement LambdaVideoDownloader**

Replace the stub in `src/main/kotlin/org/sightech/memoryvault/youtube/service/LambdaVideoDownloader.kt`. The implementation should:
- Inject `SsmClient`, `instanceId`, `appBaseUrl`, and `internalApiKey` via `@Value`
- Send an SSM `RunShellScript` command that curls `POST http://localhost:8085/api/internal/videos/download?videoId={id}` with the `X-Internal-Key` header
- Log the SSM command ID
- Return immediately (async — the download happens on EC2)

- [ ] **Step 3: Add SsmClient bean to AwsConfig**

Add to `src/main/kotlin/org/sightech/memoryvault/config/AwsConfig.kt`:

```kotlin
@Bean
fun ssmClient(): SsmClient = SsmClient.builder()
    .region(Region.of(s3Region))
    .build()
```

- [ ] **Step 4: Add config properties**

Add to `application-prod.properties`:

```properties
memoryvault.aws.ec2-instance-id=${EC2_INSTANCE_ID:}
memoryvault.aws.app-base-url=http://localhost:8085
```

- [ ] **Step 5: Run tests**

```bash
./gradlew test --tests "*LambdaVideoDownloaderTest"
./gradlew test
```

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/youtube/service/LambdaVideoDownloader.kt src/test/kotlin/org/sightech/memoryvault/youtube/service/LambdaVideoDownloaderTest.kt src/main/kotlin/org/sightech/memoryvault/config/AwsConfig.kt src/main/resources/application-prod.properties && git commit -m "feat: implement LambdaVideoDownloader with SSM dispatch"
```

---

### Task 6: Lambda Functions (Python)

Create the two Python Lambda functions for feed and YouTube sync.

**Files:**
- Create: `lambdas/feed-sync/handler.py`
- Create: `lambdas/feed-sync/requirements.txt`
- Create: `lambdas/youtube-sync/handler.py`
- Create: `lambdas/youtube-sync/requirements.txt`

- [ ] **Step 1: Create feed-sync Lambda**

Create `lambdas/feed-sync/handler.py`:

```python
import os
import urllib.request
import json

def handler(event, context):
    """Triggered by EventBridge schedule. Calls the feed sync internal API on EC2."""
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

Create `lambdas/feed-sync/requirements.txt` (empty — no external deps needed):

```
# No external dependencies — uses urllib from stdlib
```

- [ ] **Step 2: Create youtube-sync Lambda**

Create `lambdas/youtube-sync/handler.py`:

```python
import os
import urllib.request
import json

def handler(event, context):
    """Triggered by EventBridge schedule. Calls the YouTube sync internal API on EC2."""
    base_url = os.environ["APP_BASE_URL"]
    api_key = os.environ["INTERNAL_API_KEY"]

    url = f"{base_url}/api/internal/sync/youtube"
    req = urllib.request.Request(url, method="POST")
    req.add_header("X-Internal-Key", api_key)
    req.add_header("Content-Type", "application/json")

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = json.loads(resp.read())
            print(f"YouTube sync triggered: {body}")
            return {"statusCode": 200, "body": body}
    except Exception as e:
        print(f"YouTube sync failed: {e}")
        raise
```

Create `lambdas/youtube-sync/requirements.txt`:

```
# No external dependencies — uses urllib from stdlib
```

- [ ] **Step 3: Commit**

```bash
git add lambdas/ && git commit -m "feat: add feed-sync and youtube-sync Lambda functions"
```

---

### Task 7: Terraform for Lambda + EventBridge

Create Terraform resources for the Lambda functions, EventBridge rules, and IAM roles.

**Files:**
- Create: `terraform/lambda.tf`

- [ ] **Step 1: Create lambda.tf**

This file should define:
- IAM role for Lambda execution (basic execution + VPC access if needed)
- `aws_lambda_function` for feed-sync and youtube-sync
- `data "archive_file"` to zip each Lambda directory
- `aws_cloudwatch_event_rule` for each schedule (feed: every 30 min, youtube: every 6 hours)
- `aws_cloudwatch_event_target` connecting rules to Lambda functions
- `aws_lambda_permission` allowing EventBridge to invoke each function
- Environment variables: `APP_BASE_URL` (EC2 private IP or EIP), `INTERNAL_API_KEY`

Key configuration:
```hcl
variable "feed_sync_schedule" {
  description = "Cron expression for feed sync (EventBridge format)"
  type        = string
  default     = "rate(30 minutes)"
}

variable "youtube_sync_schedule" {
  description = "Cron expression for YouTube sync (EventBridge format)"
  type        = string
  default     = "rate(6 hours)"
}
```

- [ ] **Step 2: Add new variables to variables.tf**

Add `feed_sync_schedule` and `youtube_sync_schedule` variables.

- [ ] **Step 3: Add to terraform.tfvars.example**

```hcl
feed_sync_schedule    = "rate(30 minutes)"
youtube_sync_schedule = "rate(6 hours)"
```

- [ ] **Step 4: Validate**

```bash
cd terraform && terraform fmt -check && cd ..
```

- [ ] **Step 5: Commit**

```bash
git add terraform/lambda.tf terraform/variables.tf terraform/terraform.tfvars.example && git commit -m "feat: add Terraform Lambda + EventBridge for scheduled sync"
```

---

## Summary Table

| Task  | Description                                           | Key Files                                                  |
|-------|-------------------------------------------------------|------------------------------------------------------------|
| 1     | SSM SDK dependency                                    | `build.gradle.kts`                                         |
| 2     | InternalApiKeyFilter                                  | `config/InternalApiKeyFilter.kt`, `SecurityConfig.kt`      |
| 3     | InternalSyncController (feeds + YouTube)              | `sync/controller/InternalSyncController.kt`                |
| 4     | Video download internal endpoint + yt-dlp auto-update | `InternalSyncController.kt`                                |
| 5     | LambdaVideoDownloader (SSM dispatch)                  | `youtube/service/LambdaVideoDownloader.kt`, `AwsConfig.kt` |
| 6     | Python Lambda functions                               | `lambdas/feed-sync/`, `lambdas/youtube-sync/`              |
| 7     | Terraform Lambda + EventBridge                        | `terraform/lambda.tf`, `terraform/variables.tf`            |
