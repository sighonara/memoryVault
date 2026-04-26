# Phase 10 — Cross-Platform Video Backup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically back up archived YouTube videos to the Internet Archive (primary) with a pluggable secondary provider for failover, including health monitoring, throttled sync jobs, and backup status UI.

**Architecture:** Internet Archive is the built-in primary target. A `BackupProvider` interface supports secondary providers. Two scheduled sync jobs handle uploads (throttled) and health checks (daily). `EncryptionService` in a shared `crypto/` package encrypts provider credentials at rest. The existing `SyncJob` audit trail, `ApplicationEvent` system, and WebSocket relay are reused for observability.

**Tech Stack:** Spring Boot 4.x, Kotlin, Spring Security Crypto (AES-256-GCM), Spring Data JPA, Flyway, Angular 21 + Apollo + NgRx Signal Store, Internet Archive S3-like API.

**Spec:** `docs/plans/2026-04-23-phase-10-cross-platform-video-backup-design.md`

---

## File Structure

### New Files — Backend

| File | Responsibility |
|------|---------------|
| `src/main/resources/db/migration/V9__backup_tables.sql` | Migration: `backup_providers` + `backup_records` tables |
| `src/main/kotlin/.../crypto/EncryptionService.kt` | AES-256-GCM encrypt/decrypt via Spring Security Crypto `TextEncryptor` |
| `src/main/kotlin/.../backup/entity/BackupEnums.kt` | `BackupProviderType` + `BackupStatus` enums |
| `src/main/kotlin/.../backup/entity/BackupProviderEntity.kt` | JPA entity for backup providers |
| `src/main/kotlin/.../backup/entity/BackupRecord.kt` | JPA entity for per-video backup records |
| `src/main/kotlin/.../backup/repository/BackupProviderRepository.kt` | Spring Data repository for providers |
| `src/main/kotlin/.../backup/repository/BackupRecordRepository.kt` | Spring Data repository for backup records |
| `src/main/kotlin/.../backup/provider/BackupProvider.kt` | Provider interface (search, upload, checkHealth, delete) |
| `src/main/kotlin/.../backup/provider/InternetArchiveProvider.kt` | IA implementation using S3-like API |
| `src/main/kotlin/.../backup/provider/BackupProviderFactory.kt` | Resolves `BackupProviderType` → `BackupProvider` implementation |
| `src/main/kotlin/.../backup/service/BackupService.kt` | Core backup orchestration (create records, trigger uploads, backfill) |
| `src/main/kotlin/.../backup/service/BackupHealthCheckService.kt` | Health check logic (consecutive failures → LOST → failover) |
| `src/main/kotlin/.../backup/BackupSyncRegistrar.kt` | Registers upload + health check jobs on `ApplicationReadyEvent` |
| `src/main/kotlin/.../graphql/BackupResolver.kt` | GraphQL queries + mutations for backup providers and stats |
| `src/main/kotlin/.../mcp/BackupTools.kt` | MCP tools: getBackupStatus, listBackupProviders, triggerBackfill |
| `src/main/resources/graphql/backup.graphqls` | GraphQL types + schema additions for backup |
| `src/test/kotlin/.../crypto/EncryptionServiceTest.kt` | Unit tests for encrypt/decrypt |
| `src/test/kotlin/.../backup/service/BackupServiceTest.kt` | Unit tests for backup orchestration |
| `src/test/kotlin/.../backup/service/BackupHealthCheckServiceTest.kt` | Unit tests for health check logic |
| `src/test/kotlin/.../backup/provider/InternetArchiveProviderTest.kt` | Unit tests for IA provider (mocked HTTP) |

### New Files — Frontend

| File | Responsibility |
|------|---------------|
| `client/src/app/youtube/backup.graphql` | GraphQL operations for backup queries/mutations |
| `client/src/app/admin/backup-panel/backup-panel.ts` | Admin backup providers panel component |
| `client/src/app/admin/backup-panel/index.ts` | Barrel export |

### Modified Files

| File | Change |
|------|--------|
| `src/main/kotlin/.../scheduling/entity/SyncJob.kt` | Add `BACKUP_UPLOAD`, `BACKUP_HEALTH_CHECK` to `JobType` enum |
| `src/main/kotlin/.../websocket/VaultEvent.kt` | Add `BACKUP_LOST` event type + `BackupLost` data class |
| `src/main/kotlin/.../websocket/WebSocketEventRelay.kt` | Add `onBackupLost` event listener |
| `src/main/resources/graphql/schema.graphqls` | Add backup queries + mutations |
| `src/main/resources/graphql/youtube.graphqls` | Add `backupStatus: String` to `Video` type |
| `src/main/resources/application.properties` | Add backup config properties |
| `.env.sample` | Add `MEMORYVAULT_ENCRYPTION_KEY` |
| `client/src/app/youtube/youtube.graphql` | Add `backupStatus` to `GetVideos` query |
| `client/src/app/youtube/youtube.ts` | Add shield icons + legend for backup status |
| `client/src/app/youtube/youtube.store.ts` | No store changes needed (backupStatus comes on Video type) |
| `client/src/app/admin/admin.ts` | Add Backup Providers tab |
| `client/src/app/admin/admin.graphql` | Add backup provider queries/mutations |
| `client/src/app/admin/admin.store.ts` | Add backup state + methods |

All paths below use `...` as shorthand for `src/main/kotlin/org/sightech/memoryvault` (source) or `src/test/kotlin/org/sightech/memoryvault` (test).

---

## Task 1: Database Migration + Enums + Configuration

**Files:**
- Create: `src/main/resources/db/migration/V9__backup_tables.sql`
- Create: `.../backup/entity/BackupEnums.kt`
- Modify: `.../scheduling/entity/SyncJob.kt`
- Modify: `src/main/resources/application.properties`
- Modify: `.env.sample`

- [x] **Step 1: Create V9 migration**

Create `src/main/resources/db/migration/V9__backup_tables.sql`:

```sql
CREATE TABLE backup_providers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('INTERNET_ARCHIVE', 'CUSTOM')),
    name VARCHAR(100) NOT NULL,
    credentials_encrypted TEXT NOT NULL,
    config JSONB DEFAULT '{}',
    is_primary BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE backup_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id UUID NOT NULL REFERENCES videos(id),
    provider_id UUID NOT NULL REFERENCES backup_providers(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'UPLOADING', 'BACKED_UP', 'LOST', 'FAILED')),
    external_url VARCHAR(2048),
    external_id VARCHAR(255),
    error_message TEXT,
    last_health_check_at TIMESTAMPTZ,
    health_check_failures INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_backup_records_video_id ON backup_records(video_id);
CREATE INDEX idx_backup_records_provider_id ON backup_records(provider_id);
CREATE INDEX idx_backup_records_status ON backup_records(status);
CREATE INDEX idx_backup_providers_user_id ON backup_providers(user_id);
```

- [x] **Step 2: Create BackupEnums.kt**

Create `src/main/kotlin/org/sightech/memoryvault/backup/entity/BackupEnums.kt`:

```kotlin
package org.sightech.memoryvault.backup.entity

enum class BackupProviderType { INTERNET_ARCHIVE, CUSTOM }

enum class BackupStatus { PENDING, UPLOADING, BACKED_UP, LOST, FAILED }
```

- [x] **Step 3: Add new job types to SyncJob.kt**

In `src/main/kotlin/org/sightech/memoryvault/scheduling/entity/SyncJob.kt`, change:

```kotlin
enum class JobType { RSS_FETCH, YT_SYNC, BOOKMARK_ARCHIVE }
```

to:

```kotlin
enum class JobType { RSS_FETCH, YT_SYNC, BOOKMARK_ARCHIVE, BACKUP_UPLOAD, BACKUP_HEALTH_CHECK }
```

- [x] **Step 4: Add backup config properties**

Append to `src/main/resources/application.properties`:

```properties
# Backup sync schedules (cron syntax). Set to "-" to disable.
memoryvault.backup.upload-cron=-
memoryvault.backup.health-check-cron=-
memoryvault.backup.max-uploads-per-day=10
memoryvault.backup.health-check-failure-threshold=3
```

- [x] **Step 5: Update .env.sample**

Append to `.env.sample`:

```
# Encryption key for backup provider credentials (AES-256-GCM).
# Generate with: openssl rand -hex 16
MEMORYVAULT_ENCRYPTION_KEY=
```

- [x] **Step 6: Verify migration runs**

Run: `./gradlew test --tests "*MemoryVaultApplicationTests*"`

Expected: PASS (Flyway applies V9 migration against TestContainers PostgreSQL)

- [x] **Step 7: Commit**

```bash
git add src/main/resources/db/migration/V9__backup_tables.sql src/main/kotlin/org/sightech/memoryvault/backup/entity/BackupEnums.kt src/main/kotlin/org/sightech/memoryvault/scheduling/entity/SyncJob.kt src/main/resources/application.properties .env.sample
git commit -m "feat(backup): V9 migration, backup enums, job types, and config properties"
```

---

## Task 2: EncryptionService (shared crypto package)

**Files:**
- Create: `.../crypto/EncryptionService.kt`
- Create: `src/test/kotlin/.../crypto/EncryptionServiceTest.kt`

- [x] **Step 1: Write failing tests**

Create `src/test/kotlin/org/sightech/memoryvault/crypto/EncryptionServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.crypto

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class EncryptionServiceTest {

    private val service = EncryptionService("deadbeefdeadbeefdeadbeefdeadbeef")

    @Test
    fun `encrypt and decrypt round-trips`() {
        val plaintext = """{"accessKey": "abc123", "secretKey": "xyz789"}"""
        val encrypted = service.encrypt(plaintext)
        assertNotEquals(plaintext, encrypted)
        assertEquals(plaintext, service.decrypt(encrypted))
    }

    @Test
    fun `different plaintexts produce different ciphertexts`() {
        val a = service.encrypt("secret-a")
        val b = service.encrypt("secret-b")
        assertNotEquals(a, b)
    }

    @Test
    fun `decrypt with wrong key throws`() {
        val encrypted = service.encrypt("my-secret")
        val otherService = EncryptionService("aaaabbbbccccddddaaaabbbbccccdddd")
        assertThrows<Exception> {
            otherService.decrypt(encrypted)
        }
    }

    @Test
    fun `empty string round-trips`() {
        val encrypted = service.encrypt("")
        assertEquals("", service.decrypt(encrypted))
    }
}
```

- [x] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*EncryptionServiceTest*"`

Expected: FAIL (class not found)

- [x] **Step 3: Implement EncryptionService**

Create `src/main/kotlin/org/sightech/memoryvault/crypto/EncryptionService.kt`:

```kotlin
package org.sightech.memoryvault.crypto

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.encrypt.Encryptors
import org.springframework.stereotype.Service

@Service
class EncryptionService(
    @Value("\${MEMORYVAULT_ENCRYPTION_KEY:default-dev-key-do-not-use}") private val encryptionKey: String
) {

    private val salt = "memoryvault"

    fun encrypt(plaintext: String): String {
        val encryptor = Encryptors.text(encryptionKey, bytesToHex(salt.toByteArray()))
        return encryptor.encrypt(plaintext)
    }

    fun decrypt(ciphertext: String): String {
        val encryptor = Encryptors.text(encryptionKey, bytesToHex(salt.toByteArray()))
        return encryptor.decrypt(ciphertext)
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}
```

- [x] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "*EncryptionServiceTest*"`

Expected: PASS

- [x] **Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/crypto/EncryptionService.kt src/test/kotlin/org/sightech/memoryvault/crypto/EncryptionServiceTest.kt
git commit -m "feat(crypto): add shared EncryptionService with AES-256-GCM"
```

---

## Task 3: Backup Entity Classes + Repositories

**Files:**
- Create: `.../backup/entity/BackupProviderEntity.kt`
- Create: `.../backup/entity/BackupRecord.kt`
- Create: `.../backup/repository/BackupProviderRepository.kt`
- Create: `.../backup/repository/BackupRecordRepository.kt`

- [x] **Step 1: Create BackupProviderEntity**

Create `src/main/kotlin/org/sightech/memoryvault/backup/entity/BackupProviderEntity.kt`:

```kotlin
package org.sightech.memoryvault.backup.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "backup_providers")
class BackupProviderEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: BackupProviderType,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "credentials_encrypted", nullable = false, columnDefinition = "TEXT")
    var credentialsEncrypted: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var config: String? = "{}",

    @Column(name = "is_primary", nullable = false)
    var isPrimary: Boolean = true,

    val createdAt: Instant = Instant.now(),

    var updatedAt: Instant = Instant.now(),

    var deletedAt: Instant? = null,

    @Version
    val version: Long = 0
)
```

- [x] **Step 2: Create BackupRecord**

Create `src/main/kotlin/org/sightech/memoryvault/backup/entity/BackupRecord.kt`:

```kotlin
package org.sightech.memoryvault.backup.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "backup_records")
class BackupRecord(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "video_id", nullable = false)
    val videoId: UUID,

    @Column(name = "provider_id", nullable = false)
    val providerId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: BackupStatus = BackupStatus.PENDING,

    @Column(name = "external_url", length = 2048)
    var externalUrl: String? = null,

    @Column(name = "external_id", length = 255)
    var externalId: String? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "last_health_check_at")
    var lastHealthCheckAt: Instant? = null,

    @Column(name = "health_check_failures", nullable = false)
    var healthCheckFailures: Int = 0,

    val createdAt: Instant = Instant.now(),

    var updatedAt: Instant = Instant.now()
)
```

- [x] **Step 3: Create BackupProviderRepository**

Create `src/main/kotlin/org/sightech/memoryvault/backup/repository/BackupProviderRepository.kt`:

```kotlin
package org.sightech.memoryvault.backup.repository

import org.sightech.memoryvault.backup.entity.BackupProviderEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BackupProviderRepository : JpaRepository<BackupProviderEntity, UUID> {

    @Query("SELECT p FROM BackupProviderEntity p WHERE p.userId = :userId AND p.deletedAt IS NULL")
    fun findAllActiveByUserId(userId: UUID): List<BackupProviderEntity>

    @Query("SELECT p FROM BackupProviderEntity p WHERE p.id = :id AND p.userId = :userId AND p.deletedAt IS NULL")
    fun findActiveByIdAndUserId(id: UUID, userId: UUID): BackupProviderEntity?

    @Query("SELECT p FROM BackupProviderEntity p WHERE p.userId = :userId AND p.isPrimary = true AND p.deletedAt IS NULL")
    fun findPrimaryByUserId(userId: UUID): BackupProviderEntity?

    @Query("SELECT p FROM BackupProviderEntity p WHERE p.deletedAt IS NULL")
    fun findAllActive(): List<BackupProviderEntity>
}
```

- [x] **Step 4: Create BackupRecordRepository**

Create `src/main/kotlin/org/sightech/memoryvault/backup/repository/BackupRecordRepository.kt`:

```kotlin
package org.sightech.memoryvault.backup.repository

import org.sightech.memoryvault.backup.entity.BackupRecord
import org.sightech.memoryvault.backup.entity.BackupStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BackupRecordRepository : JpaRepository<BackupRecord, UUID> {

    fun findByVideoId(videoId: UUID): List<BackupRecord>

    fun findByVideoIdAndProviderId(videoId: UUID, providerId: UUID): BackupRecord?

    @Query("SELECT r FROM BackupRecord r WHERE r.status = :status ORDER BY r.createdAt ASC")
    fun findByStatus(status: BackupStatus): List<BackupRecord>

    @Query("SELECT r FROM BackupRecord r WHERE r.status = 'BACKED_UP'")
    fun findAllBackedUp(): List<BackupRecord>

    @Query("SELECT COUNT(r) FROM BackupRecord r WHERE r.providerId IN (SELECT p.id FROM BackupProviderEntity p WHERE p.userId = :userId AND p.deletedAt IS NULL)")
    fun countByUserId(userId: UUID): Long

    @Query("SELECT COUNT(r) FROM BackupRecord r WHERE r.status = :status AND r.providerId IN (SELECT p.id FROM BackupProviderEntity p WHERE p.userId = :userId AND p.deletedAt IS NULL)")
    fun countByUserIdAndStatus(userId: UUID, status: BackupStatus): Long

    @Query("SELECT r.videoId FROM BackupRecord r WHERE r.providerId = :providerId")
    fun findVideoIdsByProviderId(providerId: UUID): List<UUID>
}
```

- [x] **Step 5: Verify entities compile against migration**

Run: `./gradlew test --tests "*MemoryVaultApplicationTests*"`

Expected: PASS (Hibernate validates entity mappings against V9 schema)

- [x] **Step 6: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/backup/entity/ src/main/kotlin/org/sightech/memoryvault/backup/repository/
git commit -m "feat(backup): add BackupProviderEntity, BackupRecord entities and repositories"
```

---

## Task 4: BackupProvider Interface + InternetArchiveProvider

**Files:**
- Create: `.../backup/provider/BackupProvider.kt`
- Create: `.../backup/provider/InternetArchiveProvider.kt`
- Create: `.../backup/provider/BackupProviderFactory.kt`
- Create: `src/test/kotlin/.../backup/provider/InternetArchiveProviderTest.kt`

- [x] **Step 1: Create BackupProvider interface**

Create `src/main/kotlin/org/sightech/memoryvault/backup/provider/BackupProvider.kt`:

```kotlin
package org.sightech.memoryvault.backup.provider

import java.io.InputStream

data class BackupSearchResult(
    val externalId: String,
    val externalUrl: String
)

data class BackupUploadResult(
    val externalId: String,
    val externalUrl: String
)

data class VideoBackupMetadata(
    val youtubeVideoId: String,
    val title: String?,
    val description: String?,
    val youtubeUrl: String
)

interface BackupProvider {
    fun search(youtubeVideoId: String): BackupSearchResult?
    fun upload(videoFile: InputStream, metadata: VideoBackupMetadata): BackupUploadResult
    fun checkHealth(externalUrl: String): Boolean
    fun delete(externalId: String): Boolean
}
```

- [x] **Step 2: Write failing tests for InternetArchiveProvider**

Create `src/test/kotlin/org/sightech/memoryvault/backup/provider/InternetArchiveProviderTest.kt`:

```kotlin
package org.sightech.memoryvault.backup.provider

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InternetArchiveProviderTest {

    @Test
    fun `itemIdentifier formats youtube video id correctly`() {
        val provider = InternetArchiveProvider("test-access", "test-secret")
        assertEquals("yt-dQw4w9WgXcQ", provider.itemIdentifier("dQw4w9WgXcQ"))
    }

    @Test
    fun `buildMetadataHeaders includes required fields`() {
        val provider = InternetArchiveProvider("test-access", "test-secret")
        val metadata = VideoBackupMetadata(
            youtubeVideoId = "dQw4w9WgXcQ",
            title = "Test Video",
            description = "A description",
            youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        )
        val headers = provider.buildMetadataHeaders(metadata)

        assertEquals("movies", headers["x-archive-meta-mediatype"])
        assertEquals("Test Video", headers["x-archive-meta-title"])
        assertEquals("dQw4w9WgXcQ", headers["x-archive-meta-youtube-video-id"])
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", headers["x-archive-meta-youtube-url"])
        assertEquals("memoryvault", headers["x-archive-meta-archived-by"])
        assertNotNull(headers["x-archive-meta-description"])
    }

    @Test
    fun `buildMetadataHeaders handles null title and description`() {
        val provider = InternetArchiveProvider("test-access", "test-secret")
        val metadata = VideoBackupMetadata(
            youtubeVideoId = "abc123",
            title = null,
            description = null,
            youtubeUrl = "https://www.youtube.com/watch?v=abc123"
        )
        val headers = provider.buildMetadataHeaders(metadata)

        assertNull(headers["x-archive-meta-title"])
        assertNull(headers["x-archive-meta-description"])
        assertEquals("abc123", headers["x-archive-meta-youtube-video-id"])
    }

    @Test
    fun `externalUrl formats correctly`() {
        val provider = InternetArchiveProvider("test-access", "test-secret")
        assertEquals("https://archive.org/details/yt-abc123", provider.externalUrl("abc123"))
    }
}
```

- [x] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests "*InternetArchiveProviderTest*"`

Expected: FAIL (class not found)

- [x] **Step 4: Implement InternetArchiveProvider**

Create `src/main/kotlin/org/sightech/memoryvault/backup/provider/InternetArchiveProvider.kt`:

```kotlin
package org.sightech.memoryvault.backup.provider

import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class InternetArchiveProvider(
    private val accessKey: String,
    private val secretKey: String
) : BackupProvider {

    private val log = LoggerFactory.getLogger(javaClass)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    fun itemIdentifier(youtubeVideoId: String): String = "yt-$youtubeVideoId"

    fun externalUrl(youtubeVideoId: String): String =
        "https://archive.org/details/${itemIdentifier(youtubeVideoId)}"

    fun buildMetadataHeaders(metadata: VideoBackupMetadata): Map<String, String?> {
        val headers = mutableMapOf<String, String?>()
        headers["x-archive-meta-mediatype"] = "movies"
        headers["x-archive-meta-title"] = metadata.title
        headers["x-archive-meta-description"] = metadata.description
        headers["x-archive-meta-youtube-video-id"] = metadata.youtubeVideoId
        headers["x-archive-meta-youtube-url"] = metadata.youtubeUrl
        headers["x-archive-meta-archived-by"] = "memoryvault"
        return headers
    }

    override fun search(youtubeVideoId: String): BackupSearchResult? {
        val query = "youtube-video-id:$youtubeVideoId"
        val url = "https://archive.org/advancedsearch.php?q=${java.net.URLEncoder.encode(query, Charsets.UTF_8)}&output=json&rows=1"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                log.warn("IA search returned status {} for videoId={}", response.statusCode(), youtubeVideoId)
                return null
            }
            val body = response.body()
            if (body.contains("\"numFound\":0") || body.contains("\"numFound\": 0")) {
                return null
            }
            val identifierRegex = """"identifier"\s*:\s*"([^"]+)"""".toRegex()
            val match = identifierRegex.find(body) ?: return null
            val identifier = match.groupValues[1]
            BackupSearchResult(
                externalId = identifier,
                externalUrl = "https://archive.org/details/$identifier"
            )
        } catch (e: Exception) {
            log.warn("IA search failed for videoId={}: {}", youtubeVideoId, e.message)
            null
        }
    }

    override fun upload(videoFile: InputStream, metadata: VideoBackupMetadata): BackupUploadResult {
        val identifier = itemIdentifier(metadata.youtubeVideoId)
        val url = "https://s3.us.archive.org/$identifier/${metadata.youtubeVideoId}.mp4"

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMinutes(30))
            .header("Authorization", "LOW $accessKey:$secretKey")
            .header("x-archive-auto-make-bucket", "1")

        val metadataHeaders = buildMetadataHeaders(metadata)
        for ((key, value) in metadataHeaders) {
            if (value != null) {
                requestBuilder.header(key, value)
            }
        }

        val request = requestBuilder
            .PUT(HttpRequest.BodyPublishers.ofInputStream { videoFile })
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("IA upload failed: HTTP ${response.statusCode()} — ${response.body()}")
        }

        log.info("Uploaded to IA: identifier={}", identifier)
        return BackupUploadResult(
            externalId = identifier,
            externalUrl = externalUrl(metadata.youtubeVideoId)
        )
    }

    override fun checkHealth(externalUrl: String): Boolean {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(externalUrl))
            .timeout(Duration.ofSeconds(15))
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            response.statusCode() in 200..399
        } catch (e: Exception) {
            log.warn("Health check failed for {}: {}", externalUrl, e.message)
            false
        }
    }

    override fun delete(externalId: String): Boolean {
        log.warn("Delete not supported on Internet Archive (by design). externalId={}", externalId)
        return false
    }
}
```

- [x] **Step 5: Create BackupProviderFactory**

Create `src/main/kotlin/org/sightech/memoryvault/backup/provider/BackupProviderFactory.kt`:

```kotlin
package org.sightech.memoryvault.backup.provider

import org.sightech.memoryvault.backup.entity.BackupProviderEntity
import org.sightech.memoryvault.backup.entity.BackupProviderType
import org.sightech.memoryvault.crypto.EncryptionService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class BackupProviderFactory(
    private val encryptionService: EncryptionService,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun create(entity: BackupProviderEntity): BackupProvider {
        val credentialsJson = encryptionService.decrypt(entity.credentialsEncrypted)
        val creds = objectMapper.readTree(credentialsJson)

        return when (entity.type) {
            BackupProviderType.INTERNET_ARCHIVE -> {
                val accessKey = creds.get("accessKey")?.stringValue()
                    ?: throw IllegalStateException("Missing accessKey in provider credentials")
                val secretKey = creds.get("secretKey")?.stringValue()
                    ?: throw IllegalStateException("Missing secretKey in provider credentials")
                InternetArchiveProvider(accessKey, secretKey)
            }
            BackupProviderType.CUSTOM -> {
                throw UnsupportedOperationException("Custom backup providers are not yet implemented")
            }
        }
    }
}
```

- [x] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests "*InternetArchiveProviderTest*"`

Expected: PASS

- [x] **Step 7: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/backup/provider/ src/test/kotlin/org/sightech/memoryvault/backup/provider/
git commit -m "feat(backup): add BackupProvider interface, InternetArchiveProvider, and factory"
```

---

## Task 5: BackupService (core orchestration)

**Files:**
- Create: `.../backup/service/BackupService.kt`
- Create: `src/test/kotlin/.../backup/service/BackupServiceTest.kt`

- [x] **Step 1: Write failing tests**

Create `src/test/kotlin/org/sightech/memoryvault/backup/service/BackupServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.backup.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.backup.entity.BackupProviderEntity
import org.sightech.memoryvault.backup.entity.BackupProviderType
import org.sightech.memoryvault.backup.entity.BackupRecord
import org.sightech.memoryvault.backup.entity.BackupStatus
import org.sightech.memoryvault.backup.provider.BackupProvider
import org.sightech.memoryvault.backup.provider.BackupProviderFactory
import org.sightech.memoryvault.backup.provider.BackupSearchResult
import org.sightech.memoryvault.backup.provider.BackupUploadResult
import org.sightech.memoryvault.backup.repository.BackupProviderRepository
import org.sightech.memoryvault.backup.repository.BackupRecordRepository
import org.sightech.memoryvault.crypto.EncryptionService
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.youtube.repository.VideoRepository
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.test.assertEquals

class BackupServiceTest {

    private val providerRepo = mockk<BackupProviderRepository>()
    private val recordRepo = mockk<BackupRecordRepository>()
    private val videoRepo = mockk<VideoRepository>()
    private val providerFactory = mockk<BackupProviderFactory>()
    private val storageService = mockk<StorageService>()
    private val encryptionService = mockk<EncryptionService>()

    private val service = BackupService(providerRepo, recordRepo, videoRepo, providerFactory, storageService, encryptionService)

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val providerId = UUID.randomUUID()
    private val videoId = UUID.randomUUID()

    private val providerEntity = BackupProviderEntity(
        id = providerId,
        userId = userId,
        type = BackupProviderType.INTERNET_ARCHIVE,
        name = "Internet Archive",
        credentialsEncrypted = "encrypted-creds"
    )

    @BeforeEach
    fun setUp() {
        every { recordRepo.save(any()) } answers { firstArg() }
        every { providerRepo.save(any()) } answers { firstArg() }
    }

    @Test
    fun `createPendingRecord creates a PENDING backup record`() {
        every { recordRepo.findByVideoIdAndProviderId(videoId, providerId) } returns null

        val record = service.createPendingRecord(videoId, providerId)

        assertEquals(BackupStatus.PENDING, record.status)
        assertEquals(videoId, record.videoId)
        assertEquals(providerId, record.providerId)
        verify { recordRepo.save(any()) }
    }

    @Test
    fun `createPendingRecord returns existing record if already present`() {
        val existing = BackupRecord(videoId = videoId, providerId = providerId, status = BackupStatus.BACKED_UP)
        every { recordRepo.findByVideoIdAndProviderId(videoId, providerId) } returns existing

        val record = service.createPendingRecord(videoId, providerId)

        assertEquals(BackupStatus.BACKED_UP, record.status)
        verify(exactly = 0) { recordRepo.save(any()) }
    }

    @Test
    fun `processUpload marks BACKED_UP when IA already has the video`() {
        val record = BackupRecord(videoId = videoId, providerId = providerId)
        val mockProvider = mockk<BackupProvider>()
        every { providerFactory.create(providerEntity) } returns mockProvider
        every { mockProvider.search("ytVid123") } returns BackupSearchResult("yt-ytVid123", "https://archive.org/details/yt-ytVid123")

        service.processUpload(record, providerEntity, "ytVid123", "videos/path/file.mp4")

        assertEquals(BackupStatus.BACKED_UP, record.status)
        assertEquals("https://archive.org/details/yt-ytVid123", record.externalUrl)
        verify(exactly = 0) { storageService.retrieve(any()) }
        verify { recordRepo.save(record) }
    }

    @Test
    fun `processUpload uploads and marks BACKED_UP when not found on IA`() {
        val record = BackupRecord(videoId = videoId, providerId = providerId)
        val mockProvider = mockk<BackupProvider>()
        every { providerFactory.create(providerEntity) } returns mockProvider
        every { mockProvider.search("ytVid123") } returns null
        every { storageService.retrieve("videos/path/file.mp4") } returns ByteArrayInputStream(byteArrayOf(1, 2, 3))
        every { mockProvider.upload(any(), any()) } returns BackupUploadResult("yt-ytVid123", "https://archive.org/details/yt-ytVid123")

        service.processUpload(record, providerEntity, "ytVid123", "videos/path/file.mp4")

        assertEquals(BackupStatus.BACKED_UP, record.status)
        assertEquals("yt-ytVid123", record.externalId)
        verify { storageService.retrieve("videos/path/file.mp4") }
        verify { recordRepo.save(record) }
    }

    @Test
    fun `processUpload marks FAILED on exception`() {
        val record = BackupRecord(videoId = videoId, providerId = providerId)
        val mockProvider = mockk<BackupProvider>()
        every { providerFactory.create(providerEntity) } returns mockProvider
        every { mockProvider.search("ytVid123") } throws RuntimeException("Network error")

        service.processUpload(record, providerEntity, "ytVid123", "videos/path/file.mp4")

        assertEquals(BackupStatus.FAILED, record.status)
        assertEquals("Network error", record.errorMessage)
        verify { recordRepo.save(record) }
    }

    @Test
    fun `addProvider encrypts credentials and saves`() {
        every { encryptionService.encrypt(any()) } returns "encrypted-json"

        val provider = service.addProvider(userId, BackupProviderType.INTERNET_ARCHIVE, "Internet Archive", """{"accessKey":"a","secretKey":"b"}""", true)

        assertEquals("encrypted-json", provider.credentialsEncrypted)
        assertEquals("Internet Archive", provider.name)
        verify { encryptionService.encrypt("""{"accessKey":"a","secretKey":"b"}""") }
        verify { providerRepo.save(any()) }
    }

    @Test
    fun `backfillCount returns count of downloaded videos without backup records`() {
        val allDownloaded = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        val alreadyBacked = listOf(allDownloaded[0])
        every { providerRepo.findPrimaryByUserId(userId) } returns providerEntity
        every { videoRepo.findDownloadedVideoIdsByUserId(userId) } returns allDownloaded
        every { recordRepo.findVideoIdsByProviderId(providerId) } returns alreadyBacked

        val count = service.backfillCount(userId)

        assertEquals(2, count)
    }
}
```

- [x] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*BackupServiceTest*"`

Expected: FAIL (class not found)

- [x] **Step 3: Add findDownloadedVideoIdsByUserId to VideoRepository**

In `src/main/kotlin/org/sightech/memoryvault/youtube/repository/VideoRepository.kt`, add this method to the interface:

```kotlin
@Query("SELECT v.id FROM Video v WHERE v.youtubeList.userId = :userId AND v.youtubeList.deletedAt IS NULL AND v.downloadedAt IS NOT NULL")
fun findDownloadedVideoIdsByUserId(userId: UUID): List<UUID>
```

- [x] **Step 4: Add retrieve method to StorageService if needed**

Check if `StorageService` already has a `retrieve` method. If not, add to the interface at `src/main/kotlin/org/sightech/memoryvault/storage/StorageService.kt`:

```kotlin
fun retrieve(key: String): InputStream
```

And implement in `LocalStorageService`:

```kotlin
override fun retrieve(key: String): InputStream {
    val path = resolve(key)
    return Files.newInputStream(path)
}
```

- [x] **Step 5: Implement BackupService**

Create `src/main/kotlin/org/sightech/memoryvault/backup/service/BackupService.kt`:

```kotlin
package org.sightech.memoryvault.backup.service

import org.sightech.memoryvault.backup.entity.*
import org.sightech.memoryvault.backup.provider.BackupProviderFactory
import org.sightech.memoryvault.backup.provider.VideoBackupMetadata
import org.sightech.memoryvault.backup.repository.BackupProviderRepository
import org.sightech.memoryvault.backup.repository.BackupRecordRepository
import org.sightech.memoryvault.crypto.EncryptionService
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class BackupService(
    private val providerRepo: BackupProviderRepository,
    private val recordRepo: BackupRecordRepository,
    private val videoRepo: VideoRepository,
    private val providerFactory: BackupProviderFactory,
    private val storageService: StorageService,
    private val encryptionService: EncryptionService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun createPendingRecord(videoId: UUID, providerId: UUID): BackupRecord {
        val existing = recordRepo.findByVideoIdAndProviderId(videoId, providerId)
        if (existing != null) return existing

        val record = BackupRecord(videoId = videoId, providerId = providerId)
        log.info("Created PENDING backup record videoId={} providerId={}", videoId, providerId)
        return recordRepo.save(record)
    }

    fun processUpload(record: BackupRecord, providerEntity: BackupProviderEntity, youtubeVideoId: String, filePath: String) {
        record.status = BackupStatus.UPLOADING
        record.updatedAt = Instant.now()
        recordRepo.save(record)

        try {
            val provider = providerFactory.create(providerEntity)

            val existing = provider.search(youtubeVideoId)
            if (existing != null) {
                record.status = BackupStatus.BACKED_UP
                record.externalId = existing.externalId
                record.externalUrl = existing.externalUrl
                record.errorMessage = null
                record.updatedAt = Instant.now()
                recordRepo.save(record)
                log.info("Video already on provider videoId={} externalUrl={}", record.videoId, existing.externalUrl)
                return
            }

            val video = videoRepo.findById(record.videoId).orElse(null)
            val metadata = VideoBackupMetadata(
                youtubeVideoId = youtubeVideoId,
                title = video?.title,
                description = video?.description,
                youtubeUrl = video?.youtubeUrl ?: "https://www.youtube.com/watch?v=$youtubeVideoId"
            )

            storageService.retrieve(filePath).use { inputStream ->
                val result = provider.upload(inputStream, metadata)
                record.status = BackupStatus.BACKED_UP
                record.externalId = result.externalId
                record.externalUrl = result.externalUrl
                record.errorMessage = null
                record.updatedAt = Instant.now()
                recordRepo.save(record)
                log.info("Backup upload complete videoId={} externalUrl={}", record.videoId, result.externalUrl)
            }
        } catch (e: Exception) {
            record.status = BackupStatus.FAILED
            record.errorMessage = e.message
            record.updatedAt = Instant.now()
            recordRepo.save(record)
            log.warn("Backup upload failed videoId={}: {}", record.videoId, e.message)
        }
    }

    fun addProvider(userId: UUID, type: BackupProviderType, name: String, credentialsJson: String, isPrimary: Boolean): BackupProviderEntity {
        val encrypted = encryptionService.encrypt(credentialsJson)
        val provider = BackupProviderEntity(
            userId = userId,
            type = type,
            name = name,
            credentialsEncrypted = encrypted,
            isPrimary = isPrimary
        )
        log.info("Added backup provider name={} type={} userId={}", name, type, userId)
        return providerRepo.save(provider)
    }

    fun updateProvider(id: UUID, userId: UUID, name: String?, credentialsJson: String?): BackupProviderEntity? {
        val provider = providerRepo.findActiveByIdAndUserId(id, userId) ?: return null
        if (name != null) provider.name = name
        if (credentialsJson != null) provider.credentialsEncrypted = encryptionService.encrypt(credentialsJson)
        provider.updatedAt = Instant.now()
        log.info("Updated backup provider id={}", id)
        return providerRepo.save(provider)
    }

    fun deleteProvider(id: UUID, userId: UUID): Boolean {
        val provider = providerRepo.findActiveByIdAndUserId(id, userId) ?: return false
        provider.deletedAt = Instant.now()
        provider.updatedAt = Instant.now()
        providerRepo.save(provider)
        log.info("Soft-deleted backup provider id={}", id)
        return true
    }

    fun getProviders(userId: UUID): List<BackupProviderEntity> =
        providerRepo.findAllActiveByUserId(userId)

    fun getBackupRecords(videoId: UUID): List<BackupRecord> =
        recordRepo.findByVideoId(videoId)

    fun getBackupStatusForVideo(videoId: UUID): String? {
        val records = recordRepo.findByVideoId(videoId)
        if (records.isEmpty()) return null

        val hasPrimary = records.any { it.status == BackupStatus.BACKED_UP }
        val hasSecondary = records.size > 1 && records.drop(1).any { it.status == BackupStatus.BACKED_UP }
        val hasLost = records.any { it.status == BackupStatus.LOST }
        val hasFailed = records.any { it.status == BackupStatus.FAILED }
        val hasPending = records.any { it.status == BackupStatus.PENDING || it.status == BackupStatus.UPLOADING }

        return when {
            hasPrimary && hasSecondary -> "BACKED_UP_BOTH"
            hasSecondary && !hasPrimary -> "BACKED_UP_SECONDARY"
            hasPrimary -> "BACKED_UP"
            hasLost -> "LOST"
            hasFailed -> "FAILED"
            hasPending -> "PENDING"
            else -> null
        }
    }

    fun backfillCount(userId: UUID): Int {
        val primary = providerRepo.findPrimaryByUserId(userId) ?: return 0
        val downloadedIds = videoRepo.findDownloadedVideoIdsByUserId(userId)
        val backedUpIds = recordRepo.findVideoIdsByProviderId(primary.id).toSet()
        return downloadedIds.count { it !in backedUpIds }
    }

    fun triggerBackfill(userId: UUID): Int {
        val primary = providerRepo.findPrimaryByUserId(userId) ?: return 0
        val downloadedIds = videoRepo.findDownloadedVideoIdsByUserId(userId)
        val backedUpIds = recordRepo.findVideoIdsByProviderId(primary.id).toSet()
        val toBackfill = downloadedIds.filter { it !in backedUpIds }

        for (videoId in toBackfill) {
            createPendingRecord(videoId, primary.id)
        }
        log.info("Backfill queued {} videos for userId={}", toBackfill.size, userId)
        return toBackfill.size
    }

    fun getStats(userId: UUID): BackupStats {
        val total = recordRepo.countByUserId(userId)
        val backedUp = recordRepo.countByUserIdAndStatus(userId, BackupStatus.BACKED_UP)
        val pending = recordRepo.countByUserIdAndStatus(userId, BackupStatus.PENDING) +
            recordRepo.countByUserIdAndStatus(userId, BackupStatus.UPLOADING)
        val lost = recordRepo.countByUserIdAndStatus(userId, BackupStatus.LOST)
        val failed = recordRepo.countByUserIdAndStatus(userId, BackupStatus.FAILED)
        return BackupStats(total, backedUp, pending, lost, failed)
    }
}

data class BackupStats(
    val total: Long,
    val backedUp: Long,
    val pending: Long,
    val lost: Long,
    val failed: Long
)
```

- [x] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests "*BackupServiceTest*"`

Expected: PASS

- [x] **Step 7: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/backup/service/BackupService.kt src/test/kotlin/org/sightech/memoryvault/backup/service/BackupServiceTest.kt src/main/kotlin/org/sightech/memoryvault/youtube/repository/VideoRepository.kt src/main/kotlin/org/sightech/memoryvault/storage/
git commit -m "feat(backup): add BackupService with upload orchestration, backfill, and provider management"
```

---

## Task 6: BackupHealthCheckService

**Files:**
- Create: `.../backup/service/BackupHealthCheckService.kt`
- Create: `src/test/kotlin/.../backup/service/BackupHealthCheckServiceTest.kt`
- Modify: `.../websocket/VaultEvent.kt`
- Modify: `.../websocket/WebSocketEventRelay.kt`

- [x] **Step 1: Add BACKUP_LOST event type**

In `src/main/kotlin/org/sightech/memoryvault/websocket/VaultEvent.kt`, add to the `VaultEventType` enum:

```kotlin
enum class VaultEventType {
    FEED_SYNC_COMPLETED,
    JOB_STATUS_CHANGED,
    VIDEO_DOWNLOAD_COMPLETED,
    INGEST_READY,
    CONTENT_MUTATED,
    BACKUP_LOST
}
```

Add the event data class after `ContentMutated`:

```kotlin
data class BackupLost(
    override val userId: UUID,
    override val timestamp: Instant,
    val videoId: UUID,
    val providerId: UUID,
    val externalUrl: String?
) : VaultEvent {
    override val eventType = VaultEventType.BACKUP_LOST
}
```

- [x] **Step 2: Add WebSocket relay for BACKUP_LOST**

In `src/main/kotlin/org/sightech/memoryvault/websocket/WebSocketEventRelay.kt`, add after `onContentMutated`:

```kotlin
@Async("websocketRelayExecutor")
@EventListener
fun onBackupLost(event: BackupLost) {
    send("/topic/user/${event.userId}/backups", mapOf(
        "eventType" to event.eventType.name,
        "videoId" to event.videoId.toString(),
        "providerId" to event.providerId.toString()
    ))
}
```

- [x] **Step 3: Write failing tests**

Create `src/test/kotlin/org/sightech/memoryvault/backup/service/BackupHealthCheckServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.backup.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.backup.entity.*
import org.sightech.memoryvault.backup.provider.BackupProvider
import org.sightech.memoryvault.backup.provider.BackupProviderFactory
import org.sightech.memoryvault.backup.repository.BackupProviderRepository
import org.sightech.memoryvault.backup.repository.BackupRecordRepository
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class BackupHealthCheckServiceTest {

    private val recordRepo = mockk<BackupRecordRepository>()
    private val providerRepo = mockk<BackupProviderRepository>()
    private val providerFactory = mockk<BackupProviderFactory>()
    private val backupService = mockk<BackupService>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val service = BackupHealthCheckService(recordRepo, providerRepo, providerFactory, backupService, eventPublisher, 3)

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val providerId = UUID.randomUUID()
    private val videoId = UUID.randomUUID()

    private val providerEntity = BackupProviderEntity(
        id = providerId, userId = userId,
        type = BackupProviderType.INTERNET_ARCHIVE,
        name = "IA", credentialsEncrypted = "enc"
    )

    @BeforeEach
    fun setUp() {
        every { recordRepo.save(any()) } answers { firstArg() }
        every { providerRepo.findById(providerId) } returns Optional.of(providerEntity)
    }

    @Test
    fun `healthy check resets failure count`() {
        val record = BackupRecord(videoId = videoId, providerId = providerId, status = BackupStatus.BACKED_UP).apply {
            externalUrl = "https://archive.org/details/yt-abc"
            healthCheckFailures = 2
        }
        every { recordRepo.findAllBackedUp() } returns listOf(record)
        val mockProvider = mockk<BackupProvider>()
        every { providerFactory.create(providerEntity) } returns mockProvider
        every { mockProvider.checkHealth("https://archive.org/details/yt-abc") } returns true

        val result = service.runHealthChecks()

        assertEquals(0, record.healthCheckFailures)
        assertEquals(1, result["checked"])
        assertEquals(0, result["lost"])
    }

    @Test
    fun `failure increments count but does not mark LOST below threshold`() {
        val record = BackupRecord(videoId = videoId, providerId = providerId, status = BackupStatus.BACKED_UP).apply {
            externalUrl = "https://archive.org/details/yt-abc"
            healthCheckFailures = 1
        }
        every { recordRepo.findAllBackedUp() } returns listOf(record)
        val mockProvider = mockk<BackupProvider>()
        every { providerFactory.create(providerEntity) } returns mockProvider
        every { mockProvider.checkHealth("https://archive.org/details/yt-abc") } returns false

        service.runHealthChecks()

        assertEquals(2, record.healthCheckFailures)
        assertEquals(BackupStatus.BACKED_UP, record.status)
    }

    @Test
    fun `failure at threshold marks LOST and publishes event`() {
        val record = BackupRecord(videoId = videoId, providerId = providerId, status = BackupStatus.BACKED_UP).apply {
            externalUrl = "https://archive.org/details/yt-abc"
            healthCheckFailures = 2
        }
        every { recordRepo.findAllBackedUp() } returns listOf(record)
        val mockProvider = mockk<BackupProvider>()
        every { providerFactory.create(providerEntity) } returns mockProvider
        every { mockProvider.checkHealth("https://archive.org/details/yt-abc") } returns false

        service.runHealthChecks()

        assertEquals(BackupStatus.LOST, record.status)
        verify { eventPublisher.publishEvent(any()) }
    }

    @Test
    fun `LOST record triggers secondary failover if secondary provider exists`() {
        val record = BackupRecord(videoId = videoId, providerId = providerId, status = BackupStatus.BACKED_UP).apply {
            externalUrl = "https://archive.org/details/yt-abc"
            healthCheckFailures = 2
        }
        val secondaryProvider = BackupProviderEntity(
            userId = userId, type = BackupProviderType.CUSTOM,
            name = "Secondary", credentialsEncrypted = "enc", isPrimary = false
        )
        every { recordRepo.findAllBackedUp() } returns listOf(record)
        val mockProvider = mockk<BackupProvider>()
        every { providerFactory.create(providerEntity) } returns mockProvider
        every { mockProvider.checkHealth("https://archive.org/details/yt-abc") } returns false
        every { providerRepo.findAllActiveByUserId(userId) } returns listOf(providerEntity, secondaryProvider)
        every { backupService.createPendingRecord(videoId, secondaryProvider.id) } returns mockk()

        service.runHealthChecks()

        verify { backupService.createPendingRecord(videoId, secondaryProvider.id) }
    }
}
```

- [x] **Step 4: Run tests to verify they fail**

Run: `./gradlew test --tests "*BackupHealthCheckServiceTest*"`

Expected: FAIL (class not found)

- [x] **Step 5: Implement BackupHealthCheckService**

Create `src/main/kotlin/org/sightech/memoryvault/backup/service/BackupHealthCheckService.kt`:

```kotlin
package org.sightech.memoryvault.backup.service

import org.sightech.memoryvault.backup.entity.BackupStatus
import org.sightech.memoryvault.backup.provider.BackupProviderFactory
import org.sightech.memoryvault.backup.repository.BackupProviderRepository
import org.sightech.memoryvault.backup.repository.BackupRecordRepository
import org.sightech.memoryvault.websocket.BackupLost
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class BackupHealthCheckService(
    private val recordRepo: BackupRecordRepository,
    private val providerRepo: BackupProviderRepository,
    private val providerFactory: BackupProviderFactory,
    private val backupService: BackupService,
    private val eventPublisher: ApplicationEventPublisher,
    @Value("\${memoryvault.backup.health-check-failure-threshold:3}") private val failureThreshold: Int
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun runHealthChecks(): Map<String, Any> {
        val records = recordRepo.findAllBackedUp()
        var checked = 0
        var lost = 0

        val providerCache = mutableMapOf<java.util.UUID, org.sightech.memoryvault.backup.entity.BackupProviderEntity>()

        for (record in records) {
            val externalUrl = record.externalUrl ?: continue
            val providerEntity = providerCache.getOrPut(record.providerId) {
                providerRepo.findById(record.providerId).orElse(null) ?: continue
            }

            try {
                val provider = providerFactory.create(providerEntity)
                val healthy = provider.checkHealth(externalUrl)
                checked++

                record.lastHealthCheckAt = Instant.now()

                if (healthy) {
                    record.healthCheckFailures = 0
                } else {
                    record.healthCheckFailures++
                    log.warn("Health check failed videoId={} failures={}/{}", record.videoId, record.healthCheckFailures, failureThreshold)

                    if (record.healthCheckFailures >= failureThreshold) {
                        record.status = BackupStatus.LOST
                        lost++
                        log.warn("Backup marked LOST videoId={} externalUrl={}", record.videoId, externalUrl)

                        eventPublisher.publishEvent(BackupLost(
                            userId = providerEntity.userId,
                            timestamp = Instant.now(),
                            videoId = record.videoId,
                            providerId = record.providerId,
                            externalUrl = externalUrl
                        ))

                        triggerSecondaryFailover(record.videoId, providerEntity)
                    }
                }

                record.updatedAt = Instant.now()
                recordRepo.save(record)
            } catch (e: Exception) {
                log.warn("Health check error videoId={}: {}", record.videoId, e.message)
            }
        }

        log.info("Health check complete: checked={} lost={}", checked, lost)
        return mapOf("checked" to checked, "lost" to lost)
    }

    private fun triggerSecondaryFailover(videoId: java.util.UUID, lostProvider: org.sightech.memoryvault.backup.entity.BackupProviderEntity) {
        val allProviders = providerRepo.findAllActiveByUserId(lostProvider.userId)
        val secondary = allProviders.find { it.id != lostProvider.id && !it.isPrimary }
        if (secondary != null) {
            backupService.createPendingRecord(videoId, secondary.id)
            log.info("Secondary failover queued videoId={} secondaryProviderId={}", videoId, secondary.id)
        }
    }
}
```

- [x] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests "*BackupHealthCheckServiceTest*"`

Expected: PASS

- [x] **Step 7: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/backup/service/BackupHealthCheckService.kt src/test/kotlin/org/sightech/memoryvault/backup/service/BackupHealthCheckServiceTest.kt src/main/kotlin/org/sightech/memoryvault/websocket/VaultEvent.kt src/main/kotlin/org/sightech/memoryvault/websocket/WebSocketEventRelay.kt
git commit -m "feat(backup): add BackupHealthCheckService with LOST detection and secondary failover"
```

---

## Task 7: BackupSyncRegistrar + VideoDownloadCompleted Listener

**Files:**
- Create: `.../backup/BackupSyncRegistrar.kt`
- Modify: `.../backup/service/BackupService.kt` (add event listener)

- [x] **Step 1: Create BackupSyncRegistrar**

Create `src/main/kotlin/org/sightech/memoryvault/backup/BackupSyncRegistrar.kt`:

```kotlin
package org.sightech.memoryvault.backup

import org.sightech.memoryvault.backup.entity.BackupStatus
import org.sightech.memoryvault.backup.repository.BackupProviderRepository
import org.sightech.memoryvault.backup.repository.BackupRecordRepository
import org.sightech.memoryvault.backup.service.BackupService
import org.sightech.memoryvault.backup.service.BackupHealthCheckService
import org.sightech.memoryvault.scheduling.JobScheduler
import org.sightech.memoryvault.scheduling.entity.JobType
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class BackupSyncRegistrar(
    private val jobScheduler: JobScheduler,
    private val recordRepo: BackupRecordRepository,
    private val providerRepo: BackupProviderRepository,
    private val videoRepo: VideoRepository,
    private val backupService: BackupService,
    private val healthCheckService: BackupHealthCheckService,
    @Value("\${memoryvault.backup.upload-cron:-}") private val uploadCron: String,
    @Value("\${memoryvault.backup.health-check-cron:-}") private val healthCheckCron: String,
    @Value("\${memoryvault.backup.max-uploads-per-day:10}") private val maxUploadsPerDay: Int
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun registerBackupJobs() {
        jobScheduler.schedule("backup-upload", uploadCron, JobType.BACKUP_UPLOAD) {
            log.info("Backup upload job starting")
            val pending = recordRepo.findByStatus(BackupStatus.PENDING)
            val uploadsPerRun = (maxUploadsPerDay / 4).coerceAtLeast(1)
            val batch = pending.take(uploadsPerRun)

            var uploaded = 0
            var failed = 0
            for (record in batch) {
                val provider = providerRepo.findById(record.providerId).orElse(null) ?: continue
                val video = videoRepo.findById(record.videoId).orElse(null) ?: continue
                val filePath = video.filePath ?: continue

                backupService.processUpload(record, provider, video.youtubeVideoId, filePath)
                if (record.status == BackupStatus.BACKED_UP) uploaded++ else failed++
            }

            log.info("Backup upload job complete: uploaded={} failed={} remaining={}", uploaded, failed, pending.size - batch.size)
            mapOf("uploaded" to uploaded, "failed" to failed, "remaining" to (pending.size - batch.size))
        }

        jobScheduler.schedule("backup-health-check", healthCheckCron, JobType.BACKUP_HEALTH_CHECK) {
            log.info("Backup health check job starting")
            val result = healthCheckService.runHealthChecks()
            log.info("Backup health check job complete: {}", result)
            @Suppress("UNCHECKED_CAST")
            result as Map<String, Any>
        }
    }
}
```

- [x] **Step 2: Add VideoDownloadCompleted listener to BackupService**

In `src/main/kotlin/org/sightech/memoryvault/backup/service/BackupService.kt`, add the import and listener method:

Add imports:

```kotlin
import org.sightech.memoryvault.websocket.VideoDownloadCompleted
import org.springframework.context.event.EventListener
```

Add method to the class:

```kotlin
@EventListener
fun onVideoDownloadCompleted(event: VideoDownloadCompleted) {
    if (!event.success) return

    val primary = providerRepo.findPrimaryByUserId(event.userId) ?: return
    createPendingRecord(event.videoId, primary.id)
    log.info("Queued backup for newly downloaded video videoId={}", event.videoId)
}
```

- [x] **Step 3: Run all tests**

Run: `./gradlew test`

Expected: PASS

- [x] **Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/backup/BackupSyncRegistrar.kt src/main/kotlin/org/sightech/memoryvault/backup/service/BackupService.kt
git commit -m "feat(backup): add BackupSyncRegistrar and auto-queue on video download"
```

---

## Task 8: GraphQL Schema + Resolver

**Files:**
- Create: `src/main/resources/graphql/backup.graphqls`
- Create: `.../graphql/BackupResolver.kt`
- Modify: `src/main/resources/graphql/schema.graphqls`
- Modify: `src/main/resources/graphql/youtube.graphqls`

- [x] **Step 1: Create backup GraphQL types**

Create `src/main/resources/graphql/backup.graphqls`:

```graphql
type BackupProvider {
    id: UUID!
    type: String!
    name: String!
    isPrimary: Boolean!
    createdAt: Instant!
    updatedAt: Instant!
}

type BackupStats {
    total: Int!
    backedUp: Int!
    pending: Int!
    lost: Int!
    failed: Int!
}

input BackupProviderInput {
    type: String!
    name: String!
    accessKey: String!
    secretKey: String!
    isPrimary: Boolean!
}
```

- [x] **Step 2: Add backupStatus to Video type**

In `src/main/resources/graphql/youtube.graphqls`, add to the `Video` type after `downloadError: String`:

```graphql
    backupStatus: String
```

- [x] **Step 3: Add backup queries and mutations to schema**

In `src/main/resources/graphql/schema.graphqls`, add to the `Query` type:

```graphql
    # Backup
    backupProviders: [BackupProvider!]!
    backupStats: BackupStats!
```

Add to the `Mutation` type:

```graphql
    # Backup
    addBackupProvider(input: BackupProviderInput!): BackupProvider!
    updateBackupProvider(id: UUID!, input: BackupProviderInput!): BackupProvider
    deleteBackupProvider(id: UUID!): Boolean!
    triggerBackfill: Int!
```

- [x] **Step 4: Create BackupResolver**

Create `src/main/kotlin/org/sightech/memoryvault/graphql/BackupResolver.kt`:

```kotlin
package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.backup.entity.BackupProviderEntity
import org.sightech.memoryvault.backup.entity.BackupProviderType
import org.sightech.memoryvault.backup.service.BackupService
import org.sightech.memoryvault.youtube.entity.Video
import org.slf4j.LoggerFactory
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class BackupResolver(private val backupService: BackupService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @QueryMapping
    fun backupProviders(): List<BackupProviderEntity> {
        val userId = CurrentUser.userId()
        return backupService.getProviders(userId)
    }

    @QueryMapping
    fun backupStats(): Map<String, Any> {
        val userId = CurrentUser.userId()
        val stats = backupService.getStats(userId)
        return mapOf(
            "total" to stats.total,
            "backedUp" to stats.backedUp,
            "pending" to stats.pending,
            "lost" to stats.lost,
            "failed" to stats.failed
        )
    }

    @SchemaMapping(typeName = "Video", field = "backupStatus")
    fun backupStatus(video: Video): String? {
        return backupService.getBackupStatusForVideo(video.id)
    }

    @MutationMapping
    fun addBackupProvider(@Argument input: Map<String, Any>): BackupProviderEntity {
        val userId = CurrentUser.userId()
        val type = BackupProviderType.valueOf(input["type"] as String)
        val name = input["name"] as String
        val accessKey = input["accessKey"] as String
        val secretKey = input["secretKey"] as String
        val isPrimary = input["isPrimary"] as Boolean
        val credentialsJson = """{"accessKey":"$accessKey","secretKey":"$secretKey"}"""
        log.info("Adding backup provider name={} type={}", name, type)
        return backupService.addProvider(userId, type, name, credentialsJson, isPrimary)
    }

    @MutationMapping
    fun updateBackupProvider(@Argument id: UUID, @Argument input: Map<String, Any>): BackupProviderEntity? {
        val userId = CurrentUser.userId()
        val name = input["name"] as? String
        val accessKey = input["accessKey"] as? String
        val secretKey = input["secretKey"] as? String
        val credentialsJson = if (accessKey != null && secretKey != null) {
            """{"accessKey":"$accessKey","secretKey":"$secretKey"}"""
        } else null
        log.info("Updating backup provider id={}", id)
        return backupService.updateProvider(id, userId, name, credentialsJson)
    }

    @MutationMapping
    fun deleteBackupProvider(@Argument id: UUID): Boolean {
        val userId = CurrentUser.userId()
        log.info("Deleting backup provider id={}", id)
        return backupService.deleteProvider(id, userId)
    }

    @MutationMapping
    fun triggerBackfill(): Int {
        val userId = CurrentUser.userId()
        val count = backupService.triggerBackfill(userId)
        log.info("Backfill triggered: {} videos queued", count)
        return count
    }
}
```

- [x] **Step 5: Run all tests**

Run: `./gradlew test`

Expected: PASS

- [x] **Step 6: Commit**

```bash
git add src/main/resources/graphql/backup.graphqls src/main/resources/graphql/schema.graphqls src/main/resources/graphql/youtube.graphqls src/main/kotlin/org/sightech/memoryvault/graphql/BackupResolver.kt
git commit -m "feat(backup): add GraphQL schema and BackupResolver"
```

---

## Task 9: MCP Tools

**Files:**
- Create: `.../mcp/BackupTools.kt`

- [x] **Step 1: Create BackupTools**

Create `src/main/kotlin/org/sightech/memoryvault/mcp/BackupTools.kt`:

```kotlin
package org.sightech.memoryvault.mcp

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.backup.service.BackupService
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class BackupTools(private val backupService: BackupService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Tool(description = "Get backup status for a specific video across all configured providers. Shows whether the video is backed up, pending, lost, or failed on each provider. Use when checking if a video has been backed up.")
    fun getBackupStatus(videoId: String): String {
        val records = backupService.getBackupRecords(UUID.fromString(videoId))
        if (records.isEmpty()) return "No backup records for this video."

        val lines = records.map { r ->
            "- ${r.status}: ${r.externalUrl ?: "(no URL)"}" +
                (if (r.errorMessage != null) " — error: ${r.errorMessage}" else "") +
                (if (r.healthCheckFailures > 0) " — health check failures: ${r.healthCheckFailures}" else "")
        }
        return "${records.size} backup record(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "List all configured backup providers (without credentials). Shows provider type, name, and whether it's the primary. Use when checking what backup destinations are configured.")
    fun listBackupProviders(): String {
        val userId = CurrentUser.userId()
        val providers = backupService.getProviders(userId)
        if (providers.isEmpty()) return "No backup providers configured."

        val lines = providers.map { p ->
            "- ${p.name} (${p.type}${if (p.isPrimary) ", primary" else ""}) — id: ${p.id}"
        }
        return "${providers.size} provider(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Queue all downloaded videos that haven't been backed up yet for backup to the primary provider. Returns the count of videos queued. Use when the user wants to backfill existing videos.")
    fun triggerBackfill(): String {
        val userId = CurrentUser.userId()
        val count = backupService.triggerBackfill(userId)
        return if (count > 0) "Queued $count video(s) for backup." else "All downloaded videos are already backed up or no primary provider configured."
    }
}
```

- [x] **Step 2: Run all tests**

Run: `./gradlew test`

Expected: PASS

- [x] **Step 3: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/mcp/BackupTools.kt
git commit -m "feat(backup): add MCP tools for backup status, providers, and backfill"
```

---

## Task 10: Angular — Backup Status on YouTube Page

**Files:**
- Modify: `client/src/app/youtube/youtube.graphql`
- Modify: `client/src/app/youtube/youtube.ts`
- Regenerate: `client/src/app/shared/graphql/generated.ts`

- [x] **Step 1: Add backupStatus to GetVideos query**

In `client/src/app/youtube/youtube.graphql`, update the `GetVideos` query to include `backupStatus`:

```graphql
query GetVideos($listId: UUID, $query: String, $removedOnly: Boolean) {
  videos(listId: $listId, query: $query, removedOnly: $removedOnly) {
    id
    title
    youtubeUrl
    thumbnailPath
    removedFromYoutube
    downloadedAt
    downloadError
    backupStatus
  }
}
```

- [x] **Step 2: Regenerate GraphQL types**

Run: `cd client && npx graphql-codegen`

Expected: `generated.ts` is updated with `backupStatus` on the `Video` type.

- [x] **Step 3: Add shield icons and legend to YouTube component**

In `client/src/app/youtube/youtube.ts`, add the shield icon after the error badge in the video-meta span. Find the closing `</span>` after the error badge block and add:

```html
@if (video.backupStatus === 'BACKED_UP') {
  <mat-icon class="shield-icon shield-green" matTooltip="Backed up (IA)">shield</mat-icon>
} @else if (video.backupStatus === 'BACKED_UP_BOTH') {
  <mat-icon class="shield-icon shield-green" matTooltip="Backed up (IA + secondary)">verified_user</mat-icon>
} @else if (video.backupStatus === 'BACKED_UP_SECONDARY') {
  <mat-icon class="shield-icon shield-blue" matTooltip="Backed up (secondary only)">shield</mat-icon>
} @else if (video.backupStatus === 'LOST') {
  <mat-icon class="shield-icon shield-yellow" matTooltip="Backup lost, queued for secondary">shield</mat-icon>
} @else if (video.backupStatus === 'FAILED') {
  <mat-icon class="shield-icon shield-red" matTooltip="Backup failed">shield</mat-icon>
} @else if (video.backupStatus === 'PENDING') {
  <mat-icon class="shield-icon shield-gray" matTooltip="Backup pending">shield</mat-icon>
}
```

Add a collapsible legend above the video list. After the `@if (store.loadingVideos())` progress bar block and before the `<div class="video-list">`:

```html
<details class="backup-legend">
  <summary>Backup status legend</summary>
  <div class="legend-items">
    <span><mat-icon class="shield-icon shield-green">shield</mat-icon> Backed up (IA)</span>
    <span><mat-icon class="shield-icon shield-green">verified_user</mat-icon> Backed up (IA + secondary)</span>
    <span><mat-icon class="shield-icon shield-blue">shield</mat-icon> Backed up (secondary only)</span>
    <span><mat-icon class="shield-icon shield-yellow">shield</mat-icon> Backup lost</span>
    <span><mat-icon class="shield-icon shield-red">shield</mat-icon> Backup failed</span>
    <span><mat-icon class="shield-icon shield-gray">shield</mat-icon> Backup pending</span>
  </div>
</details>
```

Add these styles:

```css
.shield-icon { font-size: 14px; width: 14px; height: 14px; margin-left: 4px; vertical-align: middle; }
.shield-green { color: #2e7d32; }
.shield-blue { color: #1565c0; }
.shield-yellow { color: #f9a825; }
.shield-red { color: #c62828; }
.shield-gray { color: #9e9e9e; }

.backup-legend {
  padding: 4px 16px; font-size: 0.7rem; color: #5f6368;
  border-bottom: 1px solid #f1f3f4;
}
.backup-legend summary { cursor: pointer; user-select: none; }
.legend-items {
  display: flex; flex-wrap: wrap; gap: 12px; padding: 6px 0;
}
.legend-items span { display: inline-flex; align-items: center; gap: 2px; }
```

- [x] **Step 4: Run frontend tests**

Run: `cd client && npm run test`

Expected: PASS (component tests pass with new field)

- [x] **Step 5: Commit**

```bash
git add client/src/app/youtube/youtube.graphql client/src/app/youtube/youtube.ts client/src/app/shared/graphql/generated.ts
git commit -m "feat(backup): add shield icons and legend on YouTube video list"
```

---

## Task 11: Angular — Admin Backup Panel

**Files:**
- Create: `client/src/app/admin/backup-panel/backup-panel.ts`
- Create: `client/src/app/admin/backup-panel/index.ts`
- Create: `client/src/app/youtube/backup.graphql`
- Modify: `client/src/app/admin/admin.graphql`
- Modify: `client/src/app/admin/admin.store.ts`
- Modify: `client/src/app/admin/admin.ts`
- Regenerate: `client/src/app/shared/graphql/generated.ts`

- [x] **Step 1: Add backup GraphQL operations**

Create `client/src/app/youtube/backup.graphql`:

```graphql
query GetBackupProviders {
  backupProviders {
    id
    type
    name
    isPrimary
    createdAt
  }
}

query GetBackupStats {
  backupStats {
    total
    backedUp
    pending
    lost
    failed
  }
}

mutation AddBackupProvider($input: BackupProviderInput!) {
  addBackupProvider(input: $input) {
    id
    type
    name
    isPrimary
  }
}

mutation DeleteBackupProvider($id: UUID!) {
  deleteBackupProvider(id: $id)
}

mutation TriggerBackfill {
  triggerBackfill
}
```

- [x] **Step 2: Regenerate GraphQL types**

Run: `cd client && npx graphql-codegen`

- [x] **Step 3: Create backup-panel component**

Create `client/src/app/admin/backup-panel/backup-panel.ts`:

```typescript
import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { DatePipe } from '@angular/common';

export interface BackupProviderView {
  id: string;
  type: string;
  name: string;
  isPrimary: boolean;
  createdAt: string;
}

export interface BackupStatsView {
  total: number;
  backedUp: number;
  pending: number;
  lost: number;
  failed: number;
}

@Component({
  selector: 'app-backup-panel',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatListModule, DatePipe],
  template: `
    <div class="backup-panel">
      <h3>Backup Providers</h3>

      @if (providers().length === 0) {
        <p class="empty">No backup providers configured.</p>
      } @else {
        <mat-list>
          @for (p of providers(); track p.id) {
            <mat-list-item>
              <mat-icon matListItemIcon>{{ p.isPrimary ? 'cloud_done' : 'cloud_queue' }}</mat-icon>
              <span matListItemTitle>{{ p.name }}</span>
              <span matListItemLine>{{ p.type }}{{ p.isPrimary ? ' (primary)' : '' }} &mdash; added {{ p.createdAt | date:'mediumDate' }}</span>
              <button mat-icon-button matListItemMeta (click)="onDelete.emit(p.id)" matTooltip="Remove">
                <mat-icon>delete_outline</mat-icon>
              </button>
            </mat-list-item>
          }
        </mat-list>
      }

      <div class="actions">
        <button mat-stroked-button (click)="onAdd.emit()">
          <mat-icon>add</mat-icon> Add Provider
        </button>
        <button mat-stroked-button (click)="onBackfill.emit()" [disabled]="!stats() || stats()!.total === 0">
          <mat-icon>backup</mat-icon> Backfill All
        </button>
      </div>

      @if (stats(); as s) {
        <div class="stats-grid">
          <div class="stat-item">
            <span class="stat-value">{{ s.backedUp }}</span>
            <span class="stat-label">Backed Up</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ s.pending }}</span>
            <span class="stat-label">Pending</span>
          </div>
          <div class="stat-item">
            <span class="stat-value warn">{{ s.lost }}</span>
            <span class="stat-label">Lost</span>
          </div>
          <div class="stat-item">
            <span class="stat-value warn">{{ s.failed }}</span>
            <span class="stat-label">Failed</span>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .backup-panel { padding: 16px; }
    h3 { font-size: 0.875rem; font-weight: 600; color: #202124; margin: 0 0 12px; }
    .empty { font-size: 0.8125rem; color: #80868b; }
    .actions { display: flex; gap: 8px; margin: 12px 0; }
    .stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-top: 16px; }
    .stat-item { text-align: center; }
    .stat-value { display: block; font-size: 1.5rem; font-weight: 600; color: #202124; }
    .stat-value.warn { color: #c62828; }
    .stat-label { font-size: 0.7rem; color: #80868b; text-transform: uppercase; }
  `]
})
export class BackupPanelComponent {
  providers = input.required<BackupProviderView[]>();
  stats = input.required<BackupStatsView | null>();

  onAdd = output<void>();
  onDelete = output<string>();
  onBackfill = output<void>();
}
```

Create `client/src/app/admin/backup-panel/index.ts`:

```typescript
export { BackupPanelComponent, type BackupProviderView, type BackupStatsView } from './backup-panel';
```

- [x] **Step 4: Update admin.store.ts with backup state**

In `client/src/app/admin/admin.store.ts`, add backup-related state, imports, and methods. The exact changes depend on the store's current structure, but add:

State fields:
```typescript
backupProviders: [] as BackupProviderView[],
backupStats: null as BackupStatsView | null,
```

Methods:
```typescript
loadBackupProviders: () => {
  apollo.query({ query: GetBackupProvidersDocument, fetchPolicy: 'network-only' }).subscribe((result: any) => {
    patchState(store, { backupProviders: result.data.backupProviders });
  });
},

loadBackupStats: () => {
  apollo.query({ query: GetBackupStatsDocument, fetchPolicy: 'network-only' }).subscribe((result: any) => {
    patchState(store, { backupStats: result.data.backupStats });
  });
},

deleteBackupProvider: (id: string) => {
  apollo.mutate({ mutation: DeleteBackupProviderDocument, variables: { id } }).subscribe(() => {
    patchState(store, {
      backupProviders: store.backupProviders().filter((p: any) => p.id !== id)
    });
  });
},

triggerBackfill: () => {
  apollo.mutate({ mutation: TriggerBackfillDocument }).subscribe((result: any) => {
    const count = result.data?.triggerBackfill ?? 0;
    loadBackupStats();
  });
},
```

Import the generated documents from `../../shared/graphql/generated`.

- [x] **Step 5: Add Backup tab to admin component**

In `client/src/app/admin/admin.ts`, import `BackupPanelComponent` and add it to the imports array. Add a new `mat-tab` after the Logs tab:

```html
<mat-tab label="Backups">
  <app-backup-panel
    [providers]="store.backupProviders()"
    [stats]="store.backupStats()"
    (onAdd)="openAddProviderDialog()"
    (onDelete)="store.deleteBackupProvider($event)"
    (onBackfill)="store.triggerBackfill()"
  />
</mat-tab>
```

In `onTabChange`, add: `else if (index === 3) { this.store.loadBackupProviders(); this.store.loadBackupStats(); }`

Add a placeholder `openAddProviderDialog()` method (can use a simple prompt or Material dialog — follow the pattern from `YoutubeListDialogComponent`):

```typescript
openAddProviderDialog() {
  // For now, a simple implementation. A full dialog can be added later.
  const accessKey = prompt('Internet Archive Access Key:');
  if (!accessKey) return;
  const secretKey = prompt('Internet Archive Secret Key:');
  if (!secretKey) return;

  this.store.addBackupProvider({
    type: 'INTERNET_ARCHIVE',
    name: 'Internet Archive',
    accessKey,
    secretKey,
    isPrimary: true
  });
}
```

Add `addBackupProvider` to the store:

```typescript
addBackupProvider: (input: { type: string; name: string; accessKey: string; secretKey: string; isPrimary: boolean }) => {
  apollo.mutate({
    mutation: AddBackupProviderDocument,
    variables: { input }
  }).subscribe(() => {
    loadBackupProviders();
  });
},
```

- [x] **Step 6: Regenerate GraphQL types (if not already done)**

Run: `cd client && npx graphql-codegen`

- [x] **Step 7: Run frontend tests**

Run: `cd client && npm run test`

Expected: PASS

- [x] **Step 8: Commit**

```bash
git add client/src/app/admin/backup-panel/ client/src/app/youtube/backup.graphql client/src/app/admin/admin.graphql client/src/app/admin/admin.store.ts client/src/app/admin/admin.ts client/src/app/shared/graphql/generated.ts
git commit -m "feat(backup): add admin backup panel with provider management and stats"
```

---

## Task 12: Start Dev Server + Manual Smoke Test

- [x] **Step 1: Start backend + frontend**

Run: `./scripts/dev.sh`

- [x] **Step 2: Verify YouTube page loads**

Open `http://localhost:4200/youtube`. Confirm:
- Videos display without errors
- Videos without backup records show no shield icon
- Backup legend is visible (collapsed by default)

- [x] **Step 3: Verify admin backup tab**

Navigate to Admin page, click the "Backups" tab. Confirm:
- "No backup providers configured" message shows
- Stats show all zeros
- "Add Provider" button is visible

- [x] **Step 4: Test add provider flow**

Click "Add Provider", enter test IA credentials. Confirm:
- Provider appears in the list
- Stats update

- [x] **Step 5: Test backfill**

Click "Backfill All". Confirm:
- Stats "Pending" count increases

- [x] **Step 6: Run full test suite**

Run: `./gradlew test` and `cd client && npm run test`

Expected: All tests pass.

- [x] **Step 7: Final commit if any adjustments needed**

```bash
git add -A
git commit -m "fix(backup): adjustments from manual testing"
```

---

## Summary Table

| Task | Description | Key Files |
|------|-------------|-----------|
| 1 | Migration, enums, config | `V9__backup_tables.sql`, `BackupEnums.kt`, `application.properties`, `.env.sample` |
| 2 | EncryptionService | `crypto/EncryptionService.kt` + test |
| 3 | Entities + repositories | `BackupProviderEntity.kt`, `BackupRecord.kt`, 2 repositories |
| 4 | Provider interface + IA impl | `BackupProvider.kt`, `InternetArchiveProvider.kt`, `BackupProviderFactory.kt` + test |
| 5 | BackupService | `BackupService.kt` + test, `VideoRepository` changes, `StorageService` changes |
| 6 | Health check service | `BackupHealthCheckService.kt` + test, `VaultEvent.kt`, `WebSocketEventRelay.kt` |
| 7 | Sync registrar + event listener | `BackupSyncRegistrar.kt`, `BackupService.kt` event listener |
| 8 | GraphQL schema + resolver | `backup.graphqls`, `schema.graphqls`, `youtube.graphqls`, `BackupResolver.kt` |
| 9 | MCP tools | `BackupTools.kt` |
| 10 | YouTube page shield icons | `youtube.graphql`, `youtube.ts`, `generated.ts` |
| 11 | Admin backup panel | `backup-panel/`, `admin.graphql`, `admin.store.ts`, `admin.ts` |
| 12 | Manual smoke test | Dev server verification |
