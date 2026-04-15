# Phase 9C — AWS Service Implementations Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the stub S3StorageService and CloudWatchLogService with working AWS SDK v2 implementations, activated by `@Profile("aws")`.

**Architecture:** Both services implement existing interfaces (`StorageService`, `LogService`) and are activated by the `aws` Spring profile. Local implementations remain unchanged. AWS SDK v2 clients are configured as Spring beans via a dedicated config class.

**Tech Stack:** AWS SDK v2 (S3, CloudWatch Logs), Kotlin, Spring Boot, MockK (unit tests)

**Design Spec:** `docs/plans/2026-04-03-phase-9-infrastructure-design.md` § 9C

---

### Task 1: Add AWS SDK Dependencies

**Files:**
- Modify: `build.gradle.kts`

- [x] **Step 1: Add AWS SDK BOM and dependencies**

In `build.gradle.kts`, add after the RSS dependencies block (line ~53):

```kotlin
// AWS SDK v2
implementation(platform("software.amazon.awssdk:bom:2.31.17"))
implementation("software.amazon.awssdk:s3")
implementation("software.amazon.awssdk:cloudwatchlogs")
```

- [x] **Step 2: Verify it compiles**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [x] **Step 3: Commit**

```bash
git add build.gradle.kts && git commit -m "feat: add AWS SDK v2 dependencies (S3, CloudWatch Logs)"
```

---

### Task 2: AWS SDK Config Class

Create a Spring configuration class that provides `S3Client` and `CloudWatchLogsClient` beans, only active under the `aws` profile.

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/config/AwsConfig.kt`

- [x] **Step 1: Write the failing test**

Create `src/test/kotlin/org/sightech/memoryvault/config/AwsConfigTest.kt`:

```kotlin
package org.sightech.memoryvault.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import software.amazon.awssdk.regions.Region

class AwsConfigTest {

    @Test
    fun `creates S3Client with specified region`() {
        val config = AwsConfig()
        config.s3Region = "us-east-1"
        assertDoesNotThrow { config.s3Client() }
    }

    @Test
    fun `creates CloudWatchLogsClient with specified region`() {
        val config = AwsConfig()
        config.cloudwatchRegion = "us-east-1"
        assertDoesNotThrow { config.cloudWatchLogsClient() }
    }
}
```

- [x] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests "*AwsConfigTest"
```

Expected: FAIL — `AwsConfig` class doesn't exist yet.

- [x] **Step 3: Write the implementation**

Create `src/main/kotlin/org/sightech/memoryvault/config/AwsConfig.kt`:

```kotlin
package org.sightech.memoryvault.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
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
}
```

- [x] **Step 4: Run test to verify it passes**

```bash
./gradlew test --tests "*AwsConfigTest"
```

Expected: PASS

- [x] **Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/config/AwsConfig.kt src/test/kotlin/org/sightech/memoryvault/config/AwsConfigTest.kt && git commit -m "feat: add AwsConfig with S3Client and CloudWatchLogsClient beans"
```

---

### Task 3: S3StorageService Implementation

Replace the stub with a working implementation using AWS SDK v2.

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/storage/S3StorageService.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/storage/S3StorageServiceTest.kt`

- [x] **Step 1: Write the failing tests**

Create `src/test/kotlin/org/sightech/memoryvault/storage/S3StorageServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.storage

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class S3StorageServiceTest {

    private val s3Client = mockk<S3Client>(relaxed = true)
    private lateinit var service: S3StorageService

    @BeforeEach
    fun setUp() {
        service = S3StorageService(s3Client, "test-bucket")
    }

    @Test
    fun `store uploads object and returns S3 key`() {
        val input = ByteArrayInputStream("hello".toByteArray())
        val result = service.store("videos/abc.mp4", input)
        assertEquals("videos/abc.mp4", result)

        val putSlot = slot<PutObjectRequest>()
        verify { s3Client.putObject(capture(putSlot), any<RequestBody>()) }
        assertEquals("test-bucket", putSlot.captured.bucket())
        assertEquals("videos/abc.mp4", putSlot.captured.key())
    }

    @Test
    fun `retrieve returns input stream from S3`() {
        val responseStream = mockk<ResponseInputStream<GetObjectResponse>>(relaxed = true)
        every { s3Client.getObject(any<GetObjectRequest>()) } returns responseStream

        val result = service.retrieve("videos/abc.mp4")
        assertEquals(responseStream, result)
    }

    @Test
    fun `delete removes object from S3`() {
        service.delete("videos/abc.mp4")

        val deleteSlot = slot<DeleteObjectRequest>()
        verify { s3Client.deleteObject(capture(deleteSlot)) }
        assertEquals("test-bucket", deleteSlot.captured.bucket())
        assertEquals("videos/abc.mp4", deleteSlot.captured.key())
    }

    @Test
    fun `exists returns true when head succeeds`() {
        every { s3Client.headObject(any<HeadObjectRequest>()) } returns mockk()
        assertTrue(service.exists("videos/abc.mp4"))
    }

    @Test
    fun `exists returns false when NoSuchKeyException`() {
        every { s3Client.headObject(any<HeadObjectRequest>()) } throws
            NoSuchKeyException.builder().message("not found").build()
        assertFalse(service.exists("videos/abc.mp4"))
    }

    @Test
    fun `usedBytes sums object sizes across pages`() {
        val obj1 = S3Object.builder().size(100L).build()
        val obj2 = S3Object.builder().size(200L).build()
        val response = ListObjectsV2Response.builder()
            .contents(listOf(obj1, obj2))
            .isTruncated(false)
            .build()
        every { s3Client.listObjectsV2(any<ListObjectsV2Request>()) } returns response

        assertEquals(300L, service.usedBytes())
    }
}
```

- [x] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "*S3StorageServiceTest"
```

Expected: FAIL — S3StorageService constructor doesn't accept these parameters yet.

- [x] **Step 3: Implement S3StorageService**

Replace the contents of `src/main/kotlin/org/sightech/memoryvault/storage/S3StorageService.kt`:

```kotlin
package org.sightech.memoryvault.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import java.io.InputStream

@Component
@Profile("aws")
class S3StorageService(
    private val s3Client: S3Client,
    @Value("\${memoryvault.storage.s3-bucket}")
    private val bucket: String
) : StorageService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun store(key: String, inputStream: InputStream): String {
        val bytes = inputStream.readAllBytes()
        s3Client.putObject(
            PutObjectRequest.builder().bucket(bucket).key(key).build(),
            RequestBody.fromBytes(bytes)
        )
        log.info("Stored object: s3://{}/{} ({} bytes)", bucket, key, bytes.size)
        return key
    }

    override fun retrieve(key: String): InputStream {
        return s3Client.getObject(
            GetObjectRequest.builder().bucket(bucket).key(key).build()
        )
    }

    override fun delete(key: String) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder().bucket(bucket).key(key).build()
        )
        log.info("Deleted object: s3://{}/{}", bucket, key)
    }

    override fun exists(key: String): Boolean {
        return try {
            s3Client.headObject(
                HeadObjectRequest.builder().bucket(bucket).key(key).build()
            )
            true
        } catch (e: NoSuchKeyException) {
            false
        }
    }

    override fun usedBytes(): Long {
        var totalSize = 0L
        var continuationToken: String? = null
        do {
            val request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .apply { continuationToken?.let { continuationToken(it) } }
                .build()
            val response = s3Client.listObjectsV2(request)
            totalSize += response.contents().sumOf { it.size() }
            continuationToken = if (response.isTruncated()) response.nextContinuationToken() else null
        } while (continuationToken != null)
        return totalSize
    }
}
```

- [x] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "*S3StorageServiceTest"
```

Expected: PASS (all 6 tests)

- [x] **Step 5: Run all tests to verify nothing is broken**

```bash
./gradlew test
```

Expected: All tests pass. Existing tests use `@ActiveProfiles("test")` which loads `LocalStorageService` via `@Profile("local | test")`, so S3StorageService is never activated in tests.

- [x] **Step 6: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/storage/S3StorageService.kt src/test/kotlin/org/sightech/memoryvault/storage/S3StorageServiceTest.kt && git commit -m "feat: implement S3StorageService with AWS SDK v2"
```

---

### Task 4: CloudWatchLogService Implementation

Replace the stub with a working implementation using CloudWatch Logs Insights.

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/logging/CloudWatchLogService.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/logging/CloudWatchLogServiceTest.kt`

- [x] **Step 1: Write the failing tests**

Create `src/test/kotlin/org/sightech/memoryvault/logging/CloudWatchLogServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.logging

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloudWatchLogServiceTest {

    private val cwClient = mockk<CloudWatchLogsClient>()
    private lateinit var service: CloudWatchLogService

    @BeforeEach
    fun setUp() {
        service = CloudWatchLogService(cwClient, "/memoryvault/app")
    }

    @Test
    fun `getLogs returns parsed log entries`() {
        val queryId = "test-query-id"
        every { cwClient.startQuery(any<StartQueryRequest>()) } returns
            StartQueryResponse.builder().queryId(queryId).build()

        val resultField1 = ResultField.builder().field("@timestamp").value("2026-04-05T10:00:00.000Z").build()
        val resultField2 = ResultField.builder().field("level").value("INFO").build()
        val resultField3 = ResultField.builder().field("logger").value("o.s.m.feed.FeedService").build()
        val resultField4 = ResultField.builder().field("message").value("Feed synced").build()
        val resultField5 = ResultField.builder().field("thread").value("main").build()

        every { cwClient.getQueryResults(any<GetQueryResultsRequest>()) } returns
            GetQueryResultsResponse.builder()
                .status(QueryStatus.COMPLETE)
                .results(listOf(listOf(resultField1, resultField2, resultField3, resultField4, resultField5)))
                .build()

        val logs = service.getLogs(null, null, 10)
        assertEquals(1, logs.size)
        assertEquals("INFO", logs[0].level)
        assertEquals("Feed synced", logs[0].message)
        assertEquals("o.s.m.feed.FeedService", logs[0].logger)
    }

    @Test
    fun `getLogs filters by level in query`() {
        val queryId = "test-query-id"
        every { cwClient.startQuery(any<StartQueryRequest>()) } returns
            StartQueryResponse.builder().queryId(queryId).build()
        every { cwClient.getQueryResults(any<GetQueryResultsRequest>()) } returns
            GetQueryResultsResponse.builder()
                .status(QueryStatus.COMPLETE)
                .results(emptyList())
                .build()

        service.getLogs("ERROR", null, 10)

        // Verify the query was started (filtering happens in the Insights query)
        io.mockk.verify {
            cwClient.startQuery(match<StartQueryRequest> {
                it.queryString().contains("ERROR")
            })
        }
    }

    @Test
    fun `getLogs filters by logger in query`() {
        val queryId = "test-query-id"
        every { cwClient.startQuery(any<StartQueryRequest>()) } returns
            StartQueryResponse.builder().queryId(queryId).build()
        every { cwClient.getQueryResults(any<GetQueryResultsRequest>()) } returns
            GetQueryResultsResponse.builder()
                .status(QueryStatus.COMPLETE)
                .results(emptyList())
                .build()

        service.getLogs(null, "FeedService", 10)

        io.mockk.verify {
            cwClient.startQuery(match<StartQueryRequest> {
                it.queryString().contains("FeedService")
            })
        }
    }

    @Test
    fun `getLogs returns empty list when no results`() {
        every { cwClient.startQuery(any<StartQueryRequest>()) } returns
            StartQueryResponse.builder().queryId("q1").build()
        every { cwClient.getQueryResults(any<GetQueryResultsRequest>()) } returns
            GetQueryResultsResponse.builder()
                .status(QueryStatus.COMPLETE)
                .results(emptyList())
                .build()

        val logs = service.getLogs(null, null, null)
        assertTrue(logs.isEmpty())
    }

    @Test
    fun `getLogs polls until query completes`() {
        every { cwClient.startQuery(any<StartQueryRequest>()) } returns
            StartQueryResponse.builder().queryId("q1").build()
        every { cwClient.getQueryResults(any<GetQueryResultsRequest>()) } returnsMany listOf(
            GetQueryResultsResponse.builder().status(QueryStatus.RUNNING).results(emptyList()).build(),
            GetQueryResultsResponse.builder().status(QueryStatus.RUNNING).results(emptyList()).build(),
            GetQueryResultsResponse.builder().status(QueryStatus.COMPLETE).results(emptyList()).build()
        )

        val logs = service.getLogs(null, null, 10)
        assertTrue(logs.isEmpty())

        io.mockk.verify(exactly = 3) { cwClient.getQueryResults(any<GetQueryResultsRequest>()) }
    }
}
```

- [x] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "*CloudWatchLogServiceTest"
```

Expected: FAIL — CloudWatchLogService constructor doesn't accept these parameters yet.

- [x] **Step 3: Implement CloudWatchLogService**

Replace the contents of `src/main/kotlin/org/sightech/memoryvault/logging/CloudWatchLogService.kt`:

```kotlin
package org.sightech.memoryvault.logging

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient
import software.amazon.awssdk.services.cloudwatchlogs.model.*
import java.time.Instant

@Component
@Profile("aws")
class CloudWatchLogService(
    private val cwClient: CloudWatchLogsClient,
    @Value("\${memoryvault.logging.cloudwatch-log-group}")
    private val logGroupName: String
) : LogService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getLogs(level: String?, logger: String?, limit: Int?): List<LogEntry> {
        val effectiveLimit = limit ?: 50
        val query = buildQuery(level, logger, effectiveLimit)

        val now = Instant.now()
        val startQueryResponse = cwClient.startQuery(
            StartQueryRequest.builder()
                .logGroupName(logGroupName)
                .startTime(now.minusSeconds(86400).epochSecond)
                .endTime(now.epochSecond)
                .queryString(query)
                .build()
        )

        val queryId = startQueryResponse.queryId()
        return pollForResults(queryId)
    }

    private fun buildQuery(level: String?, logger: String?, limit: Int): String {
        val filters = mutableListOf<String>()
        if (level != null) filters.add("level = '$level'")
        if (logger != null) filters.add("logger like /$logger/")

        val filterClause = if (filters.isNotEmpty()) "| filter ${filters.joinToString(" and ")}" else ""
        return "fields @timestamp, level, logger, message, thread $filterClause | sort @timestamp desc | limit $limit"
    }

    private fun pollForResults(queryId: String): List<LogEntry> {
        var attempts = 0
        while (attempts < 30) {
            val response = cwClient.getQueryResults(
                GetQueryResultsRequest.builder().queryId(queryId).build()
            )
            when (response.status()) {
                QueryStatus.COMPLETE -> return parseResults(response.results())
                QueryStatus.FAILED, QueryStatus.CANCELLED -> {
                    log.warn("CloudWatch query {} ended with status {}", queryId, response.status())
                    return emptyList()
                }
                else -> {
                    attempts++
                    Thread.sleep(200)
                }
            }
        }
        log.warn("CloudWatch query {} timed out after 30 attempts", queryId)
        return emptyList()
    }

    private fun parseResults(results: List<List<ResultField>>): List<LogEntry> {
        return results.mapNotNull { row ->
            try {
                val fields = row.associate { it.field() to it.value() }
                LogEntry(
                    timestamp = Instant.parse(fields["@timestamp"]!!),
                    level = fields["level"] ?: "UNKNOWN",
                    logger = fields["logger"] ?: "",
                    message = fields["message"] ?: "",
                    thread = fields["thread"] ?: ""
                )
            } catch (e: Exception) {
                log.debug("Failed to parse CloudWatch log row: {}", e.message)
                null
            }
        }
    }
}
```

- [x] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "*CloudWatchLogServiceTest"
```

Expected: PASS (all 5 tests)

- [x] **Step 5: Run all tests**

```bash
./gradlew test
```

Expected: All tests pass.

- [x] **Step 6: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/logging/CloudWatchLogService.kt src/test/kotlin/org/sightech/memoryvault/logging/CloudWatchLogServiceTest.kt && git commit -m "feat: implement CloudWatchLogService with Logs Insights queries"
```

---

### Task 5: Add AWS Config Properties

Add the AWS-specific config properties to `application-prod.properties` so they're available when running with the `aws` profile.

**Files:**
- Modify: `src/main/resources/application-prod.properties`

- [x] **Step 1: Read current application-prod.properties**

Check what's already in the file.

- [x] **Step 2: Add AWS config properties**

Append to `src/main/resources/application-prod.properties`:

```properties
# AWS S3 storage
memoryvault.storage.s3-bucket=${MEMORYVAULT_STORAGE_S3__BUCKET}
memoryvault.storage.s3-region=${MEMORYVAULT_STORAGE_S3__REGION:us-east-1}

# AWS CloudWatch Logs
memoryvault.logging.cloudwatch-log-group=${MEMORYVAULT_LOGGING_CLOUDWATCH__LOG__GROUP:/memoryvault/app}
memoryvault.logging.cloudwatch-region=${MEMORYVAULT_LOGGING_CLOUDWATCH__REGION:us-east-1}
```

- [x] **Step 3: Run all tests**

```bash
./gradlew test
```

Expected: All tests pass. Test profile doesn't load prod properties.

- [x] **Step 4: Commit**

```bash
git add src/main/resources/application-prod.properties && git commit -m "feat: add AWS S3 and CloudWatch config properties"
```

---

### Task 6: Update Design Spec

Mark 9C as complete in the design spec.

**Files:**
- Modify: `docs/plans/2026-04-03-phase-9-infrastructure-design.md`

- [x] **Step 1: Mark 9C complete**

Change the `## 9C: AWS Service Implementations` heading to `## 9C: AWS Service Implementations ✅`

- [x] **Step 2: Commit**

```bash
git add docs/plans/2026-04-03-phase-9-infrastructure-design.md && git commit -m "docs: mark Phase 9C as complete"
```

---

## Summary Table

| Task  | Description                                                 | Key Files                                                        |
|-------|-------------------------------------------------------------|------------------------------------------------------------------|
| 1     | Add AWS SDK v2 dependencies                                 | `build.gradle.kts`                                               |
| 2     | AWS SDK config class (S3Client, CloudWatchLogsClient beans) | `config/AwsConfig.kt`                                            |
| 3     | S3StorageService implementation                             | `storage/S3StorageService.kt`, `S3StorageServiceTest.kt`         |
| 4     | CloudWatchLogService implementation                         | `logging/CloudWatchLogService.kt`, `CloudWatchLogServiceTest.kt` |
| 5     | AWS config properties in application-prod.properties        | `application-prod.properties`                                    |
| 6     | Update design spec                                          | `phase-9-infrastructure-design.md`                               |
