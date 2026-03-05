# Phase 2: RSS / Feeds Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add RSS feed subscription, item browsing, and read/unread tracking via MCP tools, with a scheduler abstraction that runs locally and can be swapped for AWS Lambda later.

**Architecture:** Feed and FeedItem JPA entities map to existing V2 tables. RssFetchService uses prof18/RSS-Parser to fetch and parse feeds. A `JobScheduler` interface abstracts scheduling — the local `SpringJobScheduler` implementation uses Spring's `TaskScheduler` with configurable cron. FeedTools exposes 7 MCP `@Tool` methods. All services use the hardcoded system user UUID `00000000-0000-0000-0000-000000000001`.

**Tech Stack:** Kotlin 2.x, Spring Boot 4.x, Spring Data JPA, Spring AI `@Tool`, prof18/RSS-Parser 6.1.0, kotlinx-coroutines (for RSS-Parser suspend calls), TestContainers, JUnit 5, MockK

**Important conventions (from Phase 1):**
- Entity classes use `@Entity`, `@Table`, mutable `var` for updatable fields, `val` for immutable
- `@Version` for optimistic locking on mutable entities
- Repositories extend `JpaRepository<T, UUID>` with custom JPQL queries
- Services are `@Service`, MCP tool classes are `@Component`
- `@Tool` methods accept `String` for UUIDs (parsed internally), return formatted `String`
- SYSTEM_USER_ID = `UUID.fromString("00000000-0000-0000-0000-000000000001")`
- Git commits use `git commit -m "message"` — never use `$()`, heredoc, or command substitution

---

## Task 1: Add RSS-Parser and Coroutines Dependencies

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/application.properties`

**Step 1: Add dependencies to build.gradle.kts**

In the `dependencies` block, add after the `// AI / MCP` section:

```kotlin
    // RSS
    implementation("com.prof18.rssparser:rssparser:6.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
```

**Step 2: Add feed sync cron property to application.properties**

Append to the end of `src/main/resources/application.properties`:

```properties

# Feed sync schedule (cron syntax). Set to "-" to disable automatic sync.
# Examples: "0 */30 * * * *" = every 30 min, "0 0 * * * *" = hourly
memoryvault.feeds.sync-cron=-
```

**Step 3: Verify build resolves dependencies**

```bash
./gradlew dependencies --configuration runtimeClasspath 2>&1 | grep rssparser
```

Expected: line showing `com.prof18.rssparser:rssparser:6.1.0`

**Step 4: Commit**

```bash
git add build.gradle.kts src/main/resources/application.properties
git commit -m "feat: add RSS-Parser and coroutines dependencies, feed sync cron config"
```

---

## Task 2: Feed and FeedItem Entities

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/feed/entity/Feed.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/feed/entity/FeedItem.kt`

**Step 1: Create the Feed entity**

```kotlin
package org.sightech.memoryvault.feed.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "feeds")
class Feed(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false, length = 2048)
    val url: String,

    @Column(length = 500)
    var title: String? = null,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "site_url", length = 2048)
    var siteUrl: String? = null,

    @Column(name = "last_fetched_at")
    var lastFetchedAt: Instant? = null,

    @Column(name = "fetch_interval_minutes", nullable = false)
    var fetchIntervalMinutes: Int = 60,

    @Column(name = "failure_count", nullable = false)
    var failureCount: Int = 0,

    val createdAt: Instant = Instant.now(),

    var updatedAt: Instant = Instant.now(),

    var deletedAt: Instant? = null,

    @Version
    val version: Long = 0
)
```

**Step 2: Create the FeedItem entity**

```kotlin
package org.sightech.memoryvault.feed.entity

import jakarta.persistence.*
import org.sightech.memoryvault.tag.entity.Tag
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "feed_items")
class FeedItem(
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_id", nullable = false)
    val feed: Feed,

    @Column(nullable = false, length = 2048)
    val guid: String,

    @Column(length = 500)
    val title: String? = null,

    @Column(length = 2048)
    val url: String? = null,

    @Column(columnDefinition = "TEXT")
    val content: String? = null,

    @Column(length = 255)
    val author: String? = null,

    @Column(name = "image_url", length = 2048)
    val imageUrl: String? = null,

    @Column(name = "published_at")
    val publishedAt: Instant? = null,

    @Column(name = "read_at")
    var readAt: Instant? = null,

    val createdAt: Instant = Instant.now(),

    @ManyToMany
    @JoinTable(
        name = "feed_item_tags",
        joinColumns = [JoinColumn(name = "feed_item_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")]
    )
    val tags: MutableSet<Tag> = mutableSetOf()
)
```

**Step 3: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/feed/entity/
git commit -m "feat: add Feed and FeedItem JPA entities"
```

---

## Task 3: Feed and FeedItem Repositories

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/feed/repository/FeedRepository.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/feed/repository/FeedItemRepository.kt`

**Step 1: Create FeedRepository**

```kotlin
package org.sightech.memoryvault.feed.repository

import org.sightech.memoryvault.feed.entity.Feed
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface FeedRepository : JpaRepository<Feed, UUID> {

    @Query("SELECT f FROM Feed f WHERE f.deletedAt IS NULL AND f.userId = :userId ORDER BY f.title")
    fun findAllActiveByUserId(userId: UUID): List<Feed>

    @Query("SELECT f FROM Feed f WHERE f.id = :id AND f.deletedAt IS NULL")
    fun findActiveById(id: UUID): Feed?
}
```

**Step 2: Create FeedItemRepository**

```kotlin
package org.sightech.memoryvault.feed.repository

import org.sightech.memoryvault.feed.entity.FeedItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface FeedItemRepository : JpaRepository<FeedItem, UUID> {

    @Query("SELECT fi FROM FeedItem fi LEFT JOIN FETCH fi.tags WHERE fi.feed.id = :feedId ORDER BY fi.publishedAt DESC NULLS LAST")
    fun findByFeedId(feedId: UUID): List<FeedItem>

    @Query("SELECT fi FROM FeedItem fi LEFT JOIN FETCH fi.tags WHERE fi.feed.id = :feedId AND fi.readAt IS NULL ORDER BY fi.publishedAt DESC NULLS LAST")
    fun findUnreadByFeedId(feedId: UUID): List<FeedItem>

    fun existsByFeedIdAndGuid(feedId: UUID, guid: String): Boolean

    @Query("SELECT COUNT(fi) FROM FeedItem fi WHERE fi.feed.id = :feedId AND fi.readAt IS NULL")
    fun countUnreadByFeedId(feedId: UUID): Long

    @Modifying
    @Query("UPDATE FeedItem fi SET fi.readAt = :readAt WHERE fi.feed.id = :feedId AND fi.readAt IS NULL")
    fun markAllReadByFeedId(feedId: UUID, readAt: Instant): Int
}
```

**Step 3: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/feed/repository/
git commit -m "feat: add FeedRepository and FeedItemRepository"
```

---

## Task 4: RssFetchService with Tests

This is the core RSS fetching and parsing logic. It uses prof18/RSS-Parser to fetch a feed URL, parse items, deduplicate by GUID, and store new items. Tests use a static XML fixture string.

**Files:**
- Create: `src/test/kotlin/org/sightech/memoryvault/feed/service/RssFetchServiceTest.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/feed/service/RssFetchService.kt`
- Create: `src/test/resources/fixtures/sample-rss.xml`

**Step 1: Create the XML test fixture**

Create `src/test/resources/fixtures/sample-rss.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>Test Feed</title>
    <link>https://example.com</link>
    <description>A test RSS feed</description>
    <item>
      <title>First Post</title>
      <link>https://example.com/post-1</link>
      <guid>post-1</guid>
      <description>First post content</description>
      <author>alice@example.com</author>
      <pubDate>Mon, 01 Jan 2024 00:00:00 GMT</pubDate>
    </item>
    <item>
      <title>Second Post</title>
      <link>https://example.com/post-2</link>
      <guid>post-2</guid>
      <description>Second post content</description>
      <author>bob@example.com</author>
      <pubDate>Tue, 02 Jan 2024 00:00:00 GMT</pubDate>
    </item>
    <item>
      <title>No GUID Post</title>
      <link>https://example.com/post-3</link>
      <description>Post without a guid element</description>
      <pubDate>Wed, 03 Jan 2024 00:00:00 GMT</pubDate>
    </item>
  </channel>
</rss>
```

**Step 2: Write the failing RssFetchService test**

```kotlin
package org.sightech.memoryvault.feed.service

import com.prof18.rssparser.RssParser
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import java.util.UUID
import kotlin.test.assertEquals

class RssFetchServiceTest {

    private val feedRepository = mockk<FeedRepository>()
    private val feedItemRepository = mockk<FeedItemRepository>()
    private val rssParser = RssParser()
    private val service = RssFetchService(feedRepository, feedItemRepository, rssParser)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val sampleXml = this::class.java.classLoader.getResource("fixtures/sample-rss.xml")!!.readText()

    @Test
    fun `fetchAndStore parses items and saves new ones`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss")
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, any()) } returns false
        every { feedItemRepository.save(any()) } answers { firstArg() }
        every { feedRepository.save(any()) } answers { firstArg() }

        val result = runBlocking { service.fetchAndStoreFromXml(feed, sampleXml) }

        assertEquals(3, result)
        verify(exactly = 3) { feedItemRepository.save(any()) }
        verify { feedRepository.save(match { it.lastFetchedAt != null }) }
    }

    @Test
    fun `fetchAndStore skips existing items by guid`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss")
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, "post-1") } returns true
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, "post-2") } returns true
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, "https://example.com/post-3") } returns false
        every { feedItemRepository.save(any()) } answers { firstArg() }
        every { feedRepository.save(any()) } answers { firstArg() }

        val result = runBlocking { service.fetchAndStoreFromXml(feed, sampleXml) }

        assertEquals(1, result)
        verify(exactly = 1) { feedItemRepository.save(any()) }
    }

    @Test
    fun `fetchAndStore uses link as guid fallback when guid is null`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss")
        // post-3 has no <guid>, should fall back to <link>
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, "post-1") } returns true
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, "post-2") } returns true
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, "https://example.com/post-3") } returns true
        every { feedRepository.save(any()) } answers { firstArg() }

        val result = runBlocking { service.fetchAndStoreFromXml(feed, sampleXml) }

        assertEquals(0, result)
    }

    @Test
    fun `fetchAndStore updates feed metadata from channel`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss")
        every { feedItemRepository.existsByFeedIdAndGuid(feed.id, any()) } returns false
        every { feedItemRepository.save(any()) } answers { firstArg() }
        every { feedRepository.save(any()) } answers { firstArg() }

        runBlocking { service.fetchAndStoreFromXml(feed, sampleXml) }

        verify { feedRepository.save(match { it.title == "Test Feed" && it.description == "A test RSS feed" && it.siteUrl == "https://example.com" }) }
    }
}
```

**Step 3: Run test to verify it fails**

```bash
./gradlew test --tests "*RssFetchServiceTest"
```

Expected: FAIL — `RssFetchService` does not exist.

**Step 4: Create RssFetchService**

```kotlin
package org.sightech.memoryvault.feed.service

import com.prof18.rssparser.RssParser
import com.prof18.rssparser.model.RssItem
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Service
class RssFetchService(
    private val feedRepository: FeedRepository,
    private val feedItemRepository: FeedItemRepository,
    private val rssParser: RssParser
) {

    suspend fun fetchAndStore(feed: Feed): Int {
        val channel = rssParser.getRssChannel(feed.url)
        val xml = null // not needed — we got the channel directly
        return processChannel(feed, channel)
    }

    suspend fun fetchAndStoreFromXml(feed: Feed, xml: String): Int {
        val channel = rssParser.parse(xml)
        return processChannel(feed, channel)
    }

    private fun processChannel(feed: Feed, channel: com.prof18.rssparser.model.RssChannel): Int {
        // Update feed metadata
        feed.title = channel.title
        feed.description = channel.description
        feed.siteUrl = channel.link
        feed.lastFetchedAt = Instant.now()
        feed.updatedAt = Instant.now()
        feedRepository.save(feed)

        // Insert new items
        var newCount = 0
        for (item in channel.items) {
            val guid = resolveGuid(item)
            if (feedItemRepository.existsByFeedIdAndGuid(feed.id, guid)) continue

            val feedItem = FeedItem(
                feed = feed,
                guid = guid,
                title = item.title,
                url = item.link,
                content = item.description,
                author = item.author,
                imageUrl = item.image,
                publishedAt = parseDate(item.pubDate)
            )
            feedItemRepository.save(feedItem)
            newCount++
        }

        return newCount
    }

    private fun resolveGuid(item: RssItem): String {
        // Prefer guid, fall back to link, then hash of title+pubDate
        return item.guid
            ?: item.link
            ?: hashFallback(item.title, item.pubDate)
    }

    private fun hashFallback(title: String?, pubDate: String?): String {
        val input = "${title.orEmpty()}|${pubDate.orEmpty()}"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun parseDate(dateStr: String?): Instant? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            ZonedDateTime.parse(dateStr, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        } catch (e: Exception) {
            try {
                Instant.parse(dateStr)
            } catch (e2: Exception) {
                null
            }
        }
    }
}
```

**Step 5: Run tests to verify they pass**

```bash
./gradlew test --tests "*RssFetchServiceTest"
```

Expected: PASS (4 tests).

**Step 6: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/feed/service/RssFetchService.kt src/test/kotlin/org/sightech/memoryvault/feed/service/RssFetchServiceTest.kt src/test/resources/fixtures/sample-rss.xml
git commit -m "feat: add RssFetchService with RSS parsing and dedup logic"
```

---

## Task 5: FeedItemService with Tests

**Files:**
- Create: `src/test/kotlin/org/sightech/memoryvault/feed/service/FeedItemServiceTest.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/feed/service/FeedItemService.kt`

**Step 1: Write the failing test**

```kotlin
package org.sightech.memoryvault.feed.service

import io.mockk.*
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FeedItemServiceTest {

    private val feedItemRepository = mockk<FeedItemRepository>()
    private val service = FeedItemService(feedItemRepository)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val feed = Feed(userId = userId, url = "https://example.com/rss")

    @Test
    fun `getItems returns all items for feed`() {
        val items = listOf(FeedItem(feed = feed, guid = "1", title = "A"))
        every { feedItemRepository.findByFeedId(feed.id) } returns items

        val result = service.getItems(feed.id, null, false)

        assertEquals(1, result.size)
    }

    @Test
    fun `getItems returns only unread when unreadOnly is true`() {
        val items = listOf(FeedItem(feed = feed, guid = "1", title = "A"))
        every { feedItemRepository.findUnreadByFeedId(feed.id) } returns items

        val result = service.getItems(feed.id, null, true)

        assertEquals(1, result.size)
        verify { feedItemRepository.findUnreadByFeedId(feed.id) }
    }

    @Test
    fun `getItems respects limit`() {
        val items = (1..10).map { FeedItem(feed = feed, guid = "$it", title = "Item $it") }
        every { feedItemRepository.findByFeedId(feed.id) } returns items

        val result = service.getItems(feed.id, 3, false)

        assertEquals(3, result.size)
    }

    @Test
    fun `markItemRead sets readAt`() {
        val item = FeedItem(feed = feed, guid = "1", title = "A")
        every { feedItemRepository.findById(item.id) } returns Optional.of(item)
        every { feedItemRepository.save(any()) } answers { firstArg() }

        val result = service.markItemRead(item.id)

        assertNotNull(result)
        assertNotNull(result.readAt)
    }

    @Test
    fun `markItemRead returns null for nonexistent item`() {
        val id = UUID.randomUUID()
        every { feedItemRepository.findById(id) } returns Optional.empty()

        val result = service.markItemRead(id)

        assertNull(result)
    }

    @Test
    fun `markItemUnread clears readAt`() {
        val item = FeedItem(feed = feed, guid = "1", title = "A").apply { readAt = Instant.now() }
        every { feedItemRepository.findById(item.id) } returns Optional.of(item)
        every { feedItemRepository.save(any()) } answers { firstArg() }

        val result = service.markItemUnread(item.id)

        assertNotNull(result)
        assertNull(result.readAt)
    }

    @Test
    fun `markFeedRead returns count of marked items`() {
        every { feedItemRepository.markAllReadByFeedId(feed.id, any()) } returns 5

        val count = service.markFeedRead(feed.id)

        assertEquals(5, count)
    }
}
```

**Step 2: Run test to verify it fails**

```bash
./gradlew test --tests "*FeedItemServiceTest"
```

Expected: FAIL — `FeedItemService` does not exist.

**Step 3: Create FeedItemService**

```kotlin
package org.sightech.memoryvault.feed.service

import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class FeedItemService(private val feedItemRepository: FeedItemRepository) {

    fun getItems(feedId: UUID, limit: Int?, unreadOnly: Boolean): List<FeedItem> {
        val items = if (unreadOnly) {
            feedItemRepository.findUnreadByFeedId(feedId)
        } else {
            feedItemRepository.findByFeedId(feedId)
        }
        return if (limit != null && limit > 0) items.take(limit) else items
    }

    fun markItemRead(itemId: UUID): FeedItem? {
        val item = feedItemRepository.findById(itemId).orElse(null) ?: return null
        item.readAt = Instant.now()
        return feedItemRepository.save(item)
    }

    fun markItemUnread(itemId: UUID): FeedItem? {
        val item = feedItemRepository.findById(itemId).orElse(null) ?: return null
        item.readAt = null
        return feedItemRepository.save(item)
    }

    @Transactional
    fun markFeedRead(feedId: UUID): Int {
        return feedItemRepository.markAllReadByFeedId(feedId, Instant.now())
    }
}
```

**Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "*FeedItemServiceTest"
```

Expected: PASS (7 tests).

**Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/feed/service/FeedItemService.kt src/test/kotlin/org/sightech/memoryvault/feed/service/FeedItemServiceTest.kt
git commit -m "feat: add FeedItemService with read/unread tracking"
```

---

## Task 6: FeedService with Tests

**Files:**
- Create: `src/test/kotlin/org/sightech/memoryvault/feed/service/FeedServiceTest.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/feed/service/FeedService.kt`

**Step 1: Write the failing test**

```kotlin
package org.sightech.memoryvault.feed.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FeedServiceTest {

    private val feedRepository = mockk<FeedRepository>()
    private val feedItemRepository = mockk<FeedItemRepository>()
    private val rssFetchService = mockk<RssFetchService>()
    private val service = FeedService(feedRepository, feedItemRepository, rssFetchService)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `addFeed creates feed and triggers initial fetch`() {
        every { feedRepository.save(any()) } answers { firstArg() }
        coEvery { rssFetchService.fetchAndStore(any()) } returns 5

        val result = runBlocking { service.addFeed("https://example.com/rss") }

        assertNotNull(result)
        assertEquals("https://example.com/rss", result.url)
        verify { feedRepository.save(any()) }
        coVerify { rssFetchService.fetchAndStore(any()) }
    }

    @Test
    fun `listFeeds returns feeds with unread counts`() {
        val feed1 = Feed(userId = userId, url = "https://a.com/rss").apply { title = "Feed A" }
        val feed2 = Feed(userId = userId, url = "https://b.com/rss").apply { title = "Feed B" }
        every { feedRepository.findAllActiveByUserId(userId) } returns listOf(feed1, feed2)
        every { feedItemRepository.countUnreadByFeedId(feed1.id) } returns 3
        every { feedItemRepository.countUnreadByFeedId(feed2.id) } returns 0

        val result = service.listFeeds()

        assertEquals(2, result.size)
        assertEquals(3L, result[0].second)
        assertEquals(0L, result[1].second)
    }

    @Test
    fun `deleteFeed soft deletes`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss")
        every { feedRepository.findActiveById(feed.id) } returns feed
        every { feedRepository.save(any()) } answers { firstArg() }

        val result = service.deleteFeed(feed.id)

        assertNotNull(result)
        assertNotNull(result.deletedAt)
    }

    @Test
    fun `deleteFeed returns null for nonexistent feed`() {
        val id = UUID.randomUUID()
        every { feedRepository.findActiveById(id) } returns null

        val result = service.deleteFeed(id)

        assertNull(result)
    }

    @Test
    fun `refreshFeed refreshes single feed`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss")
        every { feedRepository.findActiveById(feed.id) } returns feed
        coEvery { rssFetchService.fetchAndStore(feed) } returns 3

        val result = runBlocking { service.refreshFeed(feed.id) }

        assertEquals(1, result.size)
        assertEquals(3, result[0].second)
    }

    @Test
    fun `refreshFeed with null refreshes all feeds`() {
        val feed1 = Feed(userId = userId, url = "https://a.com/rss")
        val feed2 = Feed(userId = userId, url = "https://b.com/rss")
        every { feedRepository.findAllActiveByUserId(userId) } returns listOf(feed1, feed2)
        coEvery { rssFetchService.fetchAndStore(any()) } returns 2

        val result = runBlocking { service.refreshFeed(null) }

        assertEquals(2, result.size)
    }
}
```

**Step 2: Run test to verify it fails**

```bash
./gradlew test --tests "*FeedServiceTest"
```

Expected: FAIL — `FeedService` does not exist.

**Step 3: Create FeedService**

```kotlin
package org.sightech.memoryvault.feed.service

import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.repository.FeedItemRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class FeedService(
    private val feedRepository: FeedRepository,
    private val feedItemRepository: FeedItemRepository,
    private val rssFetchService: RssFetchService
) {

    companion object {
        val SYSTEM_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    suspend fun addFeed(url: String): Feed {
        val feed = feedRepository.save(Feed(userId = SYSTEM_USER_ID, url = url))
        rssFetchService.fetchAndStore(feed)
        return feed
    }

    fun listFeeds(): List<Pair<Feed, Long>> {
        val feeds = feedRepository.findAllActiveByUserId(SYSTEM_USER_ID)
        return feeds.map { feed ->
            val unreadCount = feedItemRepository.countUnreadByFeedId(feed.id)
            feed to unreadCount
        }
    }

    fun deleteFeed(feedId: UUID): Feed? {
        val feed = feedRepository.findActiveById(feedId) ?: return null
        feed.deletedAt = Instant.now()
        feed.updatedAt = Instant.now()
        return feedRepository.save(feed)
    }

    suspend fun refreshFeed(feedId: UUID?): List<Pair<Feed, Int>> {
        val feeds = if (feedId != null) {
            val feed = feedRepository.findActiveById(feedId) ?: return emptyList()
            listOf(feed)
        } else {
            feedRepository.findAllActiveByUserId(SYSTEM_USER_ID)
        }

        return feeds.map { feed ->
            val newCount = rssFetchService.fetchAndStore(feed)
            feed to newCount
        }
    }
}
```

**Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "*FeedServiceTest"
```

Expected: PASS (6 tests).

**Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/feed/service/FeedService.kt src/test/kotlin/org/sightech/memoryvault/feed/service/FeedServiceTest.kt
git commit -m "feat: add FeedService with addFeed, listFeeds, deleteFeed, refreshFeed"
```

---

## Task 7: JobScheduler Interface and SpringJobScheduler

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/scheduling/JobScheduler.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/scheduling/SpringJobScheduler.kt`

**Step 1: Create the JobScheduler interface**

```kotlin
package org.sightech.memoryvault.scheduling

interface JobScheduler {
    fun schedule(jobName: String, cron: String, task: () -> Unit)
    fun triggerNow(jobName: String)
}
```

**Step 2: Create the SpringJobScheduler implementation**

```kotlin
package org.sightech.memoryvault.scheduling

import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture

@Component
class SpringJobScheduler(private val taskScheduler: TaskScheduler) : JobScheduler {

    private val logger = LoggerFactory.getLogger(SpringJobScheduler::class.java)
    private val jobs = ConcurrentHashMap<String, () -> Unit>()
    private val futures = ConcurrentHashMap<String, ScheduledFuture<*>>()

    override fun schedule(jobName: String, cron: String, task: () -> Unit) {
        jobs[jobName] = task
        if (cron != "-") {
            val future = taskScheduler.schedule(task, CronTrigger(cron))
            if (future != null) {
                futures[jobName] = future
                logger.info("Scheduled job '{}' with cron '{}'", jobName, cron)
            }
        } else {
            logger.info("Job '{}' registered but not scheduled (cron disabled)", jobName)
        }
    }

    override fun triggerNow(jobName: String) {
        val task = jobs[jobName]
        if (task != null) {
            logger.info("Triggering job '{}' immediately", jobName)
            task()
        } else {
            logger.warn("Job '{}' not found", jobName)
        }
    }
}
```

**Step 3: Add a TaskScheduler bean configuration**

Create `src/main/kotlin/org/sightech/memoryvault/config/SchedulingConfig.kt`:

```kotlin
package org.sightech.memoryvault.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class SchedulingConfig {

    @Bean
    fun taskScheduler(): TaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 2
        scheduler.setThreadNamePrefix("memoryvault-scheduler-")
        scheduler.initialize()
        return scheduler
    }
}
```

**Step 4: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

**Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/scheduling/ src/main/kotlin/org/sightech/memoryvault/config/SchedulingConfig.kt
git commit -m "feat: add JobScheduler interface and SpringJobScheduler implementation"
```

---

## Task 8: Feed Sync Scheduler Registration

Wire the scheduler to call `FeedService.refreshFeed(null)` on the configured cron.

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/feed/FeedSyncRegistrar.kt`

**Step 1: Create the registrar**

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
        jobScheduler.schedule("feed-sync", syncCron) {
            logger.info("Feed sync job starting")
            val results = runBlocking { feedService.refreshFeed(null) }
            val totalNew = results.sumOf { it.second }
            logger.info("Feed sync complete: {} feeds refreshed, {} new items", results.size, totalNew)
        }
    }
}
```

**Step 2: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/feed/FeedSyncRegistrar.kt
git commit -m "feat: register feed sync job on application startup"
```

---

## Task 9: FeedTools MCP Class with Tests

**Files:**
- Create: `src/test/kotlin/org/sightech/memoryvault/mcp/FeedToolsTest.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/mcp/FeedTools.kt`

**Step 1: Write the failing test**

```kotlin
package org.sightech.memoryvault.mcp

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.service.FeedItemService
import org.sightech.memoryvault.feed.service.FeedService
import java.util.UUID
import kotlin.test.assertContains

class FeedToolsTest {

    private val feedService = mockk<FeedService>()
    private val feedItemService = mockk<FeedItemService>()
    private val tools = FeedTools(feedService, feedItemService)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `addFeed returns confirmation`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss").apply { title = "Example Feed" }
        coEvery { feedService.addFeed("https://example.com/rss") } returns feed

        val result = runBlocking { tools.addFeed("https://example.com/rss") }

        assertContains(result, "Example Feed")
        assertContains(result, "https://example.com/rss")
    }

    @Test
    fun `listFeeds returns formatted list with unread counts`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss").apply { title = "Example" }
        every { feedService.listFeeds() } returns listOf(feed to 5L)

        val result = tools.listFeeds()

        assertContains(result, "Example")
        assertContains(result, "5 unread")
    }

    @Test
    fun `listFeeds returns message when empty`() {
        every { feedService.listFeeds() } returns emptyList()

        val result = tools.listFeeds()

        assertContains(result, "No feeds")
    }

    @Test
    fun `getFeedItems returns formatted items`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss")
        val item = FeedItem(feed = feed, guid = "1", title = "Post Title", url = "https://example.com/post")
        every { feedItemService.getItems(any(), any(), any()) } returns listOf(item)

        val result = tools.getFeedItems(feed.id.toString(), null, null)

        assertContains(result, "Post Title")
    }

    @Test
    fun `markItemRead returns confirmation`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss")
        val item = FeedItem(feed = feed, guid = "1", title = "Post Title")
        every { feedItemService.markItemRead(item.id) } returns item

        val result = tools.markItemRead(item.id.toString())

        assertContains(result, "Post Title")
        assertContains(result, "read")
    }

    @Test
    fun `markItemRead returns not found`() {
        val id = UUID.randomUUID()
        every { feedItemService.markItemRead(id) } returns null

        val result = tools.markItemRead(id.toString())

        assertContains(result, "not found")
    }

    @Test
    fun `markItemUnread returns confirmation`() {
        val feed = Feed(userId = userId, url = "https://example.com/rss")
        val item = FeedItem(feed = feed, guid = "1", title = "Post Title")
        every { feedItemService.markItemUnread(item.id) } returns item

        val result = tools.markItemUnread(item.id.toString())

        assertContains(result, "Post Title")
        assertContains(result, "unread")
    }

    @Test
    fun `markFeedRead returns count`() {
        every { feedItemService.markFeedRead(any()) } returns 10

        val result = tools.markFeedRead(UUID.randomUUID().toString())

        assertContains(result, "10")
        assertContains(result, "read")
    }

    @Test
    fun `refreshFeed returns summary`() {
        val feed = Feed(userId = userId, url = "https://a.com/rss").apply { title = "Feed A" }
        coEvery { feedService.refreshFeed(feed.id) } returns listOf(feed to 3)

        val result = runBlocking { tools.refreshFeed(feed.id.toString()) }

        assertContains(result, "Feed A")
        assertContains(result, "3")
    }

    @Test
    fun `refreshFeed with null refreshes all`() {
        val feed = Feed(userId = userId, url = "https://a.com/rss").apply { title = "A" }
        coEvery { feedService.refreshFeed(null) } returns listOf(feed to 2)

        val result = runBlocking { tools.refreshFeed(null) }

        assertContains(result, "A")
    }
}
```

**Step 2: Run test to verify it fails**

```bash
./gradlew test --tests "*FeedToolsTest"
```

Expected: FAIL — `FeedTools` does not exist.

**Step 3: Create FeedTools**

```kotlin
package org.sightech.memoryvault.mcp

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import org.sightech.memoryvault.feed.service.FeedItemService
import org.sightech.memoryvault.feed.service.FeedService
import kotlinx.coroutines.runBlocking
import java.util.UUID

@Component
class FeedTools(
    private val feedService: FeedService,
    private val feedItemService: FeedItemService
) {

    @Tool(description = "Subscribe to an RSS feed. Use when the user wants to add, follow, or subscribe to an RSS or Atom feed by URL.")
    fun addFeed(url: String): String {
        val feed = runBlocking { feedService.addFeed(url) }
        return "Subscribed to feed: \"${feed.title ?: feed.url}\" (${feed.url}) — id: ${feed.id}"
    }

    @Tool(description = "List all subscribed RSS feeds with unread item counts. Use when the user wants to see their feeds or check for unread items.")
    fun listFeeds(): String {
        val feeds = feedService.listFeeds()
        if (feeds.isEmpty()) return "No feeds subscribed."

        val lines = feeds.map { (feed, unread) ->
            "- ${feed.title ?: feed.url} — $unread unread — id: ${feed.id}"
        }
        return "${feeds.size} feed(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Browse items from an RSS feed. Use when the user wants to read or see articles from a specific feed. Set unreadOnly to true to see only unread items.")
    fun getFeedItems(feedId: String, limit: Int?, unreadOnly: Boolean?): String {
        val items = feedItemService.getItems(UUID.fromString(feedId), limit, unreadOnly ?: false)
        if (items.isEmpty()) return "No items found."

        val lines = items.map { item ->
            val status = if (item.readAt != null) "[read]" else "[unread]"
            val tagStr = if (item.tags.isNotEmpty()) " [${item.tags.joinToString(", ") { it.name }}]" else ""
            "- $status ${item.title ?: "(no title)"} — ${item.url ?: "(no url)"}$tagStr — id: ${item.id}"
        }
        return "${items.size} item(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Mark a single feed item as read. Use when the user has read an article or wants to dismiss it.")
    fun markItemRead(itemId: String): String {
        val item = feedItemService.markItemRead(UUID.fromString(itemId))
            ?: return "Feed item not found."
        return "Marked as read: \"${item.title ?: "(no title)"}\""
    }

    @Tool(description = "Mark a single feed item as unread. Use when the user wants to mark an article as unread again.")
    fun markItemUnread(itemId: String): String {
        val item = feedItemService.markItemUnread(UUID.fromString(itemId))
            ?: return "Feed item not found."
        return "Marked as unread: \"${item.title ?: "(no title)"}\""
    }

    @Tool(description = "Mark all items in a feed as read. Use when the user wants to clear all unread items in a feed.")
    fun markFeedRead(feedId: String): String {
        val count = feedItemService.markFeedRead(UUID.fromString(feedId))
        return "Marked $count item(s) as read."
    }

    @Tool(description = "Refresh one or all RSS feeds to fetch new items. Use when the user wants to check for new articles. Pass a feedId to refresh one feed, or omit it to refresh all.")
    fun refreshFeed(feedId: String?): String {
        val results = runBlocking {
            feedService.refreshFeed(feedId?.let { UUID.fromString(it) })
        }
        if (results.isEmpty()) return "No feeds to refresh."

        val lines = results.map { (feed, newCount) ->
            "- ${feed.title ?: feed.url}: $newCount new item(s)"
        }
        return "Refreshed ${results.size} feed(s):\n${lines.joinToString("\n")}"
    }
}
```

**Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "*FeedToolsTest"
```

Expected: PASS (10 tests).

**Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/mcp/FeedTools.kt src/test/kotlin/org/sightech/memoryvault/mcp/FeedToolsTest.kt
git commit -m "feat: add FeedTools with 7 MCP tools"
```

---

## Task 10: Feed REST Controller Stub

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/feed/controller/FeedController.kt`

**Step 1: Create the controller**

```kotlin
package org.sightech.memoryvault.feed.controller

import org.sightech.memoryvault.feed.service.FeedService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/feeds")
class FeedController(private val service: FeedService) {

    @GetMapping
    fun listFeeds() = service.listFeeds().map { (feed, unread) ->
        mapOf("id" to feed.id, "url" to feed.url, "title" to feed.title, "unreadCount" to unread)
    }
}
```

**Step 2: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/feed/controller/
git commit -m "feat: add FeedController stub with list endpoint"
```

---

## Task 11: RssParser Bean Configuration

The RssParser needs to be a Spring bean so it can be injected into RssFetchService.

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/config/RssParserConfig.kt`

**Step 1: Create the config**

```kotlin
package org.sightech.memoryvault.config

import com.prof18.rssparser.RssParser
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RssParserConfig {

    @Bean
    fun rssParser(): RssParser = RssParser()
}
```

**Step 2: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/config/RssParserConfig.kt
git commit -m "feat: add RssParser Spring bean configuration"
```

---

## Task 12: Integration Test

**Files:**
- Create: `src/test/kotlin/org/sightech/memoryvault/feed/FeedIntegrationTest.kt`

**Step 1: Write the integration test**

```kotlin
package org.sightech.memoryvault.feed

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.service.FeedItemService
import org.sightech.memoryvault.feed.service.FeedService
import org.sightech.memoryvault.feed.service.RssFetchService
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class FeedIntegrationTest {

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

    @Autowired lateinit var feedService: FeedService
    @Autowired lateinit var feedItemService: FeedItemService
    @Autowired lateinit var rssFetchService: RssFetchService
    @Autowired lateinit var feedRepository: FeedRepository

    private val sampleXml = this::class.java.classLoader.getResource("fixtures/sample-rss.xml")!!.readText()
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `create feed and fetch items from XML`() {
        val feed = feedRepository.save(
            org.sightech.memoryvault.feed.entity.Feed(userId = userId, url = "https://test-feed.com/rss")
        )

        val newCount = runBlocking { rssFetchService.fetchAndStoreFromXml(feed, sampleXml) }

        assertEquals(3, newCount)

        val items = feedItemService.getItems(feed.id, null, false)
        assertEquals(3, items.size)
    }

    @Test
    fun `deduplication prevents duplicate items`() {
        val feed = feedRepository.save(
            org.sightech.memoryvault.feed.entity.Feed(userId = userId, url = "https://dedup-test.com/rss")
        )

        val first = runBlocking { rssFetchService.fetchAndStoreFromXml(feed, sampleXml) }
        assertEquals(3, first)

        val second = runBlocking { rssFetchService.fetchAndStoreFromXml(feed, sampleXml) }
        assertEquals(0, second)

        val items = feedItemService.getItems(feed.id, null, false)
        assertEquals(3, items.size)
    }

    @Test
    fun `mark item read and unread`() {
        val feed = feedRepository.save(
            org.sightech.memoryvault.feed.entity.Feed(userId = userId, url = "https://read-test.com/rss")
        )
        runBlocking { rssFetchService.fetchAndStoreFromXml(feed, sampleXml) }

        val items = feedItemService.getItems(feed.id, null, false)
        val item = items.first()

        // Mark read
        val read = feedItemService.markItemRead(item.id)
        assertNotNull(read?.readAt)

        // Verify unread count decreased
        val unreadItems = feedItemService.getItems(feed.id, null, true)
        assertEquals(items.size - 1, unreadItems.size)

        // Mark unread
        val unread = feedItemService.markItemUnread(item.id)
        assertNull(unread?.readAt)
    }

    @Test
    fun `mark all items in feed as read`() {
        val feed = feedRepository.save(
            org.sightech.memoryvault.feed.entity.Feed(userId = userId, url = "https://markall-test.com/rss")
        )
        runBlocking { rssFetchService.fetchAndStoreFromXml(feed, sampleXml) }

        val count = feedItemService.markFeedRead(feed.id)
        assertEquals(3, count)

        val unread = feedItemService.getItems(feed.id, null, true)
        assertTrue(unread.isEmpty())
    }

    @Test
    fun `list feeds returns unread counts`() {
        val feed = feedRepository.save(
            org.sightech.memoryvault.feed.entity.Feed(userId = userId, url = "https://list-test.com/rss")
        )
        runBlocking { rssFetchService.fetchAndStoreFromXml(feed, sampleXml) }

        val feeds = feedService.listFeeds()
        val match = feeds.find { it.first.id == feed.id }
        assertNotNull(match)
        assertEquals(3L, match.second)
    }

    @Test
    fun `soft delete feed`() {
        val feed = feedRepository.save(
            org.sightech.memoryvault.feed.entity.Feed(userId = userId, url = "https://delete-test.com/rss")
        )

        val deleted = feedService.deleteFeed(feed.id)
        assertNotNull(deleted?.deletedAt)

        val feeds = feedService.listFeeds()
        assertTrue(feeds.none { it.first.id == feed.id })
    }

    @Test
    fun `feed metadata updated after fetch`() {
        val feed = feedRepository.save(
            org.sightech.memoryvault.feed.entity.Feed(userId = userId, url = "https://meta-test.com/rss")
        )
        runBlocking { rssFetchService.fetchAndStoreFromXml(feed, sampleXml) }

        val updated = feedRepository.findActiveById(feed.id)
        assertNotNull(updated)
        assertEquals("Test Feed", updated.title)
        assertEquals("A test RSS feed", updated.description)
        assertEquals("https://example.com", updated.siteUrl)
        assertNotNull(updated.lastFetchedAt)
    }
}
```

**Step 2: Run the integration test**

```bash
./gradlew test --tests "*FeedIntegrationTest"
```

Expected: PASS (7 tests). If any fail, debug and fix.

**Step 3: Run the full test suite**

```bash
./gradlew test
```

Expected: All tests pass (bookmark tests + feed tests + context loads test).

**Step 4: Commit**

```bash
git add src/test/kotlin/org/sightech/memoryvault/feed/FeedIntegrationTest.kt
git commit -m "feat: add FeedIntegrationTest with full round-trip verification"
```

---

## Task 13: Test Script

**Files:**
- Create: `scripts/test-feeds.sh`

**Step 1: Create the script**

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "=== MemoryVault: Feed tests ==="
echo ""

echo "--- Unit tests ---"
./gradlew test --tests "*RssFetchServiceTest" --tests "*FeedItemServiceTest" --tests "*FeedServiceTest" --tests "*FeedToolsTest"

echo ""
echo "--- Integration tests ---"
./gradlew test --tests "*FeedIntegrationTest"

echo ""
echo "=== All feed tests passed ==="
```

**Step 2: Make executable**

```bash
chmod +x scripts/test-feeds.sh
```

**Step 3: Commit**

```bash
git add scripts/test-feeds.sh
git commit -m "feat: add test-feeds.sh script"
```

---

## Phase 2 Complete

At the end of Phase 2 you have:

- Feed and FeedItem JPA entities mapped to existing V2 tables
- RssFetchService with prof18/RSS-Parser, GUID fallback dedup, date parsing
- FeedService with addFeed, listFeeds, deleteFeed, refreshFeed
- FeedItemService with getItems, markItemRead, markItemUnread, markFeedRead
- JobScheduler interface + SpringJobScheduler (configurable cron, disabled by default)
- FeedSyncRegistrar wiring scheduler to feed refresh on app startup
- FeedTools with 7 MCP tools
- FeedController stub
- RssParser Spring bean config
- Integration test with 7 round-trip tests
- `scripts/test-feeds.sh`

**Next:** Phase 3 — YouTube Archival. Tables already exist. Use `/scaffold-entity` for YoutubeList + Video entities, then `/add-mcp-tool` for YouTube tools.
