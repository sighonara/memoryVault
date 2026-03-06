# Phase 4: Cross-Cutting — Design Document

**Date**: 2026-03-05
**Status**: Approved

## Goal

Add cross-cutting features that span all content domains: full-text search, system stats, job history tracking, and log retrieval. These tools complete the MCP tool suite for day-to-day use.

## Scope

Four new MCP tools:

| Tool | Purpose |
|---|---|
| `search(query, types?)` | Full-text search across bookmarks, feed items, videos |
| `getStats()` | Content counts, storage usage, sync health |
| `listJobs(type?, limit?)` | Job execution history with metadata |
| `getLogs(level?, service?, limit?)` | Retrieve structured logs |

**Not in scope (deferred to Phase 6):**
- `get_aws_cost` — requires AWS Cost Explorer API
- AWS stats in `getStats` (S3 size, Lambda invocations, RDS metrics)
- `CloudWatchLogService` implementation

---

## Full-Text Search

### Approach: PostgreSQL native FTS

Add a `search_vector` column (`tsvector`) to `bookmarks`, `feed_items`, and `videos`. PostgreSQL triggers maintain the vector on INSERT/UPDATE. GIN indexes make queries fast. No additional infrastructure.

### Weight configuration

| Table | Field | Weight |
|---|---|---|
| `bookmarks` | `title` | A |
| `bookmarks` | `url` | B |
| `feed_items` | `title` | A |
| `feed_items` | `content` | B |
| `feed_items` | `author` | C |
| `videos` | `title` | A |
| `videos` | `channel_name` | B |
| `videos` | `description` | C |

### Query pattern

```sql
SELECT id, title, ts_rank(search_vector, query) AS rank
FROM bookmarks, to_tsquery('english', :query) query
WHERE user_id = :userId
  AND deleted_at IS NULL
  AND search_vector @@ query
ORDER BY rank DESC
LIMIT :limit
```

Results from all three tables are merged and sorted by rank in the service layer.

### Unified result type

```kotlin
data class SearchResult(
    val type: ContentType,  // BOOKMARK, FEED_ITEM, VIDEO
    val id: UUID,
    val title: String?,
    val url: String?,
    val rank: Float
)

enum class ContentType { BOOKMARK, FEED_ITEM, VIDEO }
```

Multi-tenant safe: all queries filter by `user_id`.

---

## Job History (SyncJob)

### Entity

```
sync_jobs
  id              UUID PK
  user_id         UUID NOT NULL FK users
  type            VARCHAR(30) NOT NULL  -- RSS_FETCH | YT_SYNC | BOOKMARK_ARCHIVE
  status          VARCHAR(20) NOT NULL  -- PENDING | RUNNING | SUCCESS | FAILED
  started_at      TIMESTAMPTZ NOT NULL
  completed_at    TIMESTAMPTZ
  error_message   TEXT
  triggered_by    VARCHAR(20) NOT NULL  -- SCHEDULED | MANUAL
  metadata        JSONB       -- per-job-type details (items processed, etc.)
```

Append-only audit records. No soft delete, no versioning.

### Metadata examples

**RSS_FETCH:**
```json
{"feedId": "uuid", "newItems": 12, "feedTitle": "Hacker News"}
```

**YT_SYNC:**
```json
{"listId": "uuid", "newVideos": 3, "removedVideos": 1, "downloadSuccesses": 2, "downloadFailures": 1}
```

**BOOKMARK_ARCHIVE:**
```json
{"bookmarkCount": 47}
```

### Integration with SpringJobScheduler

The existing `SpringJobScheduler` wraps job execution to record start/completion/failure to the `sync_jobs` table via `SyncJobService`. Domain-specific metadata is passed through from sync results.

---

## System Stats

### StatsService returns

```kotlin
data class SystemStats(
    val bookmarkCount: Long,
    val feedCount: Long,
    val feedItemCount: Long,
    val unreadFeedItemCount: Long,
    val youtubeListCount: Long,
    val videoCount: Long,
    val downloadedVideoCount: Long,
    val removedVideoCount: Long,
    val tagCount: Long,
    val storageUsedBytes: Long,
    val lastFeedSync: Instant?,
    val lastYoutubeSync: Instant?,
    val feedsWithFailures: Long,
    val youtubeListsWithFailures: Long
)
```

All derived from existing repository count queries plus `SyncJobRepository` for last sync times.

### Future AWS stats (Phase 6 TODO)

When AWS infrastructure exists, `getStats` should also report:
- AWS cost per billing cycle (compute, storage, transfer) via Cost Explorer API
- S3 bucket storage used (CloudWatch metrics or S3 inventory)
- Lambda invocation counts and error rates
- RDS connection/query metrics

---

## Log Retrieval

### Infrastructure: logback-spring.xml

New logback config with two appenders:

- **Console**: standard Spring Boot format for dev readability
- **File**: JSON structured format, rolling daily
  - Path: `${memoryvault.logging.path}` (default: `~/.memoryvault/logs/memoryvault.log`)
  - Fields: `timestamp`, `level`, `logger`, `message`, `thread`

### LogService interface

```kotlin
interface LogService {
    fun getLogs(level: String?, logger: String?, limit: Int?): List<LogEntry>
}

data class LogEntry(
    val timestamp: Instant,
    val level: String,
    val logger: String,
    val message: String,
    val thread: String
)
```

### Implementations

- **`LocalLogService`** (`@Profile("!aws")`): Reads the JSON log file, parses line by line, filters by level/logger, returns most recent entries (tail-first). Default limit: 50.
- **`CloudWatchLogService`** (`@Profile("aws")`): Stub. Returns message indicating AWS implementation pending (Phase 6). When implemented: use CloudWatch Logs Insights API to query log groups.

---

## MCP Tools

All four tools go in `CrossCuttingTools.kt`:

```kotlin
@Tool("Search across all content — bookmarks, feed items, and videos.
       Optionally filter by type (BOOKMARK, FEED_ITEM, VIDEO).")
fun search(query: String, types: String?): String

@Tool("Get system statistics — content counts, storage usage, sync health.")
fun getStats(): String

@Tool("View job execution history. Optionally filter by type
       (RSS_FETCH, YT_SYNC, BOOKMARK_ARCHIVE) and limit results.")
fun listJobs(type: String?, limit: Int?): String

@Tool("Retrieve application logs. Filter by level (INFO, WARN, ERROR),
       logger name, and limit.")
fun getLogs(level: String?, service: String?, limit: Int?): String
```

---

## Migration: V3__search_and_jobs.sql

1. Create `sync_jobs` table
2. Add `search_vector tsvector` column to `bookmarks`, `feed_items`, `videos`
3. Create GIN indexes on each `search_vector` column
4. Create triggers to auto-update `search_vector` on INSERT/UPDATE
5. Backfill existing rows

---

## Testing

- **Unit tests**: SearchService, StatsService, SyncJobService, LocalLogService (MockK)
- **Integration test**: CrossCuttingIntegrationTest — insert content across domains, verify FTS returns ranked results, verify stats, verify job recording
- **MCP tool tests**: CrossCuttingToolsTest — mock services, verify formatting
- **Script**: `scripts/test-crosscutting.sh`
