# Phase 10 — Cross-Platform Video Backup

**Goal**: Automatically back up archived YouTube videos to the Internet Archive (and optionally a secondary provider) for resilience against takedowns and creator deletions.

**Architecture**: Internet Archive is the built-in primary backup target. A pluggable provider interface supports secondary providers as failover — secondary uploads activate only when IA health checks detect a backup is lost. All backup operations run as throttled sync jobs to be respectful to external services.

---

## Core Concepts

- **Internet Archive as default primary** — every downloaded video is queued for IA upload automatically. IA's S3-like API handles uploads; metadata tags preserve YouTube provenance.
- **Dedup before upload** — search IA for existing uploads by YouTube video ID before uploading. If someone else already archived it, record the URL as our backup reference without re-uploading.
- **Secondary provider as failover** — if configured, a secondary provider receives videos only when the IA backup is lost (detected by health checks) or when manually triggered.
- **Throttled uploads** — configurable rate (default 10/day) to be respectful to IA. Implemented as a capped queue in the sync job.
- **Backfill** — admin action to queue all existing downloaded videos for backup, throttled at the same rate.
- **Health monitoring** — periodic checks that backed-up videos are still accessible. 3 consecutive failures → mark as LOST → trigger secondary failover + notification.

---

## Provider Interface

```
BackupProvider (interface)
├── search(youtubeVideoId: String): BackupSearchResult?
├── upload(videoFile: InputStream, metadata: VideoBackupMetadata): BackupUploadResult
├── checkHealth(externalUrl: String): Boolean
└── delete(externalId: String): Boolean
```

**InternetArchiveProvider** is the built-in implementation:
- **Item identifier**: `yt-{youtubeVideoId}` (e.g., `yt-dQw4w9WgXcQ`)
- **Metadata headers**: `mediatype:movies`, `title`, `description`, `youtube-video-id`, `youtube-url`, `archived-by:memoryvault`
- **Search**: IA Advanced Search API querying `youtube-video-id:{id}`
- **Health check**: HTTP HEAD on `https://archive.org/details/yt-{id}`
- **Upload**: IA S3 API with `x-archive-auto-make-bucket` header

Secondary providers implement the same interface. A factory resolves `BackupProviderEntity` type to the right implementation at runtime.

---

## Data Model

### BackupProviderEntity

```
id (UUID), userId (FK → users)
type: BackupProviderType enum (INTERNET_ARCHIVE, CUSTOM)
name: String (display name, e.g., "Internet Archive", "My Rumble")
credentialsEncrypted: String (AES-256-GCM encrypted JSON)
config: JSONB (provider-specific settings, e.g., throttle rate)
isPrimary: Boolean (true = IA default, false = secondary)
createdAt, updatedAt, deletedAt, version
```

### BackupRecord

```
id (UUID)
videoId (FK → videos)
providerId (FK → backup_providers)
status: BackupStatus enum (PENDING, UPLOADING, BACKED_UP, LOST, FAILED)
externalUrl: String? (URL on the backup platform)
externalId: String? (platform-specific identifier)
errorMessage: String? (failure details)
lastHealthCheckAt: Instant?
healthCheckFailures: Int (consecutive failures, resets to 0 on success)
createdAt, updatedAt
```

### Enums (BackupEnums.kt)

Both enums live in a single `BackupEnums.kt` file in the entity package, following the pattern of grouping related enums by domain.

```kotlin
enum class BackupProviderType { INTERNET_ARCHIVE, CUSTOM }
enum class BackupStatus { PENDING, UPLOADING, BACKED_UP, LOST, FAILED }
```

### Migration (V9)

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
```

---

## Credential Encryption

`EncryptionService` lives in the shared `crypto/` package (`src/main/kotlin/org/sightech/memoryvault/crypto/EncryptionService.kt`) — not in `backup/` — since credential encryption is a cross-cutting concern reusable by future features (Phase 11 payment tokens, API keys, etc.).

IA credentials (access key + secret key) are stored encrypted using Spring Security Crypto's `TextEncryptor` (AES-256-GCM). The encryption password comes from env var `MEMORYVAULT_ENCRYPTION_KEY`. Credentials are stored as encrypted JSON:

```json
{"accessKey": "...", "secretKey": "..."}
```

Decrypted at runtime when the provider is used. Never logged, never returned in API responses.

---

## Data Flow

### Upload Flow

1. `AsyncVideoDownloader` completes → publishes `VideoDownloadCompleted` event
2. `BackupService` listens → creates `BackupRecord` with status `PENDING` for the primary provider
3. `BackupSyncJob` runs on schedule → picks up `PENDING` records, respects throttle rate
4. For each video:
   a. Search IA for existing upload by YouTube video ID
   b. If found → store external URL, mark `BACKED_UP` (no upload needed)
   c. If not found → download file from storage, upload to IA via S3 API, mark `BACKED_UP`
   d. On failure → mark `FAILED` with error message

### Health Check Flow

1. `BackupHealthCheckJob` runs on schedule (daily)
2. For all `BACKED_UP` records: HTTP HEAD on external URL
3. Success → reset `healthCheckFailures` to 0, update `lastHealthCheckAt`
4. Failure → increment `healthCheckFailures`
5. If `healthCheckFailures >= 3` → mark `LOST`
   a. If secondary provider is configured → create `PENDING` record for secondary
   b. Publish notification event (WebSocket + admin UI)

### Backfill Flow

1. Admin triggers backfill via UI or MCP tool
2. `BackupService` queries all downloaded videos without a `BackupRecord`
3. Creates `PENDING` records for each
4. Normal sync job processes them at the throttled rate

---

## Sync Jobs

Two sync jobs, both using the existing `SyncJob` audit trail pattern:

- **BackupUploadJob** — processes PENDING uploads, configurable cron (default: every 6 hours, 4 runs/day), throttled to N uploads per run (default: 10/day = ~2-3 per run)
- **BackupHealthCheckJob** — checks all BACKED_UP records, configurable cron (default: daily)

---

## UI

### YouTube Video List

Shield icon per video showing backup status:
- **No icon** — not yet backed up (pending or no provider configured)
- **Green shield** — backed up on primary (IA)
- **Yellow shield** — IA backup lost, queued for secondary
- **Blue shield** — backed up on secondary
- **Red shield** — backup failed (tooltip shows error)
- **Green shield + badge** — backed up on both IA and secondary

**Legend**: Collapsible legend/key on the YouTube page explaining shield colors and their meanings.

### Admin Page

New "Backup Providers" section:
- Add/edit Internet Archive credentials (access key + secret key)
- Add/edit secondary provider
- Backup stats: total videos, backed up, pending, lost, failed
- "Backfill All" button to queue existing videos
- Provider health status (last successful upload, last health check)

### GraphQL

Extend `Video` type with `backupStatus: BackupStatus` (computed from backup records).

New queries:
- `backupProviders: [BackupProvider!]!`
- `backupStats: BackupStats!`

New mutations:
- `addBackupProvider(input: BackupProviderInput!): BackupProvider!`
- `updateBackupProvider(id: UUID!, input: BackupProviderInput!): BackupProvider!`
- `deleteBackupProvider(id: UUID!): Boolean!`
- `triggerBackfill: Int!` (returns count of videos queued)

---

## MCP Tools

- `getBackupStatus(videoId)` — backup status for a specific video across all providers
- `listBackupProviders()` — list configured backup providers (without credentials)
- `triggerBackfill()` — queue all unbacked-up videos for backup

---

## Cross-Cutting Concerns

### Security
- Credentials encrypted at rest (AES-256-GCM via Spring Security Crypto)
- Encryption key from env var, not committed to code
- Credentials never logged, never returned in API responses
- Admin-only access to provider management

### Logging
- INFO: upload success, health check pass, backfill triggered
- WARN: upload failure, health check failure, failover triggered
- All operations include video ID and provider context

### Threading
- Upload and health check jobs are scheduled, not @Async
- IA HTTP calls use configurable timeouts
- Throttle prevents overwhelming external services

### Error Handling
- Upload failures set `FAILED` status with error message, retryable on next sync run
- Health check uses consecutive-failure threshold (3) before marking LOST
- IA API errors (auth, rate limit, server error) logged and retried on next cycle

### Configuration
- `memoryvault.backup.upload-cron` — upload job schedule (default: `0 0 */6 * * *`)
- `memoryvault.backup.health-check-cron` — health check schedule (default: `0 0 6 * * *`)
- `memoryvault.backup.max-uploads-per-day` — throttle (default: 10)
- `memoryvault.backup.health-check-failure-threshold` — consecutive failures before LOST (default: 3)
- `MEMORYVAULT_ENCRYPTION_KEY` — env var for credential encryption

---

## Package Structure

```
src/main/kotlin/org/sightech/memoryvault/
├── crypto/            EncryptionService.kt
└── backup/
    ├── entity/        BackupProviderEntity.kt, BackupRecord.kt, BackupEnums.kt
    ├── repository/    BackupProviderRepository.kt, BackupRecordRepository.kt
    ├── service/       BackupService.kt, BackupHealthCheckService.kt
    ├── provider/      BackupProvider.kt (interface), InternetArchiveProvider.kt, BackupProviderFactory.kt
    └── controller/    (none — GraphQL and MCP only)
```

GraphQL: `graphql/BackupResolver.kt`
MCP: `mcp/BackupTools.kt`
