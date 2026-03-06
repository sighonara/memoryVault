# Phase 4: Cross-Cutting Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add full-text search, system stats, job history persistence, and log retrieval — four MCP tools that span all content domains.

**Architecture:** PostgreSQL `tsvector` columns with GIN indexes for full-text search. New `SyncJob` entity for job history. `LogService` interface with local file reader and AWS stub. A `CrossCuttingTools` MCP class exposes all four tools.

**Tech Stack:** Spring Boot 4.x, Kotlin 2.x, PostgreSQL FTS (`tsvector`/`to_tsquery`), Logback JSON file appender (`ch.qos.logback.contrib`), Spring AI MCP `@Tool`, JUnit 5, MockK, TestContainers.

**Important:** Use `git commit -m "message"` for all commits — never use `$()`, heredocs, or subshells in commit messages.

**Important:** Spring Boot 4.x uses Jackson 3.x — the auto-configured `ObjectMapper` is `tools.jackson.databind.ObjectMapper`, NOT `com.fasterxml.jackson.databind.ObjectMapper`.

---

### Task 1: Database Migration — sync_jobs Table + FTS Columns

**Files:**
- Create: `src/main/resources/db/migration/V3__search_and_jobs.sql`

**Step 1: Write the migration**

Create the file `src/main/resources/db/migration/V3__search_and_jobs.sql` with this content:

```sql
-- V3__search_and_jobs.sql
-- Job history tracking + full-text search infrastructure

-- ============================================================
-- Sync Jobs (append-only job execution history)
-- ============================================================
CREATE TABLE sync_jobs (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id),
    type            VARCHAR(30)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    error_message   TEXT,
    triggered_by    VARCHAR(20)  NOT NULL,
    metadata        JSONB
);

CREATE INDEX idx_sync_jobs_user_type ON sync_jobs(user_id, type);
CREATE INDEX idx_sync_jobs_started_at ON sync_jobs(started_at DESC);

-- ============================================================
-- Full-Text Search Vectors
-- ============================================================

-- Bookmarks: search by title (weight A) and url (weight B)
ALTER TABLE bookmarks ADD COLUMN search_vector tsvector;

CREATE INDEX idx_bookmarks_search ON bookmarks USING GIN (search_vector);

CREATE OR REPLACE FUNCTION bookmarks_search_trigger() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.url, '')), 'B');
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bookmarks_search
    BEFORE INSERT OR UPDATE OF title, url ON bookmarks
    FOR EACH ROW EXECUTE FUNCTION bookmarks_search_trigger();

-- Feed items: search by title (A), content (B), author (C)
ALTER TABLE feed_items ADD COLUMN search_vector tsvector;

CREATE INDEX idx_feed_items_search ON feed_items USING GIN (search_vector);

CREATE OR REPLACE FUNCTION feed_items_search_trigger() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.content, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(NEW.author, '')), 'C');
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_feed_items_search
    BEFORE INSERT OR UPDATE OF title, content, author ON feed_items
    FOR EACH ROW EXECUTE FUNCTION feed_items_search_trigger();

-- Videos: search by title (A), channel_name (B), description (C)
ALTER TABLE videos ADD COLUMN search_vector tsvector;

CREATE INDEX idx_videos_search ON videos USING GIN (search_vector);

CREATE OR REPLACE FUNCTION videos_search_trigger() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.channel_name, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(NEW.description, '')), 'C');
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_videos_search
    BEFORE INSERT OR UPDATE OF title, channel_name, description ON videos
    FOR EACH ROW EXECUTE FUNCTION videos_search_trigger();

-- ============================================================
-- Backfill search vectors for existing rows
-- ============================================================
UPDATE bookmarks SET search_vector =
    setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(url, '')), 'B');

UPDATE feed_items SET search_vector =
    setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(content, '')), 'B') ||
    setweight(to_tsvector('english', coalesce(author, '')), 'C');

UPDATE videos SET search_vector =
    setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(channel_name, '')), 'B') ||
    setweight(to_tsvector('english', coalesce(description, '')), 'C');
```

**Step 2: Verify the migration applies**

Run: `./gradlew test --tests "*BookmarkIntegrationTest.create and retrieve bookmark" 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL (TestContainers will run the migration)

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V3__search_and_jobs.sql
git commit -m "feat: add V3 migration for sync_jobs table and FTS search vectors"
```

---

### Task 2: SyncJob Entity + Repository

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/scheduling/entity/SyncJob.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/scheduling/repository/SyncJobRepository.kt`

**Step 1: Create the SyncJob entity**

Create `src/main/kotlin/org/sightech/memoryvault/scheduling/entity/SyncJob.kt`:

```kotlin
package org.sightech.memoryvault.scheduling.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "sync_jobs")
class SyncJob(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false, length = 30)
    val type: String,

    @Column(nullable = false, length = 20)
    var status: String = "PENDING",

    @Column(name = "started_at", nullable = false)
    val startedAt: Instant = Instant.now(),

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "triggered_by", nullable = false, length = 20)
    val triggeredBy: String,

    @Column(columnDefinition = "jsonb")
    var metadata: String? = null
)
```

**Step 2: Create the SyncJobRepository**

Create `src/main/kotlin/org/sightech/memoryvault/scheduling/repository/SyncJobRepository.kt`:

```kotlin
package org.sightech.memoryvault.scheduling.repository

import org.sightech.memoryvault.scheduling.entity.SyncJob
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SyncJobRepository : JpaRepository<SyncJob, UUID> {

    @Query("SELECT j FROM SyncJob j WHERE j.userId = :userId ORDER BY j.startedAt DESC LIMIT :limit")
    fun findRecentByUserId(userId: UUID, limit: Int): List<SyncJob>

    @Query("SELECT j FROM SyncJob j WHERE j.userId = :userId AND j.type = :type ORDER BY j.startedAt DESC LIMIT :limit")
    fun findRecentByUserIdAndType(userId: UUID, type: String, limit: Int): List<SyncJob>

    @Query("SELECT j FROM SyncJob j WHERE j.userId = :userId AND j.type = :type AND j.status = 'SUCCESS' ORDER BY j.completedAt DESC LIMIT 1")
    fun findLastSuccessful(userId: UUID, type: String): SyncJob?
}
```

**Step 3: Verify compilation**

Run: `./gradlew compileKotlin 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/scheduling/entity/SyncJob.kt src/main/kotlin/org/sightech/memoryvault/scheduling/repository/SyncJobRepository.kt
git commit -m "feat: add SyncJob entity and repository for job history tracking"
```

---

### Task 3: SyncJobService

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/scheduling/service/SyncJobService.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/scheduling/service/SyncJobServiceTest.kt`

**Step 1: Write the failing test**

Create `src/test/kotlin/org/sightech/memoryvault/scheduling/service/SyncJobServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.scheduling.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.scheduling.entity.SyncJob
import org.sightech.memoryvault.scheduling.repository.SyncJobRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SyncJobServiceTest {

    private val repository = mockk<SyncJobRepository>()
    private val service = SyncJobService(repository)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `recordStart creates a RUNNING job`() {
        val slot = slot<SyncJob>()
        every { repository.save(capture(slot)) } answers { slot.captured }

        val job = service.recordStart("RSS_FETCH", "SCHEDULED", userId)

        assertEquals("RSS_FETCH", job.type)
        assertEquals("RUNNING", job.status)
        assertEquals("SCHEDULED", job.triggeredBy)
        assertEquals(userId, job.userId)
    }

    @Test
    fun `recordSuccess updates status and metadata`() {
        val job = SyncJob(userId = userId, type = "YT_SYNC", triggeredBy = "MANUAL")
        job.status = "RUNNING"
        every { repository.findById(job.id) } returns java.util.Optional.of(job)
        every { repository.save(any()) } answers { firstArg() }

        service.recordSuccess(job.id, mapOf("newVideos" to 3))

        assertEquals("SUCCESS", job.status)
        assertNotNull(job.completedAt)
        assertNotNull(job.metadata)
    }

    @Test
    fun `recordFailure sets error message`() {
        val job = SyncJob(userId = userId, type = "RSS_FETCH", triggeredBy = "SCHEDULED")
        job.status = "RUNNING"
        every { repository.findById(job.id) } returns java.util.Optional.of(job)
        every { repository.save(any()) } answers { firstArg() }

        service.recordFailure(job.id, "Connection refused")

        assertEquals("FAILED", job.status)
        assertEquals("Connection refused", job.errorMessage)
        assertNotNull(job.completedAt)
    }

    @Test
    fun `listJobs delegates to repository`() {
        every { repository.findRecentByUserId(userId, 20) } returns listOf(
            SyncJob(userId = userId, type = "RSS_FETCH", triggeredBy = "SCHEDULED")
        )

        val result = service.listJobs(userId, null, 20)
        assertEquals(1, result.size)
    }

    @Test
    fun `listJobs with type filter`() {
        every { repository.findRecentByUserIdAndType(userId, "YT_SYNC", 10) } returns emptyList()

        val result = service.listJobs(userId, "YT_SYNC", 10)
        assertEquals(0, result.size)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*SyncJobServiceTest" 2>&1 | tail -10`

Expected: FAIL (SyncJobService class does not exist)

**Step 3: Implement SyncJobService**

Create `src/main/kotlin/org/sightech/memoryvault/scheduling/service/SyncJobService.kt`:

```kotlin
package org.sightech.memoryvault.scheduling.service

import org.sightech.memoryvault.scheduling.entity.SyncJob
import org.sightech.memoryvault.scheduling.repository.SyncJobRepository
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@Service
class SyncJobService(
    private val syncJobRepository: SyncJobRepository
) {

    private val objectMapper = ObjectMapper()

    fun recordStart(type: String, triggeredBy: String, userId: UUID): SyncJob {
        val job = SyncJob(
            userId = userId,
            type = type,
            triggeredBy = triggeredBy
        )
        job.status = "RUNNING"
        return syncJobRepository.save(job)
    }

    fun recordSuccess(jobId: UUID, metadata: Map<String, Any>?) {
        val job = syncJobRepository.findById(jobId).orElse(null) ?: return
        job.status = "SUCCESS"
        job.completedAt = Instant.now()
        if (metadata != null) {
            job.metadata = objectMapper.writeValueAsString(metadata)
        }
        syncJobRepository.save(job)
    }

    fun recordFailure(jobId: UUID, error: String) {
        val job = syncJobRepository.findById(jobId).orElse(null) ?: return
        job.status = "FAILED"
        job.completedAt = Instant.now()
        job.errorMessage = error
        syncJobRepository.save(job)
    }

    fun listJobs(userId: UUID, type: String?, limit: Int): List<SyncJob> {
        return if (type != null) {
            syncJobRepository.findRecentByUserIdAndType(userId, type, limit)
        } else {
            syncJobRepository.findRecentByUserId(userId, limit)
        }
    }

    fun findLastSuccessful(userId: UUID, type: String): SyncJob? {
        return syncJobRepository.findLastSuccessful(userId, type)
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*SyncJobServiceTest" 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/scheduling/service/SyncJobService.kt src/test/kotlin/org/sightech/memoryvault/scheduling/service/SyncJobServiceTest.kt
git commit -m "feat: add SyncJobService for recording and querying job history"
```

---

### Task 4: SearchService

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/search/SearchService.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/search/SearchServiceTest.kt`

**Step 1: Write the failing test**

Create `src/test/kotlin/org/sightech/memoryvault/search/SearchServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.search

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchServiceTest {

    private val searchRepository = mockk<SearchRepository>()
    private val service = SearchService(searchRepository)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `search returns results from all types`() {
        every { searchRepository.searchBookmarks(any(), any(), any()) } returns listOf(
            SearchResult(ContentType.BOOKMARK, UUID.randomUUID(), "Kotlin Docs", "https://kotlinlang.org", 0.8f)
        )
        every { searchRepository.searchFeedItems(any(), any(), any()) } returns listOf(
            SearchResult(ContentType.FEED_ITEM, UUID.randomUUID(), "Kotlin 2.0 Released", "https://blog.example.com", 0.6f)
        )
        every { searchRepository.searchVideos(any(), any(), any()) } returns listOf(
            SearchResult(ContentType.VIDEO, UUID.randomUUID(), "Kotlin Tutorial", "https://youtube.com/watch?v=abc", 0.5f)
        )

        val results = service.search("kotlin", null, userId, 20)
        assertEquals(3, results.size)
        // Results should be sorted by rank descending
        assertTrue(results[0].rank >= results[1].rank)
        assertTrue(results[1].rank >= results[2].rank)
    }

    @Test
    fun `search filters by content type`() {
        every { searchRepository.searchBookmarks(any(), any(), any()) } returns listOf(
            SearchResult(ContentType.BOOKMARK, UUID.randomUUID(), "Result", "url", 0.9f)
        )

        val results = service.search("test", listOf(ContentType.BOOKMARK), userId, 20)
        assertEquals(1, results.size)
        assertEquals(ContentType.BOOKMARK, results[0].type)
    }

    @Test
    fun `search with no types queries all`() {
        every { searchRepository.searchBookmarks(any(), any(), any()) } returns emptyList()
        every { searchRepository.searchFeedItems(any(), any(), any()) } returns emptyList()
        every { searchRepository.searchVideos(any(), any(), any()) } returns emptyList()

        val results = service.search("nothing", null, userId, 20)
        assertEquals(0, results.size)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*SearchServiceTest" 2>&1 | tail -10`

Expected: FAIL (classes do not exist)

**Step 3: Implement SearchService, SearchResult, ContentType, and SearchRepository**

Create `src/main/kotlin/org/sightech/memoryvault/search/SearchService.kt`:

```kotlin
package org.sightech.memoryvault.search

import org.springframework.stereotype.Service
import java.util.UUID

enum class ContentType { BOOKMARK, FEED_ITEM, VIDEO }

data class SearchResult(
    val type: ContentType,
    val id: UUID,
    val title: String?,
    val url: String?,
    val rank: Float
)

@Service
class SearchService(private val searchRepository: SearchRepository) {

    fun search(query: String, types: List<ContentType>?, userId: UUID, limit: Int): List<SearchResult> {
        val searchTypes = types ?: ContentType.entries

        val results = mutableListOf<SearchResult>()

        if (ContentType.BOOKMARK in searchTypes) {
            results.addAll(searchRepository.searchBookmarks(query, userId, limit))
        }
        if (ContentType.FEED_ITEM in searchTypes) {
            results.addAll(searchRepository.searchFeedItems(query, userId, limit))
        }
        if (ContentType.VIDEO in searchTypes) {
            results.addAll(searchRepository.searchVideos(query, userId, limit))
        }

        return results.sortedByDescending { it.rank }.take(limit)
    }
}
```

**Step 4: Create the SearchRepository**

Create `src/main/kotlin/org/sightech/memoryvault/search/SearchRepository.kt`:

```kotlin
package org.sightech.memoryvault.search

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class SearchRepository(private val entityManager: EntityManager) {

    fun searchBookmarks(query: String, userId: UUID, limit: Int): List<SearchResult> {
        val tsQuery = toTsQuery(query)
        val sql = """
            SELECT id, title, url, ts_rank(search_vector, to_tsquery('english', :query)) AS rank
            FROM bookmarks
            WHERE user_id = :userId AND deleted_at IS NULL
              AND search_vector @@ to_tsquery('english', :query)
            ORDER BY rank DESC
            LIMIT :limit
        """.trimIndent()

        return executeSearch(sql, tsQuery, userId, limit, ContentType.BOOKMARK)
    }

    fun searchFeedItems(query: String, userId: UUID, limit: Int): List<SearchResult> {
        val tsQuery = toTsQuery(query)
        val sql = """
            SELECT fi.id, fi.title, fi.url, ts_rank(fi.search_vector, to_tsquery('english', :query)) AS rank
            FROM feed_items fi
            JOIN feeds f ON fi.feed_id = f.id
            WHERE f.user_id = :userId AND f.deleted_at IS NULL
              AND fi.search_vector @@ to_tsquery('english', :query)
            ORDER BY rank DESC
            LIMIT :limit
        """.trimIndent()

        return executeSearch(sql, tsQuery, userId, limit, ContentType.FEED_ITEM)
    }

    fun searchVideos(query: String, userId: UUID, limit: Int): List<SearchResult> {
        val tsQuery = toTsQuery(query)
        val sql = """
            SELECT v.id, v.title, v.youtube_url AS url, ts_rank(v.search_vector, to_tsquery('english', :query)) AS rank
            FROM videos v
            JOIN youtube_lists yl ON v.youtube_list_id = yl.id
            WHERE yl.user_id = :userId AND yl.deleted_at IS NULL
              AND v.search_vector @@ to_tsquery('english', :query)
            ORDER BY rank DESC
            LIMIT :limit
        """.trimIndent()

        return executeSearch(sql, tsQuery, userId, limit, ContentType.VIDEO)
    }

    private fun executeSearch(sql: String, query: String, userId: UUID, limit: Int, type: ContentType): List<SearchResult> {
        @Suppress("UNCHECKED_CAST")
        val results = entityManager.createNativeQuery(sql)
            .setParameter("query", query)
            .setParameter("userId", userId)
            .setParameter("limit", limit)
            .resultList as List<Array<Any>>

        return results.map { row ->
            SearchResult(
                type = type,
                id = row[0] as UUID,
                title = row[1] as? String,
                url = row[2] as? String,
                rank = (row[3] as Number).toFloat()
            )
        }
    }

    private fun toTsQuery(input: String): String {
        // Split on whitespace, join with & for AND semantics
        return input.trim().split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" & ")
    }
}
```

**Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "*SearchServiceTest" 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/search/SearchService.kt src/main/kotlin/org/sightech/memoryvault/search/SearchRepository.kt src/test/kotlin/org/sightech/memoryvault/search/SearchServiceTest.kt
git commit -m "feat: add SearchService with PostgreSQL FTS across bookmarks, feed items, and videos"
```

---

### Task 5: StatsService

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/stats/StatsService.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/stats/StatsServiceTest.kt`

**Step 1: Write the failing test**

Create `src/test/kotlin/org/sightech/memoryvault/stats/StatsServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.stats

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.bookmark.repository.BookmarkRepository
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.sightech.memoryvault.scheduling.entity.SyncJob
import org.sightech.memoryvault.scheduling.service.SyncJobService
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.tag.repository.TagRepository
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.sightech.memoryvault.youtube.repository.YoutubeListRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class StatsServiceTest {

    private val bookmarkRepository = mockk<BookmarkRepository>()
    private val feedRepository = mockk<FeedRepository>()
    private val feedItemRepository = mockk<FeedItemRepository>()
    private val youtubeListRepository = mockk<YoutubeListRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val tagRepository = mockk<TagRepository>()
    private val syncJobService = mockk<SyncJobService>()
    private val storageService = mockk<StorageService>()

    private val service = StatsService(
        bookmarkRepository, feedRepository, feedItemRepository,
        youtubeListRepository, videoRepository, tagRepository,
        syncJobService, storageService
    )

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `getStats returns correct counts`() {
        every { bookmarkRepository.countByUserIdAndDeletedAtIsNull(userId) } returns 10
        every { feedRepository.countByUserIdAndDeletedAtIsNull(userId) } returns 3
        every { feedItemRepository.countByFeedUserIdAndFeedDeletedAtIsNull(userId) } returns 150
        every { feedItemRepository.countByFeedUserIdAndFeedDeletedAtIsNullAndReadAtIsNull(userId) } returns 42
        every { youtubeListRepository.countByUserIdAndDeletedAtIsNull(userId) } returns 2
        every { videoRepository.countByYoutubeListUserIdAndYoutubeListDeletedAtIsNull(userId) } returns 75
        every { videoRepository.countByYoutubeListUserIdAndYoutubeListDeletedAtIsNullAndDownloadedAtIsNotNull(userId) } returns 60
        every { videoRepository.countByYoutubeListUserIdAndYoutubeListDeletedAtIsNullAndRemovedFromYoutubeTrue(userId) } returns 5
        every { tagRepository.countByUserId(userId) } returns 12
        every { storageService.usedBytes() } returns 1_073_741_824L
        every { syncJobService.findLastSuccessful(userId, "RSS_FETCH") } returns SyncJob(
            userId = userId, type = "RSS_FETCH", triggeredBy = "SCHEDULED"
        ).apply { completedAt = Instant.parse("2026-03-05T10:00:00Z") }
        every { syncJobService.findLastSuccessful(userId, "YT_SYNC") } returns null
        every { feedRepository.countByUserIdAndDeletedAtIsNullAndFailureCountGreaterThan(userId, 0) } returns 1
        every { youtubeListRepository.countByUserIdAndDeletedAtIsNullAndFailureCountGreaterThan(userId, 0) } returns 0

        val stats = service.getStats(userId)

        assertEquals(10, stats.bookmarkCount)
        assertEquals(3, stats.feedCount)
        assertEquals(150, stats.feedItemCount)
        assertEquals(42, stats.unreadFeedItemCount)
        assertEquals(2, stats.youtubeListCount)
        assertEquals(75, stats.videoCount)
        assertEquals(60, stats.downloadedVideoCount)
        assertEquals(5, stats.removedVideoCount)
        assertEquals(12, stats.tagCount)
        assertEquals(1_073_741_824L, stats.storageUsedBytes)
        assertEquals(1, stats.feedsWithFailures)
        assertEquals(0, stats.youtubeListsWithFailures)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*StatsServiceTest" 2>&1 | tail -10`

Expected: FAIL (StatsService does not exist)

**Step 3: Add count queries to repositories that don't have them yet**

Add to `src/main/kotlin/org/sightech/memoryvault/bookmark/repository/BookmarkRepository.kt`:

```kotlin
    fun countByUserIdAndDeletedAtIsNull(userId: UUID): Long
```

Add to `src/main/kotlin/org/sightech/memoryvault/feed/repository/FeedRepository.kt`:

```kotlin
    fun countByUserIdAndDeletedAtIsNull(userId: UUID): Long

    fun countByUserIdAndDeletedAtIsNullAndFailureCountGreaterThan(userId: UUID, failureCount: Int): Long
```

Add to `src/main/kotlin/org/sightech/memoryvault/feed/repository/FeedItemRepository.kt`:

```kotlin
    fun countByFeedUserIdAndFeedDeletedAtIsNull(userId: UUID): Long

    fun countByFeedUserIdAndFeedDeletedAtIsNullAndReadAtIsNull(userId: UUID): Long
```

Add to `src/main/kotlin/org/sightech/memoryvault/youtube/repository/YoutubeListRepository.kt`:

```kotlin
    fun countByUserIdAndDeletedAtIsNull(userId: UUID): Long

    fun countByUserIdAndDeletedAtIsNullAndFailureCountGreaterThan(userId: UUID, failureCount: Int): Long
```

Add to `src/main/kotlin/org/sightech/memoryvault/youtube/repository/VideoRepository.kt`:

```kotlin
    fun countByYoutubeListUserIdAndYoutubeListDeletedAtIsNull(userId: UUID): Long

    fun countByYoutubeListUserIdAndYoutubeListDeletedAtIsNullAndDownloadedAtIsNotNull(userId: UUID): Long

    fun countByYoutubeListUserIdAndYoutubeListDeletedAtIsNullAndRemovedFromYoutubeTrue(userId: UUID): Long
```

Add to `src/main/kotlin/org/sightech/memoryvault/tag/repository/TagRepository.kt`:

```kotlin
    fun countByUserId(userId: UUID): Long
```

Add to `src/main/kotlin/org/sightech/memoryvault/storage/StorageService.kt`:

```kotlin
    fun usedBytes(): Long
```

Implement `usedBytes()` in `src/main/kotlin/org/sightech/memoryvault/storage/LocalStorageService.kt`:

```kotlin
    override fun usedBytes(): Long {
        val base = Path.of(basePath)
        if (!Files.exists(base)) return 0
        return Files.walk(base)
            .filter { Files.isRegularFile(it) }
            .mapToLong { Files.size(it) }
            .sum()
    }
```

Add stub to `src/main/kotlin/org/sightech/memoryvault/storage/S3StorageService.kt`:

```kotlin
    // TODO: Phase 6 — use CloudWatch GetMetricData for BucketSizeBytes,
    //  or S3 inventory reports for bucket-level size tracking.
    override fun usedBytes(): Long {
        logger.warn("S3StorageService.usedBytes() is a stub — AWS implementation pending (Phase 6)")
        return 0
    }
```

**Step 4: Implement StatsService**

Create `src/main/kotlin/org/sightech/memoryvault/stats/StatsService.kt`:

```kotlin
package org.sightech.memoryvault.stats

import org.sightech.memoryvault.bookmark.repository.BookmarkRepository
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.sightech.memoryvault.scheduling.service.SyncJobService
import org.sightech.memoryvault.storage.StorageService
import org.sightech.memoryvault.tag.repository.TagRepository
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.sightech.memoryvault.youtube.repository.YoutubeListRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

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

@Service
class StatsService(
    private val bookmarkRepository: BookmarkRepository,
    private val feedRepository: FeedRepository,
    private val feedItemRepository: FeedItemRepository,
    private val youtubeListRepository: YoutubeListRepository,
    private val videoRepository: VideoRepository,
    private val tagRepository: TagRepository,
    private val syncJobService: SyncJobService,
    private val storageService: StorageService
) {

    // TODO: Phase 6 — add AWS stats: S3 bucket size, Lambda invocation counts/error rates,
    //  AWS cost per billing cycle (compute, storage, transfer) via Cost Explorer API,
    //  RDS connection/query metrics.

    fun getStats(userId: UUID): SystemStats {
        val lastFeedSync = syncJobService.findLastSuccessful(userId, "RSS_FETCH")?.completedAt
        val lastYoutubeSync = syncJobService.findLastSuccessful(userId, "YT_SYNC")?.completedAt

        return SystemStats(
            bookmarkCount = bookmarkRepository.countByUserIdAndDeletedAtIsNull(userId),
            feedCount = feedRepository.countByUserIdAndDeletedAtIsNull(userId),
            feedItemCount = feedItemRepository.countByFeedUserIdAndFeedDeletedAtIsNull(userId),
            unreadFeedItemCount = feedItemRepository.countByFeedUserIdAndFeedDeletedAtIsNullAndReadAtIsNull(userId),
            youtubeListCount = youtubeListRepository.countByUserIdAndDeletedAtIsNull(userId),
            videoCount = videoRepository.countByYoutubeListUserIdAndYoutubeListDeletedAtIsNull(userId),
            downloadedVideoCount = videoRepository.countByYoutubeListUserIdAndYoutubeListDeletedAtIsNullAndDownloadedAtIsNotNull(userId),
            removedVideoCount = videoRepository.countByYoutubeListUserIdAndYoutubeListDeletedAtIsNullAndRemovedFromYoutubeTrue(userId),
            tagCount = tagRepository.countByUserId(userId),
            storageUsedBytes = storageService.usedBytes(),
            lastFeedSync = lastFeedSync,
            lastYoutubeSync = lastYoutubeSync,
            feedsWithFailures = feedRepository.countByUserIdAndDeletedAtIsNullAndFailureCountGreaterThan(userId, 0),
            youtubeListsWithFailures = youtubeListRepository.countByUserIdAndDeletedAtIsNullAndFailureCountGreaterThan(userId, 0)
        )
    }
}
```

**Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "*StatsServiceTest" 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/stats/StatsService.kt src/test/kotlin/org/sightech/memoryvault/stats/StatsServiceTest.kt src/main/kotlin/org/sightech/memoryvault/bookmark/repository/BookmarkRepository.kt src/main/kotlin/org/sightech/memoryvault/feed/repository/FeedRepository.kt src/main/kotlin/org/sightech/memoryvault/feed/repository/FeedItemRepository.kt src/main/kotlin/org/sightech/memoryvault/youtube/repository/YoutubeListRepository.kt src/main/kotlin/org/sightech/memoryvault/youtube/repository/VideoRepository.kt src/main/kotlin/org/sightech/memoryvault/tag/repository/TagRepository.kt src/main/kotlin/org/sightech/memoryvault/storage/StorageService.kt src/main/kotlin/org/sightech/memoryvault/storage/LocalStorageService.kt src/main/kotlin/org/sightech/memoryvault/storage/S3StorageService.kt
git commit -m "feat: add StatsService with counts across all domains and storage usage"
```

---

### Task 6: Logback JSON Config + LogService

**Files:**
- Create: `src/main/resources/logback-spring.xml`
- Modify: `src/main/resources/application.properties` (add logging path property)
- Create: `src/main/kotlin/org/sightech/memoryvault/logging/LogService.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/logging/LocalLogService.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/logging/CloudWatchLogService.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/logging/LocalLogServiceTest.kt`

**Step 1: Add logback dependency to build.gradle.kts**

Add to the `dependencies` block in `build.gradle.kts`:

```kotlin
    // Structured JSON logging
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
```

**Step 2: Create logback-spring.xml**

Create `src/main/resources/logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>

    <property name="LOG_PATH" value="${memoryvault.logging.path:-${user.home}/.memoryvault/logs}"/>
    <property name="LOG_FILE" value="${LOG_PATH}/memoryvault.log"/>

    <!-- Console: human-readable for dev -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %highlight(%-5level) [%thread] %cyan(%logger{36}) - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- File: JSON structured, rolling daily, max 30 days -->
    <appender name="JSON_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_FILE}</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/memoryvault.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <fieldNames>
                <timestamp>timestamp</timestamp>
                <level>level</level>
                <logger>logger</logger>
                <message>message</message>
                <thread>thread</thread>
            </fieldNames>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="JSON_FILE"/>
    </root>

</configuration>
```

**Step 3: Add logging path property to application.properties**

Add this line to `src/main/resources/application.properties`:

```properties
# Log file path for structured JSON logs
memoryvault.logging.path=${user.home}/.memoryvault/logs
```

**Step 4: Write the failing test**

Create `src/test/kotlin/org/sightech/memoryvault/logging/LocalLogServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.logging

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalLogServiceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `getLogs parses JSON log lines`() {
        val logFile = tempDir.resolve("memoryvault.log")
        Files.writeString(logFile, buildString {
            appendLine("""{"timestamp":"2026-03-05T10:00:00.000Z","level":"INFO","logger":"org.sightech.memoryvault.feed.FeedService","message":"Feed sync started","thread":"main"}""")
            appendLine("""{"timestamp":"2026-03-05T10:00:01.000Z","level":"ERROR","logger":"org.sightech.memoryvault.youtube.YtDlpService","message":"Download failed","thread":"scheduler-1"}""")
        })

        val service = LocalLogService(logFile.toString())
        val logs = service.getLogs(null, null, 50)

        assertEquals(2, logs.size)
        assertEquals("ERROR", logs[0].level)  // Most recent first
        assertEquals("INFO", logs[1].level)
    }

    @Test
    fun `getLogs filters by level`() {
        val logFile = tempDir.resolve("memoryvault.log")
        Files.writeString(logFile, buildString {
            appendLine("""{"timestamp":"2026-03-05T10:00:00.000Z","level":"INFO","logger":"test","message":"info msg","thread":"main"}""")
            appendLine("""{"timestamp":"2026-03-05T10:00:01.000Z","level":"ERROR","logger":"test","message":"error msg","thread":"main"}""")
        })

        val service = LocalLogService(logFile.toString())
        val logs = service.getLogs("ERROR", null, 50)

        assertEquals(1, logs.size)
        assertEquals("error msg", logs[0].message)
    }

    @Test
    fun `getLogs filters by logger`() {
        val logFile = tempDir.resolve("memoryvault.log")
        Files.writeString(logFile, buildString {
            appendLine("""{"timestamp":"2026-03-05T10:00:00.000Z","level":"INFO","logger":"org.sightech.memoryvault.feed.FeedService","message":"feed msg","thread":"main"}""")
            appendLine("""{"timestamp":"2026-03-05T10:00:01.000Z","level":"INFO","logger":"org.sightech.memoryvault.youtube.YtDlpService","message":"youtube msg","thread":"main"}""")
        })

        val service = LocalLogService(logFile.toString())
        val logs = service.getLogs(null, "feed", 50)

        assertEquals(1, logs.size)
        assertEquals("feed msg", logs[0].message)
    }

    @Test
    fun `getLogs respects limit`() {
        val logFile = tempDir.resolve("memoryvault.log")
        Files.writeString(logFile, buildString {
            repeat(10) { i ->
                appendLine("""{"timestamp":"2026-03-05T10:00:0$i.000Z","level":"INFO","logger":"test","message":"msg $i","thread":"main"}""")
            }
        })

        val service = LocalLogService(logFile.toString())
        val logs = service.getLogs(null, null, 3)

        assertEquals(3, logs.size)
    }

    @Test
    fun `getLogs returns empty when file does not exist`() {
        val service = LocalLogService(tempDir.resolve("nonexistent.log").toString())
        val logs = service.getLogs(null, null, 50)

        assertTrue(logs.isEmpty())
    }
}
```

**Step 5: Run test to verify it fails**

Run: `./gradlew test --tests "*LocalLogServiceTest" 2>&1 | tail -10`

Expected: FAIL (classes do not exist)

**Step 6: Implement LogService interface, LogEntry, LocalLogService, and CloudWatchLogService**

Create `src/main/kotlin/org/sightech/memoryvault/logging/LogService.kt`:

```kotlin
package org.sightech.memoryvault.logging

import java.time.Instant

data class LogEntry(
    val timestamp: Instant,
    val level: String,
    val logger: String,
    val message: String,
    val thread: String
)

interface LogService {
    fun getLogs(level: String?, logger: String?, limit: Int?): List<LogEntry>
}
```

Create `src/main/kotlin/org/sightech/memoryvault/logging/LocalLogService.kt`:

```kotlin
package org.sightech.memoryvault.logging

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@Component
@Profile("!aws")
class LocalLogService(
    @Value("\${memoryvault.logging.path:\${user.home}/.memoryvault/logs}/memoryvault.log")
    private val logFilePath: String
) : LogService {

    private val objectMapper = ObjectMapper()

    override fun getLogs(level: String?, logger: String?, limit: Int?): List<LogEntry> {
        val path = Path.of(logFilePath)
        if (!Files.exists(path)) return emptyList()

        val effectiveLimit = limit ?: 50

        return Files.readAllLines(path)
            .asReversed()
            .asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line -> parseLine(line) }
            .filter { entry ->
                (level == null || entry.level.equals(level, ignoreCase = true)) &&
                    (logger == null || entry.logger.contains(logger, ignoreCase = true))
            }
            .take(effectiveLimit)
            .toList()
    }

    private fun parseLine(line: String): LogEntry? {
        return try {
            val node = objectMapper.readTree(line)
            LogEntry(
                timestamp = Instant.parse(node.get("timestamp").asText()),
                level = node.get("level").asText(),
                logger = node.get("logger").asText(),
                message = node.get("message").asText(),
                thread = node.get("thread").asText()
            )
        } catch (_: Exception) {
            null
        }
    }
}
```

Create `src/main/kotlin/org/sightech/memoryvault/logging/CloudWatchLogService.kt`:

```kotlin
package org.sightech.memoryvault.logging

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

// AWS CloudWatch Logs implementation of LogService.
//
// When activated (spring.profiles.active=aws), this replaces LocalLogService.
//
// Implementation notes for Phase 6:
// - Use AWS SDK v2 CloudWatchLogsClient (software.amazon.awssdk:cloudwatchlogs)
// - Configure via properties:
//     memoryvault.logging.cloudwatch-log-group=/memoryvault/application
//     memoryvault.logging.cloudwatch-region=us-east-1
//
// - getLogs() should use CloudWatch Logs Insights:
//     CloudWatchLogsClient.startQuery(StartQueryRequest.builder()
//         .logGroupName(logGroupName)
//         .startTime(...)
//         .endTime(...)
//         .queryString("fields @timestamp, @message | sort @timestamp desc | limit $limit")
//         .build())
//
// - Then poll with getQueryResults() until status is COMPLETE
// - Parse results into LogEntry objects
// - AWS creates its own logs from the application's stdout/stderr — this service
//   retrieves those logs back for viewing via MCP tool

@Component
@Profile("aws")
class CloudWatchLogService : LogService {

    private val logger = LoggerFactory.getLogger(CloudWatchLogService::class.java)

    override fun getLogs(level: String?, logger: String?, limit: Int?): List<LogEntry> {
        this.logger.warn("CloudWatchLogService.getLogs() is a stub — AWS implementation pending (Phase 6)")
        return emptyList()
    }
}
```

**Step 7: Run test to verify it passes**

Run: `./gradlew test --tests "*LocalLogServiceTest" 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

**Step 8: Commit**

```bash
git add build.gradle.kts src/main/resources/logback-spring.xml src/main/resources/application.properties src/main/kotlin/org/sightech/memoryvault/logging/LogService.kt src/main/kotlin/org/sightech/memoryvault/logging/LocalLogService.kt src/main/kotlin/org/sightech/memoryvault/logging/CloudWatchLogService.kt src/test/kotlin/org/sightech/memoryvault/logging/LocalLogServiceTest.kt
git commit -m "feat: add LogService with JSON file reader and CloudWatch stub"
```

---

### Task 7: CrossCuttingTools MCP

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/mcp/CrossCuttingTools.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/mcp/CrossCuttingToolsTest.kt`

**Step 1: Write the failing test**

Create `src/test/kotlin/org/sightech/memoryvault/mcp/CrossCuttingToolsTest.kt`:

```kotlin
package org.sightech.memoryvault.mcp

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.logging.LogEntry
import org.sightech.memoryvault.logging.LogService
import org.sightech.memoryvault.scheduling.entity.SyncJob
import org.sightech.memoryvault.scheduling.service.SyncJobService
import org.sightech.memoryvault.search.ContentType
import org.sightech.memoryvault.search.SearchResult
import org.sightech.memoryvault.search.SearchService
import org.sightech.memoryvault.stats.StatsService
import org.sightech.memoryvault.stats.SystemStats
import java.time.Instant
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CrossCuttingToolsTest {

    private val searchService = mockk<SearchService>()
    private val statsService = mockk<StatsService>()
    private val syncJobService = mockk<SyncJobService>()
    private val logService = mockk<LogService>()

    private val tools = CrossCuttingTools(searchService, statsService, syncJobService, logService)

    @Test
    fun `search returns formatted results`() {
        every { searchService.search("kotlin", null, any(), 20) } returns listOf(
            SearchResult(ContentType.BOOKMARK, UUID.randomUUID(), "Kotlin Docs", "https://kotlinlang.org", 0.8f),
            SearchResult(ContentType.FEED_ITEM, UUID.randomUUID(), "Kotlin 2.0", "https://blog.example.com", 0.6f)
        )

        val result = tools.search("kotlin", null)
        assertContains(result, "2 result(s)")
        assertContains(result, "[BOOKMARK]")
        assertContains(result, "Kotlin Docs")
        assertContains(result, "[FEED_ITEM]")
    }

    @Test
    fun `search returns no results message`() {
        every { searchService.search("nonexistent", null, any(), 20) } returns emptyList()
        assertEquals("No results found.", tools.search("nonexistent", null))
    }

    @Test
    fun `search with type filter`() {
        every { searchService.search("test", listOf(ContentType.VIDEO), any(), 20) } returns listOf(
            SearchResult(ContentType.VIDEO, UUID.randomUUID(), "Test Video", "url", 0.5f)
        )

        val result = tools.search("test", "VIDEO")
        assertContains(result, "[VIDEO]")
    }

    @Test
    fun `getStats returns formatted summary`() {
        every { statsService.getStats(any()) } returns SystemStats(
            bookmarkCount = 42,
            feedCount = 5,
            feedItemCount = 1200,
            unreadFeedItemCount = 87,
            youtubeListCount = 3,
            videoCount = 150,
            downloadedVideoCount = 140,
            removedVideoCount = 8,
            tagCount = 15,
            storageUsedBytes = 5_368_709_120,
            lastFeedSync = Instant.parse("2026-03-05T10:00:00Z"),
            lastYoutubeSync = Instant.parse("2026-03-05T08:00:00Z"),
            feedsWithFailures = 1,
            youtubeListsWithFailures = 0
        )

        val result = tools.getStats()
        assertContains(result, "42 bookmarks")
        assertContains(result, "87 unread")
        assertContains(result, "140/150 downloaded")
        assertContains(result, "8 removed")
        assertContains(result, "5.0 GB")
        assertContains(result, "15 tags")
    }

    @Test
    fun `listJobs returns formatted history`() {
        every { syncJobService.listJobs(any(), null, 10) } returns listOf(
            SyncJob(userId = UUID.randomUUID(), type = "RSS_FETCH", triggeredBy = "SCHEDULED").apply {
                status = "SUCCESS"
                completedAt = Instant.parse("2026-03-05T10:00:00Z")
                metadata = """{"newItems": 12}"""
            },
            SyncJob(userId = UUID.randomUUID(), type = "YT_SYNC", triggeredBy = "MANUAL").apply {
                status = "FAILED"
                completedAt = Instant.parse("2026-03-05T09:00:00Z")
                errorMessage = "Connection timeout"
            }
        )

        val result = tools.listJobs(null, null)
        assertContains(result, "RSS_FETCH")
        assertContains(result, "SUCCESS")
        assertContains(result, "FAILED")
        assertContains(result, "Connection timeout")
    }

    @Test
    fun `listJobs returns no jobs message`() {
        every { syncJobService.listJobs(any(), any(), any()) } returns emptyList()
        assertEquals("No job history found.", tools.listJobs(null, null))
    }

    @Test
    fun `getLogs returns formatted entries`() {
        every { logService.getLogs("ERROR", null, 10) } returns listOf(
            LogEntry(
                timestamp = Instant.parse("2026-03-05T10:00:01Z"),
                level = "ERROR",
                logger = "org.sightech.memoryvault.youtube.YtDlpService",
                message = "Download failed: connection refused",
                thread = "scheduler-1"
            )
        )

        val result = tools.getLogs("ERROR", null, 10)
        assertContains(result, "ERROR")
        assertContains(result, "YtDlpService")
        assertContains(result, "Download failed")
    }

    @Test
    fun `getLogs returns no logs message`() {
        every { logService.getLogs(null, null, 50) } returns emptyList()
        assertEquals("No log entries found.", tools.getLogs(null, null, null))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*CrossCuttingToolsTest" 2>&1 | tail -10`

Expected: FAIL (CrossCuttingTools does not exist)

**Step 3: Implement CrossCuttingTools**

Create `src/main/kotlin/org/sightech/memoryvault/mcp/CrossCuttingTools.kt`:

```kotlin
package org.sightech.memoryvault.mcp

import org.sightech.memoryvault.logging.LogService
import org.sightech.memoryvault.scheduling.service.SyncJobService
import org.sightech.memoryvault.search.ContentType
import org.sightech.memoryvault.search.SearchService
import org.sightech.memoryvault.stats.StatsService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CrossCuttingTools(
    private val searchService: SearchService,
    private val statsService: StatsService,
    private val syncJobService: SyncJobService,
    private val logService: LogService
) {

    companion object {
        val SYSTEM_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    @Tool(description = "Search across all content — bookmarks, feed items, and videos. Returns ranked results. Optionally filter by type: BOOKMARK, FEED_ITEM, VIDEO (comma-separated for multiple).")
    fun search(query: String, types: String?): String {
        val typeList = types?.split(",")?.map { it.trim().uppercase() }?.map { ContentType.valueOf(it) }

        val results = searchService.search(query, typeList, SYSTEM_USER_ID, 20)
        if (results.isEmpty()) return "No results found."

        val lines = results.map { r ->
            "- [${r.type}] ${r.title ?: "(no title)"} — ${r.url ?: "no url"} (rank: ${"%.2f".format(r.rank)})"
        }
        return "${results.size} result(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Get system statistics — content counts, storage usage, sync health, and failure counts. Use when the user wants an overview of their MemoryVault.")
    fun getStats(): String {
        val stats = statsService.getStats(SYSTEM_USER_ID)

        val storageStr = formatBytes(stats.storageUsedBytes)

        val lines = mutableListOf<String>()
        lines.add("Content:")
        lines.add("  ${stats.bookmarkCount} bookmarks")
        lines.add("  ${stats.feedCount} feeds, ${stats.feedItemCount} items (${stats.unreadFeedItemCount} unread)")
        lines.add("  ${stats.youtubeListCount} playlists, ${stats.downloadedVideoCount}/${stats.videoCount} downloaded, ${stats.removedVideoCount} removed")
        lines.add("  ${stats.tagCount} tags")
        lines.add("")
        lines.add("Storage: $storageStr")
        lines.add("")
        lines.add("Sync health:")
        lines.add("  Last feed sync: ${stats.lastFeedSync ?: "never"}")
        lines.add("  Last YouTube sync: ${stats.lastYoutubeSync ?: "never"}")
        if (stats.feedsWithFailures > 0) lines.add("  ${stats.feedsWithFailures} feed(s) with failures")
        if (stats.youtubeListsWithFailures > 0) lines.add("  ${stats.youtubeListsWithFailures} playlist(s) with failures")

        return lines.joinToString("\n")
    }

    @Tool(description = "View job execution history. Shows recent sync job runs with status, timing, and metadata. Optionally filter by type (RSS_FETCH, YT_SYNC, BOOKMARK_ARCHIVE) and limit results.")
    fun listJobs(type: String?, limit: Int?): String {
        val effectiveLimit = limit ?: 10
        val jobs = syncJobService.listJobs(SYSTEM_USER_ID, type, effectiveLimit)
        if (jobs.isEmpty()) return "No job history found."

        val lines = jobs.map { job ->
            val duration = if (job.completedAt != null) {
                val secs = java.time.Duration.between(job.startedAt, job.completedAt).seconds
                " (${secs}s)"
            } else ""
            val error = if (job.errorMessage != null) " — ${job.errorMessage}" else ""
            val meta = if (job.metadata != null) " ${job.metadata}" else ""
            "- [${job.status}] ${job.type} (${job.triggeredBy}) at ${job.startedAt}$duration$error$meta"
        }
        return "${jobs.size} job(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Retrieve application logs. Filter by level (INFO, WARN, ERROR), logger/service name, and limit. Use when diagnosing issues or checking system activity.")
    fun getLogs(level: String?, service: String?, limit: Int?): String {
        val logs = logService.getLogs(level, service, limit ?: 50)
        if (logs.isEmpty()) return "No log entries found."

        val lines = logs.map { entry ->
            val shortLogger = entry.logger.substringAfterLast(".")
            "${entry.timestamp} [${entry.level}] $shortLogger — ${entry.message}"
        }
        return "${logs.size} log entries:\n${lines.joinToString("\n")}"
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "${"%.1f".format(bytes / 1_073_741_824.0)} GB"
            bytes >= 1_048_576 -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
            bytes >= 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            else -> "$bytes bytes"
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*CrossCuttingToolsTest" 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/mcp/CrossCuttingTools.kt src/test/kotlin/org/sightech/memoryvault/mcp/CrossCuttingToolsTest.kt
git commit -m "feat: add CrossCuttingTools MCP with search, stats, jobs, and logs"
```

---

### Task 8: Integrate SyncJobService into SpringJobScheduler

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/scheduling/JobScheduler.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/scheduling/SpringJobScheduler.kt`

The goal is to wrap job execution so that every scheduled or manual job run records a SyncJob entry with start/completion/failure status.

**Step 1: Update JobScheduler interface**

The `schedule` method signature needs to support returning metadata from the task so SyncJobService can record it. Change the task type to return metadata:

Replace the content of `src/main/kotlin/org/sightech/memoryvault/scheduling/JobScheduler.kt` with:

```kotlin
package org.sightech.memoryvault.scheduling

interface JobScheduler {
    fun schedule(jobName: String, cron: String, jobType: String, task: () -> Map<String, Any>?)
    fun triggerNow(jobName: String)
}
```

**Step 2: Update SpringJobScheduler**

Replace the content of `src/main/kotlin/org/sightech/memoryvault/scheduling/SpringJobScheduler.kt` with:

```kotlin
package org.sightech.memoryvault.scheduling

import org.sightech.memoryvault.scheduling.service.SyncJobService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Component
class SpringJobScheduler(
    private val taskScheduler: TaskScheduler,
    private val syncJobService: SyncJobService
) : JobScheduler {

    companion object {
        val SYSTEM_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    private val logger = LoggerFactory.getLogger(SpringJobScheduler::class.java)
    private val jobs = ConcurrentHashMap<String, JobRegistration>()
    private val futures = ConcurrentHashMap<String, ScheduledFuture<*>>()

    private data class JobRegistration(val jobType: String, val task: () -> Map<String, Any>?)

    override fun schedule(jobName: String, cron: String, jobType: String, task: () -> Map<String, Any>?) {
        jobs[jobName] = JobRegistration(jobType, task)
        if (cron != "-") {
            val wrappedTask = Runnable { executeWithTracking(jobName, "SCHEDULED") }
            val future = taskScheduler.schedule(wrappedTask, CronTrigger(cron))
            if (future != null) {
                futures[jobName] = future
                logger.info("Scheduled job '{}' with cron '{}'", jobName, cron)
            }
        } else {
            logger.info("Job '{}' registered but not scheduled (cron disabled)", jobName)
        }
    }

    override fun triggerNow(jobName: String) {
        if (jobs.containsKey(jobName)) {
            logger.info("Triggering job '{}' immediately", jobName)
            executeWithTracking(jobName, "MANUAL")
        } else {
            logger.warn("Job '{}' not found", jobName)
        }
    }

    private fun executeWithTracking(jobName: String, triggeredBy: String) {
        val registration = jobs[jobName] ?: return
        val syncJob = syncJobService.recordStart(registration.jobType, triggeredBy, SYSTEM_USER_ID)

        try {
            val metadata = registration.task()
            syncJobService.recordSuccess(syncJob.id, metadata)
        } catch (e: Exception) {
            logger.error("Job '{}' failed: {}", jobName, e.message, e)
            syncJobService.recordFailure(syncJob.id, e.message ?: "Unknown error")
        }
    }
}
```

**Step 3: Update FeedSyncRegistrar to match new signature**

Modify `src/main/kotlin/org/sightech/memoryvault/feed/FeedSyncRegistrar.kt` — the `schedule` call needs `jobType` parameter and must return metadata:

Replace the content with:

```kotlin
package org.sightech.memoryvault.feed

import kotlinx.coroutines.runBlocking
import org.sightech.memoryvault.feed.service.FeedService
import org.sightech.memoryvault.scheduling.JobScheduler
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class FeedSyncRegistrar(
    private val jobScheduler: JobScheduler,
    private val feedService: FeedService,
    @Value("\${memoryvault.feeds.sync-cron:-}") private val syncCron: String
) {

    private val logger = LoggerFactory.getLogger(FeedSyncRegistrar::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun registerFeedSyncJob() {
        jobScheduler.schedule("feed-sync", syncCron, "RSS_FETCH") {
            logger.info("Feed sync job starting")
            val results = runBlocking { feedService.refreshFeed(null) }
            val totalNew = results.sumOf { it.second }
            logger.info("Feed sync complete: {} feeds refreshed, {} new items", results.size, totalNew)
            mapOf("feedsRefreshed" to results.size, "newItems" to totalNew)
        }
    }
}
```

**Step 4: Update YoutubeSyncRegistrar to match new signature**

Modify `src/main/kotlin/org/sightech/memoryvault/youtube/YoutubeSyncRegistrar.kt`:

Replace the content with:

```kotlin
package org.sightech.memoryvault.youtube

import org.sightech.memoryvault.scheduling.JobScheduler
import org.sightech.memoryvault.youtube.service.YoutubeListService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class YoutubeSyncRegistrar(
    private val jobScheduler: JobScheduler,
    private val youtubeListService: YoutubeListService,
    @Value("\${memoryvault.youtube.sync-cron:-}") private val syncCron: String
) {

    private val logger = LoggerFactory.getLogger(YoutubeSyncRegistrar::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun registerYoutubeSyncJob() {
        jobScheduler.schedule("youtube-sync", syncCron, "YT_SYNC") {
            logger.info("YouTube sync job starting")
            val results = youtubeListService.refreshList(null)
            val totalNew = results.sumOf { it.newVideos }
            val totalRemoved = results.sumOf { it.removedVideos }
            logger.info("YouTube sync complete: {} lists synced, {} new videos, {} removals detected",
                results.size, totalNew, totalRemoved)
            mapOf(
                "listsSynced" to results.size,
                "newVideos" to totalNew,
                "removedVideos" to totalRemoved,
                "downloadSuccesses" to results.sumOf { it.downloadSuccesses },
                "downloadFailures" to results.sumOf { it.downloadFailures }
            )
        }
    }
}
```

**Step 5: Verify compilation**

Run: `./gradlew compileKotlin 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

**Step 6: Run all tests to make sure nothing broke**

Run: `./gradlew test 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/scheduling/JobScheduler.kt src/main/kotlin/org/sightech/memoryvault/scheduling/SpringJobScheduler.kt src/main/kotlin/org/sightech/memoryvault/feed/FeedSyncRegistrar.kt src/main/kotlin/org/sightech/memoryvault/youtube/YoutubeSyncRegistrar.kt
git commit -m "feat: integrate SyncJobService into SpringJobScheduler for job history tracking"
```

---

### Task 9: Integration Test

**Files:**
- Create: `src/test/kotlin/org/sightech/memoryvault/crosscutting/CrossCuttingIntegrationTest.kt`

**Step 1: Write the integration test**

Create `src/test/kotlin/org/sightech/memoryvault/crosscutting/CrossCuttingIntegrationTest.kt`:

```kotlin
package org.sightech.memoryvault.crosscutting

import org.junit.jupiter.api.Test
import org.sightech.memoryvault.bookmark.service.BookmarkService
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.sightech.memoryvault.scheduling.service.SyncJobService
import org.sightech.memoryvault.search.ContentType
import org.sightech.memoryvault.search.SearchService
import org.sightech.memoryvault.stats.StatsService
import org.sightech.memoryvault.youtube.entity.YoutubeList
import org.sightech.memoryvault.youtube.entity.Video
import org.sightech.memoryvault.youtube.repository.VideoRepository
import org.sightech.memoryvault.youtube.repository.YoutubeListRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class CrossCuttingIntegrationTest {

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

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Autowired lateinit var searchService: SearchService
    @Autowired lateinit var statsService: StatsService
    @Autowired lateinit var syncJobService: SyncJobService
    @Autowired lateinit var bookmarkService: BookmarkService
    @Autowired lateinit var feedRepository: FeedRepository
    @Autowired lateinit var feedItemRepository: FeedItemRepository
    @Autowired lateinit var youtubeListRepository: YoutubeListRepository
    @Autowired lateinit var videoRepository: VideoRepository

    @Test
    fun `full-text search finds bookmarks`() {
        bookmarkService.create("https://kotlinlang.org", "Kotlin Programming Language", null)

        val results = searchService.search("kotlin", null, userId, 20)
        assertTrue(results.any { it.type == ContentType.BOOKMARK && it.title?.contains("Kotlin") == true })
    }

    @Test
    fun `full-text search finds feed items`() {
        val feed = feedRepository.save(Feed(userId = userId, url = "https://blog.example.com/rss"))
        feedItemRepository.save(FeedItem(feed = feed, guid = "search-test-1", title = "Kubernetes Deep Dive", url = "https://blog.example.com/k8s"))

        val results = searchService.search("kubernetes", null, userId, 20)
        assertTrue(results.any { it.type == ContentType.FEED_ITEM && it.title?.contains("Kubernetes") == true })
    }

    @Test
    fun `full-text search finds videos`() {
        val list = youtubeListRepository.save(YoutubeList(userId = userId, youtubeListId = "PLsearch1", url = "https://youtube.com/playlist?list=PLsearch1"))
        videoRepository.save(Video(youtubeList = list, youtubeVideoId = "search1", youtubeUrl = "https://youtube.com/watch?v=search1", title = "PostgreSQL Full Text Search Tutorial", channelName = "DB Channel"))

        val results = searchService.search("postgresql", null, userId, 20)
        assertTrue(results.any { it.type == ContentType.VIDEO && it.title?.contains("PostgreSQL") == true })
    }

    @Test
    fun `full-text search filters by type`() {
        bookmarkService.create("https://unique-search-test.com", "Unique Searchable Bookmark", null)

        val bookmarkOnly = searchService.search("unique searchable", listOf(ContentType.BOOKMARK), userId, 20)
        assertTrue(bookmarkOnly.all { it.type == ContentType.BOOKMARK })

        val videoOnly = searchService.search("unique searchable", listOf(ContentType.VIDEO), userId, 20)
        assertTrue(videoOnly.isEmpty())
    }

    @Test
    fun `getStats returns correct counts`() {
        val stats = statsService.getStats(userId)
        assertTrue(stats.bookmarkCount >= 0)
        assertTrue(stats.feedCount >= 0)
        assertTrue(stats.tagCount >= 0)
    }

    @Test
    fun `syncJobService records and retrieves job history`() {
        val job = syncJobService.recordStart("RSS_FETCH", "MANUAL", userId)
        assertNotNull(job.id)
        assertEquals("RUNNING", job.status)

        syncJobService.recordSuccess(job.id, mapOf("newItems" to 5))

        val jobs = syncJobService.listJobs(userId, "RSS_FETCH", 10)
        assertTrue(jobs.any { it.id == job.id && it.status == "SUCCESS" })
    }

    @Test
    fun `syncJobService records failure`() {
        val job = syncJobService.recordStart("YT_SYNC", "SCHEDULED", userId)
        syncJobService.recordFailure(job.id, "Connection refused")

        val jobs = syncJobService.listJobs(userId, "YT_SYNC", 10)
        val found = jobs.find { it.id == job.id }
        assertNotNull(found)
        assertEquals("FAILED", found.status)
        assertEquals("Connection refused", found.errorMessage)
    }
}
```

**Step 2: Run the integration test**

Run: `./gradlew test --tests "*CrossCuttingIntegrationTest" 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add src/test/kotlin/org/sightech/memoryvault/crosscutting/CrossCuttingIntegrationTest.kt
git commit -m "feat: add CrossCuttingIntegrationTest for FTS, stats, and job history"
```

---

### Task 10: Test Script

**Files:**
- Create: `scripts/test-crosscutting.sh`

**Step 1: Create the script**

Create `scripts/test-crosscutting.sh`:

```bash
#!/bin/bash
set -e

echo "=== Cross-Cutting Tests ==="

echo ""
echo "--- Unit: SearchService ---"
./gradlew test --tests "*SearchServiceTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Unit: StatsService ---"
./gradlew test --tests "*StatsServiceTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Unit: SyncJobService ---"
./gradlew test --tests "*SyncJobServiceTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Unit: LocalLogService ---"
./gradlew test --tests "*LocalLogServiceTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Unit: CrossCuttingTools (MCP) ---"
./gradlew test --tests "*CrossCuttingToolsTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Integration: CrossCuttingIntegrationTest ---"
./gradlew test --tests "*CrossCuttingIntegrationTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "=== All cross-cutting tests complete ==="
```

**Step 2: Make executable and run**

```bash
chmod +x scripts/test-crosscutting.sh
bash scripts/test-crosscutting.sh
```

Expected: All tests pass

**Step 3: Commit**

```bash
git add scripts/test-crosscutting.sh
git commit -m "feat: add test-crosscutting.sh script for Phase 4 test suite"
```

---

### Task 11: Update Design Doc

**Files:**
- Modify: `docs/plans/2026-03-05-tooling-first-design.md`

**Step 1: Update the Cross-cutting section**

In `docs/plans/2026-03-05-tooling-first-design.md`, update the Cross-cutting tools section (around line 46) to reflect what was actually built:

Replace:
```markdown
### Cross-cutting
- `search(query, types?)` — full-text search across all content types
- `get_stats()` — storage used, item counts, last sync times
- `get_aws_cost(billingCycle?)` — compute, storage, transfer costs per billing cycle
- `list_jobs()` — view scheduled sync job status and history
- `get_logs(level?, service?, limit?)` — pull logs from CloudWatch without needing the AWS console
```

With:
```markdown
### Cross-cutting
- `search(query, types?)` — PostgreSQL full-text search across bookmarks, feed items, and videos with ranked results
- `getStats()` — content counts, storage used, last sync times, failure counts
- `listJobs(type?, limit?)` — view sync job execution history with status and metadata
- `getLogs(level?, service?, limit?)` — retrieve structured JSON logs from local file or CloudWatch (Phase 6)
- `get_aws_cost(billingCycle?)` — compute, storage, transfer costs per billing cycle (Phase 6)
```

Also update Phase 4 description (around line 222-223):

Replace:
```markdown
### Phase 4 — Cross-cutting
Full-text search, `get_stats`, `get_aws_cost`, `get_logs`, tag-based filtering across all content types.
```

With:
```markdown
### Phase 4 — Cross-cutting
PostgreSQL full-text search, system stats, job history tracking with SyncJob entity, structured logging with local file reader and CloudWatch stub. AWS cost tracking deferred to Phase 6.
```

**Step 2: Commit**

```bash
git add docs/plans/2026-03-05-tooling-first-design.md
git commit -m "docs: update design doc to reflect Phase 4 implementation"
```
