# Phase 7 — Mirror OldReader Functionality: Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the MemoryVault feed reader feature-complete by adding feed categories, OPML import/export, feed management UI, reader view modes, and stubs for starred articles/API keys/OAuth.

**Architecture:** Single-level FeedCategory entity with "Subscribed" default per user. Feed entity gains a non-nullable categoryId FK. User entity gains viewMode/sortOrder preference columns. OPML handled by a dedicated OpmlService. Angular reader refactored from monolithic component into sub-components (category sidebar, toolbar, list/full views). Stubs are commented-out code with TODO markers.

**Tech Stack:** Kotlin 2.x / Spring Boot 4.x / Spring Data JPA / Spring for GraphQL / Spring AI MCP / PostgreSQL 16 / Flyway / Angular 21 / NgRx Signal Store / Angular Material / Apollo Angular / Vitest / Playwright

**Spec:** `docs/plans/2026-03-16-phase-7-mirror-oldreader-design.md`

---

## Chunk 1: Database Migration + FeedCategory Entity + Repository

### Task 1: Create V6 Migration ✓

**Files:**
- Create: `src/main/resources/db/migration/V6__feed_categories_and_preferences.sql`

- [x] **Step 1: Write the migration file**

```sql
-- Feed categories (single-level, no hierarchy)
CREATE TABLE feed_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    version INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_feed_categories_user_name ON feed_categories(user_id, name);

-- Seed "Subscribed" category for existing users
INSERT INTO feed_categories (id, user_id, name, sort_order)
SELECT gen_random_uuid(), id, 'Subscribed', 0 FROM users;

-- Add category_id to feeds (populate with user's "Subscribed" category)
ALTER TABLE feeds ADD COLUMN category_id UUID;
UPDATE feeds SET category_id = (
    SELECT fc.id FROM feed_categories fc
    WHERE fc.user_id = feeds.user_id AND fc.name = 'Subscribed'
);
ALTER TABLE feeds ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE feeds ADD CONSTRAINT fk_feeds_category FOREIGN KEY (category_id) REFERENCES feed_categories(id);

-- Starred articles stub
ALTER TABLE feed_items ADD COLUMN starred_at TIMESTAMPTZ;

-- User preferences
ALTER TABLE users ADD COLUMN view_mode VARCHAR(10) NOT NULL DEFAULT 'LIST';
ALTER TABLE users ADD COLUMN sort_order VARCHAR(20) NOT NULL DEFAULT 'NEWEST_FIRST';

-- API keys stub
CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    key_hash VARCHAR(255) NOT NULL,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    version INT NOT NULL DEFAULT 0
);

-- OAuth stub
CREATE TABLE user_auth_providers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    provider VARCHAR(50) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    access_token VARCHAR(1024),
    refresh_token VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    version INT NOT NULL DEFAULT 0,
    UNIQUE(provider, external_id)
);
```

- [x] **Step 2: Verify migration applies**

Run: `./gradlew test --tests '*FeedIntegrationTest*' -x :client:test 2>&1 | tail -20`

Expected: Tests pass (Flyway runs V6 migration automatically via TestContainers). If there are failures, they will be about Feed entity not matching schema (expected — we haven't updated the entity yet). The key check is that the migration itself doesn't error.

- [x] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V6__feed_categories_and_preferences.sql
git commit -m "feat: V6 migration — feed categories, user preferences, starred/api-key/oauth stubs"
```

---

### Task 2: Create FeedCategory Entity ✓

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/feed/entity/FeedCategory.kt`

- [x] **Step 1: Write the entity**

Follow the pattern from `Folder.kt` (bookmark/entity/Folder.kt) and `Feed.kt` (feed/entity/Feed.kt):

```kotlin
package org.sightech.memoryvault.feed.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "feed_categories")
class FeedCategory(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    @Version
    val version: Long = 0
) {
    companion object {
        const val SUBSCRIBED_NAME = "Subscribed"
    }
}
```

- [x] **Step 2: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/feed/entity/FeedCategory.kt
git commit -m "feat: FeedCategory entity"
```

---

### Task 3: Update Feed Entity with categoryId ✓

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/feed/entity/Feed.kt`

- [x] **Step 1: Add categoryId field and ManyToOne relationship**

Add these fields to the `Feed` class constructor, after `failureCount`:

```kotlin
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    var category: FeedCategory? = null,
```

Add the import: `import org.sightech.memoryvault.feed.entity.FeedCategory`

- [x] **Step 2: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/feed/entity/Feed.kt
git commit -m "feat: add category relationship to Feed entity"
```

---

### Task 4: Update User Entity with Preferences ✓

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/auth/entity/User.kt`

- [x] **Step 1: Add viewMode and sortOrder fields**

Add these fields to the `User` class constructor, before `createdAt`:

```kotlin
    @Column(name = "view_mode", nullable = false, length = 10)
    var viewMode: String = "LIST",

    @Column(name = "sort_order", nullable = false, length = 20)
    var sortOrder: String = "NEWEST_FIRST",
```

- [x] **Step 2: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/auth/entity/User.kt
git commit -m "feat: add viewMode/sortOrder preference fields to User entity"
```

---

### Task 5: Update FeedItem Entity with starredAt ✓

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/feed/entity/FeedItem.kt`

- [x] **Step 1: Add starredAt field**

Add this field to the `FeedItem` class constructor, after `readAt`:

```kotlin
    @Column(name = "starred_at")
    var starredAt: Instant? = null,
```

- [x] **Step 2: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/feed/entity/FeedItem.kt
git commit -m "feat: add starredAt stub field to FeedItem entity"
```

---

### Task 6: Create FeedCategoryRepository ✓

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/feed/repository/FeedCategoryRepository.kt`

- [x] **Step 1: Write the repository**

Follow the pattern from `FeedRepository.kt`:

```kotlin
package org.sightech.memoryvault.feed.repository

import org.sightech.memoryvault.feed.entity.FeedCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface FeedCategoryRepository : JpaRepository<FeedCategory, UUID> {

    @Query("SELECT fc FROM FeedCategory fc WHERE fc.deletedAt IS NULL AND fc.userId = :userId ORDER BY fc.sortOrder, fc.name")
    fun findAllActiveByUserId(userId: UUID): List<FeedCategory>

    @Query("SELECT fc FROM FeedCategory fc WHERE fc.id = :id AND fc.userId = :userId AND fc.deletedAt IS NULL")
    fun findActiveByIdAndUserId(id: UUID, userId: UUID): FeedCategory?

    @Query("SELECT fc FROM FeedCategory fc WHERE fc.userId = :userId AND fc.deletedAt IS NULL AND LOWER(fc.name) = LOWER(:name)")
    fun findActiveByUserIdAndNameIgnoreCase(userId: UUID, name: String): FeedCategory?

    @Query("SELECT COALESCE(MAX(fc.sortOrder), 0) FROM FeedCategory fc WHERE fc.userId = :userId AND fc.deletedAt IS NULL")
    fun findMaxSortOrderByUserId(userId: UUID): Int
}
```

- [x] **Step 2: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/feed/repository/FeedCategoryRepository.kt
git commit -m "feat: FeedCategoryRepository"
```

---

### Task 7: Update FeedRepository with Category Queries ✓

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/feed/repository/FeedRepository.kt`

- [x] **Step 1: Add category-related queries**

Add these methods to `FeedRepository`:

```kotlin
    @Query("SELECT f FROM Feed f WHERE f.deletedAt IS NULL AND f.userId = :userId AND f.category.id = :categoryId ORDER BY f.title")
    fun findAllActiveByCategoryId(userId: UUID, categoryId: UUID): List<Feed>

    @Modifying
    @Query("UPDATE feeds SET category_id = :targetCategoryId, updated_at = :now WHERE category_id = :sourceCategoryId AND user_id = :userId AND deleted_at IS NULL", nativeQuery = true)
    fun moveFeedsBetweenCategories(sourceCategoryId: UUID, targetCategoryId: UUID, userId: UUID, now: Instant): Int
```

Add these imports at the top:
```kotlin
import org.springframework.data.jpa.repository.Modifying
import java.time.Instant
```

- [x] **Step 2: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/feed/repository/FeedRepository.kt
git commit -m "feat: add category queries to FeedRepository"
```

---

### Task 8: Verify Entity/Migration Alignment ✓

- [x] **Step 1: Run all backend tests**

Run: `./gradlew test -x :client:test 2>&1 | tail -30`

Expected: All tests pass. The migration creates the tables, entities map to them correctly, Hibernate validation (ddl-auto=validate) succeeds.

- [x] **Step 2: If tests fail due to Feed entity requiring category**

Existing tests create `Feed` objects without a `category` field. Update `FeedServiceTest.kt` to supply a mock FeedCategory:

In `FeedServiceTest.kt`, add at the top of the class:
```kotlin
private val defaultCategory = FeedCategory(userId = userId, name = "Subscribed")
```

Update each `Feed(...)` constructor call to include `category = defaultCategory`. For example:
```kotlin
val feed = Feed(userId = userId, url = "https://example.com/rss", category = defaultCategory)
```

- [x] **Step 3: Commit if changes were needed**

```bash
git add -A
git commit -m "fix: update existing tests for Feed.category field"
```

---

## Chunk 2: FeedCategoryService + FeedService Updates

### Task 9: Create FeedCategoryService ✓

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/feed/service/FeedCategoryService.kt`

- [x] **Step 1: Write the service**

```kotlin
package org.sightech.memoryvault.feed.service

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.feed.entity.FeedCategory
import org.sightech.memoryvault.feed.repository.FeedCategoryRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class FeedCategoryService(
    private val categoryRepository: FeedCategoryRepository,
    private val feedRepository: FeedRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun listCategories(): List<FeedCategory> {
        val userId = CurrentUser.userId()
        return categoryRepository.findAllActiveByUserId(userId)
    }

    fun getCategoryById(categoryId: UUID): FeedCategory? {
        val userId = CurrentUser.userId()
        return categoryRepository.findActiveByIdAndUserId(categoryId, userId)
    }

    fun getSubscribedCategory(): FeedCategory {
        val userId = CurrentUser.userId()
        return categoryRepository.findActiveByUserIdAndNameIgnoreCase(userId, FeedCategory.SUBSCRIBED_NAME)
            ?: createCategory(FeedCategory.SUBSCRIBED_NAME)
    }

    /**
     * Returns Pair(category, wasCreated) so callers can track creation counts.
     */
    fun findOrCreateByName(name: String): Pair<FeedCategory, Boolean> {
        val userId = CurrentUser.userId()
        val existing = categoryRepository.findActiveByUserIdAndNameIgnoreCase(userId, name)
        return if (existing != null) {
            existing to false
        } else {
            createCategory(name) to true
        }
    }

    fun createCategory(name: String): FeedCategory {
        val userId = CurrentUser.userId()
        val maxSort = categoryRepository.findMaxSortOrderByUserId(userId)
        val category = FeedCategory(userId = userId, name = name, sortOrder = maxSort + 1)
        log.info("Created feed category '{}' for user {}", name, userId)
        return categoryRepository.save(category)
    }

    fun renameCategory(categoryId: UUID, newName: String): FeedCategory? {
        val userId = CurrentUser.userId()
        val category = categoryRepository.findActiveByIdAndUserId(categoryId, userId) ?: return null
        if (category.name == FeedCategory.SUBSCRIBED_NAME) {
            throw IllegalArgumentException("Cannot rename the '${FeedCategory.SUBSCRIBED_NAME}' category")
        }
        category.name = newName
        category.updatedAt = Instant.now()
        log.info("Renamed feed category {} to '{}' for user {}", categoryId, newName, userId)
        return categoryRepository.save(category)
    }

    @Transactional
    fun deleteCategory(categoryId: UUID): Boolean {
        val userId = CurrentUser.userId()
        val category = categoryRepository.findActiveByIdAndUserId(categoryId, userId) ?: return false
        if (category.name == FeedCategory.SUBSCRIBED_NAME) {
            throw IllegalArgumentException("Cannot delete the '${FeedCategory.SUBSCRIBED_NAME}' category")
        }
        val subscribed = getSubscribedCategory()
        val moved = feedRepository.moveFeedsBetweenCategories(categoryId, subscribed.id, userId, Instant.now())
        log.info("Moved {} feed(s) from category {} to Subscribed before deletion", moved, categoryId)
        category.deletedAt = Instant.now()
        category.updatedAt = Instant.now()
        categoryRepository.save(category)
        log.info("Deleted feed category {} for user {}", categoryId, userId)
        return true
    }

    @Transactional
    fun reorderCategories(categoryIds: List<UUID>): List<FeedCategory> {
        val userId = CurrentUser.userId()
        val categories = categoryRepository.findAllActiveByUserId(userId)
        val categoryMap = categories.associateBy { it.id }
        val now = Instant.now()
        categoryIds.forEachIndexed { index, id ->
            val cat = categoryMap[id] ?: return@forEachIndexed
            cat.sortOrder = index
            cat.updatedAt = now
        }
        log.info("Reordered {} feed categories for user {}", categoryIds.size, userId)
        return categoryRepository.saveAll(categories)
    }
}
```

- [x] **Step 2: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/feed/service/FeedCategoryService.kt
git commit -m "feat: FeedCategoryService with CRUD, reorder, and delete-with-move"
```

---

### Task 10: Write FeedCategoryService Unit Tests ✓

**Files:**
- Create: `src/test/kotlin/org/sightech/memoryvault/feed/service/FeedCategoryServiceTest.kt`

- [x] **Step 1: Write the test class**

Follow the pattern from `FeedServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.feed.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.FeedCategory
import org.sightech.memoryvault.feed.repository.FeedCategoryRepository
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class FeedCategoryServiceTest {

    private val categoryRepository = mockk<FeedCategoryRepository>()
    private val feedRepository = mockk<FeedRepository>()
    private val service = FeedCategoryService(categoryRepository, feedRepository)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        val securityContext = mockk<SecurityContext>()
        val authentication = mockk<Authentication>()
        every { securityContext.authentication } returns authentication
        every { authentication.principal } returns userId.toString()
        SecurityContextHolder.setContext(securityContext)
    }

    @AfterTest
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `createCategory assigns next sort order`() {
        every { categoryRepository.findMaxSortOrderByUserId(userId) } returns 2
        every { categoryRepository.save(any()) } answers { firstArg() }

        val result = service.createCategory("Tech")

        assertEquals("Tech", result.name)
        assertEquals(3, result.sortOrder)
        verify { categoryRepository.save(any()) }
    }

    @Test
    fun `renameCategory prevents renaming Subscribed`() {
        val subscribed = FeedCategory(userId = userId, name = "Subscribed", sortOrder = 0)
        every { categoryRepository.findActiveByIdAndUserId(subscribed.id, userId) } returns subscribed

        assertFailsWith<IllegalArgumentException> {
            service.renameCategory(subscribed.id, "Something Else")
        }
    }

    @Test
    fun `renameCategory updates name`() {
        val category = FeedCategory(userId = userId, name = "Old Name", sortOrder = 1)
        every { categoryRepository.findActiveByIdAndUserId(category.id, userId) } returns category
        every { categoryRepository.save(any()) } answers { firstArg() }

        val result = service.renameCategory(category.id, "New Name")

        assertNotNull(result)
        assertEquals("New Name", result.name)
    }

    @Test
    fun `deleteCategory prevents deleting Subscribed`() {
        val subscribed = FeedCategory(userId = userId, name = "Subscribed", sortOrder = 0)
        every { categoryRepository.findActiveByIdAndUserId(subscribed.id, userId) } returns subscribed

        assertFailsWith<IllegalArgumentException> {
            service.deleteCategory(subscribed.id)
        }
    }

    @Test
    fun `deleteCategory moves feeds to Subscribed then soft deletes`() {
        val subscribed = FeedCategory(userId = userId, name = "Subscribed", sortOrder = 0)
        val category = FeedCategory(userId = userId, name = "Tech", sortOrder = 1)
        every { categoryRepository.findActiveByIdAndUserId(category.id, userId) } returns category
        every { categoryRepository.findActiveByUserIdAndNameIgnoreCase(userId, "Subscribed") } returns subscribed
        every { feedRepository.moveFeedsBetweenCategories(category.id, subscribed.id, userId, any()) } returns 3
        every { categoryRepository.save(any()) } answers { firstArg() }

        val result = service.deleteCategory(category.id)

        assertTrue(result)
        assertNotNull(category.deletedAt)
        verify { feedRepository.moveFeedsBetweenCategories(category.id, subscribed.id, userId, any()) }
    }

    @Test
    fun `reorderCategories sets sort order by position`() {
        val cat1 = FeedCategory(userId = userId, name = "A", sortOrder = 0)
        val cat2 = FeedCategory(userId = userId, name = "B", sortOrder = 1)
        every { categoryRepository.findAllActiveByUserId(userId) } returns listOf(cat1, cat2)
        every { categoryRepository.saveAll(any<List<FeedCategory>>()) } answers { firstArg() }

        service.reorderCategories(listOf(cat2.id, cat1.id))

        assertEquals(1, cat1.sortOrder)
        assertEquals(0, cat2.sortOrder)
    }
}
```

- [x] **Step 2: Run tests**

Run: `./gradlew test --tests '*FeedCategoryServiceTest*' -x :client:test 2>&1 | tail -20`

Expected: All tests pass.

- [x] **Step 3: Commit**

```bash
git add src/test/kotlin/org/sightech/memoryvault/feed/service/FeedCategoryServiceTest.kt
git commit -m "test: FeedCategoryService unit tests"
```

---

### Task 11: Update FeedService to Use Categories

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/feed/service/FeedService.kt`

- [x] **Step 1: Add FeedCategoryService dependency and update addFeed**

Update the constructor to inject `FeedCategoryService`:

```kotlin
@Service
class FeedService(
    private val feedRepository: FeedRepository,
    private val feedItemRepository: FeedItemRepository,
    private val rssFetchService: RssFetchService,
    private val feedCategoryService: FeedCategoryService
) {
```

Update `addFeed` to accept optional `categoryId`:

```kotlin
    suspend fun addFeed(url: String, categoryId: UUID? = null): Feed {
        val userId = CurrentUser.userId()
        val category = if (categoryId != null) {
            feedCategoryService.getCategoryById(categoryId)
                ?: feedCategoryService.getSubscribedCategory()
        } else {
            feedCategoryService.getSubscribedCategory()
        }
        val feed = feedRepository.save(Feed(userId = userId, url = url, category = category))
        rssFetchService.fetchAndStore(feed)
        return feed
    }
```

Add `listFeedsByCategory` method:

```kotlin
    fun listFeedsByCategory(categoryId: UUID): List<Pair<Feed, Long>> {
        val userId = CurrentUser.userId()
        val feeds = feedRepository.findAllActiveByCategoryId(userId, categoryId)
        return feeds.map { feed ->
            val unreadCount = feedItemRepository.countUnreadByFeedIdAndUserId(feed.id, userId)
            feed to unreadCount
        }
    }
```

Add `moveFeedToCategory` method:

```kotlin
    fun moveFeedToCategory(feedId: UUID, categoryId: UUID): Feed? {
        val userId = CurrentUser.userId()
        val feed = feedRepository.findActiveByIdAndUserId(feedId, userId) ?: return null
        val category = feedCategoryService.getCategoryById(categoryId) ?: return null
        feed.category = category
        feed.updatedAt = Instant.now()
        return feedRepository.save(feed)
    }
```

- [x] **Step 2: Update FeedServiceTest for new dependency**

In `FeedServiceTest.kt`, add the mock and update constructor:

```kotlin
private val feedCategoryService = mockk<FeedCategoryService>()
private val service = FeedService(feedRepository, feedItemRepository, rssFetchService, feedCategoryService)
private val defaultCategory = FeedCategory(userId = userId, name = "Subscribed")
```

Update `addFeed` test to mock category lookup:
```kotlin
every { feedCategoryService.getSubscribedCategory() } returns defaultCategory
```

- [x] **Step 3: Run tests**

Run: `./gradlew test --tests '*FeedServiceTest*' -x :client:test 2>&1 | tail -20`

Expected: All tests pass.

- [x] **Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/feed/service/FeedService.kt src/test/kotlin/org/sightech/memoryvault/feed/service/FeedServiceTest.kt
git commit -m "feat: update FeedService with category support"
```

---

### Task 12: Update FeedItemService with Sort Order and markCategoryRead

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/feed/service/FeedItemService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/feed/repository/FeedItemRepository.kt`

- [x] **Step 1: Add sort direction to FeedItemRepository queries**

In `FeedItemRepository.kt`, add ASC variants of the existing queries:

```kotlin
    @Query("SELECT fi FROM FeedItem fi LEFT JOIN FETCH fi.tags WHERE fi.feed.id = :feedId AND fi.feed.userId = :userId ORDER BY fi.publishedAt ASC NULLS LAST")
    fun findByFeedIdAndUserIdAsc(feedId: UUID, userId: UUID): List<FeedItem>

    @Query("SELECT fi FROM FeedItem fi LEFT JOIN FETCH fi.tags WHERE fi.feed.id = :feedId AND fi.feed.userId = :userId AND fi.readAt IS NULL ORDER BY fi.publishedAt ASC NULLS LAST")
    fun findUnreadByFeedIdAndUserIdAsc(feedId: UUID, userId: UUID): List<FeedItem>
```

Also add queries for fetching items across multiple feeds (for category view):

```kotlin
    @Query("SELECT fi FROM FeedItem fi LEFT JOIN FETCH fi.tags WHERE fi.feed.id IN :feedIds AND fi.feed.userId = :userId ORDER BY fi.publishedAt DESC NULLS LAST")
    fun findByFeedIdsAndUserId(feedIds: List<UUID>, userId: UUID): List<FeedItem>

    @Query("SELECT fi FROM FeedItem fi LEFT JOIN FETCH fi.tags WHERE fi.feed.id IN :feedIds AND fi.feed.userId = :userId AND fi.readAt IS NULL ORDER BY fi.publishedAt DESC NULLS LAST")
    fun findUnreadByFeedIdsAndUserId(feedIds: List<UUID>, userId: UUID): List<FeedItem>

    @Query("SELECT fi FROM FeedItem fi LEFT JOIN FETCH fi.tags WHERE fi.feed.id IN :feedIds AND fi.feed.userId = :userId ORDER BY fi.publishedAt ASC NULLS LAST")
    fun findByFeedIdsAndUserIdAsc(feedIds: List<UUID>, userId: UUID): List<FeedItem>

    @Query("SELECT fi FROM FeedItem fi LEFT JOIN FETCH fi.tags WHERE fi.feed.id IN :feedIds AND fi.feed.userId = :userId AND fi.readAt IS NULL ORDER BY fi.publishedAt ASC NULLS LAST")
    fun findUnreadByFeedIdsAndUserIdAsc(feedIds: List<UUID>, userId: UUID): List<FeedItem>
```

- [x] **Step 2: Update FeedItemService.getItems with sortOrder parameter**

```kotlin
    fun getItems(feedId: UUID, limit: Int?, unreadOnly: Boolean, sortOrder: String = "NEWEST_FIRST"): List<FeedItem> {
        val userId = CurrentUser.userId()
        val ascending = sortOrder == "OLDEST_FIRST"
        val items = if (unreadOnly) {
            if (ascending) feedItemRepository.findUnreadByFeedIdAndUserIdAsc(feedId, userId)
            else feedItemRepository.findUnreadByFeedIdAndUserId(feedId, userId)
        } else {
            if (ascending) feedItemRepository.findByFeedIdAndUserIdAsc(feedId, userId)
            else feedItemRepository.findByFeedIdAndUserId(feedId, userId)
        }
        return if (limit != null && limit > 0) items.take(limit) else items
    }
```

- [x] **Step 3: Add getItemsByFeedIds method for category/all-items view**

```kotlin
    fun getItemsByFeedIds(feedIds: List<UUID>, limit: Int?, unreadOnly: Boolean, sortOrder: String = "NEWEST_FIRST"): List<FeedItem> {
        if (feedIds.isEmpty()) return emptyList()
        val userId = CurrentUser.userId()
        val ascending = sortOrder == "OLDEST_FIRST"
        val items = if (unreadOnly) {
            if (ascending) feedItemRepository.findUnreadByFeedIdsAndUserIdAsc(feedIds, userId)
            else feedItemRepository.findUnreadByFeedIdsAndUserId(feedIds, userId)
        } else {
            if (ascending) feedItemRepository.findByFeedIdsAndUserIdAsc(feedIds, userId)
            else feedItemRepository.findByFeedIdsAndUserId(feedIds, userId)
        }
        return if (limit != null && limit > 0) items.take(limit) else items
    }
```

- [x] **Step 4: Add markCategoryRead method**

```kotlin
    @Transactional
    fun markCategoryRead(categoryId: UUID): Int {
        val userId = CurrentUser.userId()
        val feeds = feedRepository.findAllActiveByCategoryId(userId, categoryId)
        val now = Instant.now()
        return feeds.sumOf { feed ->
            feedItemRepository.markAllReadByFeedIdAndUserId(feed.id, userId, now)
        }
    }
```

Add `FeedRepository` as a constructor dependency:

```kotlin
@Service
class FeedItemService(
    private val feedItemRepository: FeedItemRepository,
    private val feedRepository: FeedRepository
) {
```

- [x] **Step 5: Add starred item stubs (commented out)**

Add at the end of `FeedItemService`:

```kotlin
    // TODO: Phase 7 stub — starred articles
    // fun starItem(itemId: UUID): FeedItem? {
    //     val userId = CurrentUser.userId()
    //     val item = feedItemRepository.findByIdAndUserId(itemId, userId) ?: return null
    //     item.starredAt = Instant.now()
    //     return feedItemRepository.save(item)
    // }

    // fun unstarItem(itemId: UUID): FeedItem? {
    //     val userId = CurrentUser.userId()
    //     val item = feedItemRepository.findByIdAndUserId(itemId, userId) ?: return null
    //     item.starredAt = null
    //     return feedItemRepository.save(item)
    // }
```

- [x] **Step 6: Run tests**

Run: `./gradlew test --tests '*FeedItemService*' -x :client:test 2>&1 | tail -20`

Expected: Tests pass. Existing tests may need updating for the new `FeedRepository` constructor parameter — add a mock if needed.

- [x] **Step 7: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/feed/service/FeedItemService.kt src/main/kotlin/org/sightech/memoryvault/feed/repository/FeedItemRepository.kt
git commit -m "feat: FeedItemService with sort order, multi-feed queries, markCategoryRead, starred stubs"
```

---

## Chunk 3: OPML Service

### Task 13: Create OpmlService

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/feed/service/OpmlService.kt`

- [ ] **Step 1: Write the OPML service**

```kotlin
package org.sightech.memoryvault.feed.service

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedCategory
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.w3c.dom.Element
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

data class ImportResult(
    val feedsAdded: Int,
    val feedsSkipped: Int,
    val categoriesCreated: Int
)

@Service
class OpmlService(
    private val feedRepository: FeedRepository,
    private val feedService: FeedService,
    private val feedCategoryService: FeedCategoryService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun exportOpml(): String {
        val userId = CurrentUser.userId()
        val categories = feedCategoryService.listCategories()
        val feeds = feedRepository.findAllActiveByUserId(userId)
        val feedsByCategory = feeds.groupBy { it.category?.id }

        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = docBuilder.newDocument()

        val opml = doc.createElement("opml").also { it.setAttribute("version", "2.0") }
        doc.appendChild(opml)

        val head = doc.createElement("head")
        val title = doc.createElement("title").also { it.textContent = "MemoryVault Feed Subscriptions" }
        head.appendChild(title)
        opml.appendChild(head)

        val body = doc.createElement("body")
        opml.appendChild(body)

        for (category in categories) {
            val categoryFeeds = feedsByCategory[category.id] ?: continue
            if (categoryFeeds.isEmpty()) continue

            val categoryOutline = doc.createElement("outline").also {
                it.setAttribute("text", category.name)
                it.setAttribute("title", category.name)
            }
            for (feed in categoryFeeds) {
                val feedOutline = doc.createElement("outline").also {
                    it.setAttribute("type", "rss")
                    it.setAttribute("text", feed.title ?: feed.url)
                    it.setAttribute("title", feed.title ?: feed.url)
                    it.setAttribute("xmlUrl", feed.url)
                    if (feed.siteUrl != null) it.setAttribute("htmlUrl", feed.siteUrl)
                }
                categoryOutline.appendChild(feedOutline)
            }
            body.appendChild(categoryOutline)
        }

        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        }
        val writer = StringWriter()
        transformer.transform(DOMSource(doc), StreamResult(writer))
        return writer.toString()
    }

    suspend fun importOpml(opmlContent: String): ImportResult {
        val userId = CurrentUser.userId()
        val existingFeeds = feedRepository.findAllActiveByUserId(userId).map { it.url.lowercase() }.toSet()

        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = docBuilder.parse(opmlContent.byteInputStream())
        val body = doc.getElementsByTagName("body").item(0) as? Element
            ?: return ImportResult(0, 0, 0)

        var feedsAdded = 0
        var feedsSkipped = 0
        var categoriesCreated = 0

        val outlines = body.childNodes
        for (i in 0 until outlines.length) {
            val node = outlines.item(i)
            if (node !is Element || node.tagName != "outline") continue

            val xmlUrl = node.getAttribute("xmlUrl")
            if (xmlUrl.isNotBlank()) {
                // Top-level feed (no category wrapper)
                if (xmlUrl.lowercase() in existingFeeds) {
                    feedsSkipped++
                } else {
                    feedService.addFeed(xmlUrl)
                    feedsAdded++
                }
            } else {
                // Category folder — child outlines are feeds
                val categoryName = node.getAttribute("text").ifBlank { node.getAttribute("title") }
                if (categoryName.isBlank()) continue

                var categoryObj: FeedCategory? = null
                val children = node.childNodes
                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child !is Element || child.tagName != "outline") continue
                    val childXmlUrl = child.getAttribute("xmlUrl")
                    if (childXmlUrl.isBlank()) continue

                    if (childXmlUrl.lowercase() in existingFeeds) {
                        feedsSkipped++
                        continue
                    }

                    // Lazily resolve/create category only if we have feeds to add
                    if (categoryObj == null) {
                        val (category, wasCreated) = feedCategoryService.findOrCreateByName(categoryName)
                        if (wasCreated) categoriesCreated++
                        categoryObj = category
                    }

                    feedService.addFeed(childXmlUrl, categoryObj.id)
                    feedsAdded++
                }
            }
        }

        log.info("OPML import for user {}: added={}, skipped={}, categoriesCreated={}", userId, feedsAdded, feedsSkipped, categoriesCreated)
        return ImportResult(feedsAdded, feedsSkipped, categoriesCreated)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/feed/service/OpmlService.kt
git commit -m "feat: OpmlService for OPML import/export"
```

---

### Task 14: Write OpmlService Unit Tests

**Files:**
- Create: `src/test/kotlin/org/sightech/memoryvault/feed/service/OpmlServiceTest.kt`

- [ ] **Step 1: Write tests**

```kotlin
package org.sightech.memoryvault.feed.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedCategory
import org.sightech.memoryvault.feed.repository.FeedRepository
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpmlServiceTest {

    private val feedRepository = mockk<FeedRepository>()
    private val feedService = mockk<FeedService>()
    private val feedCategoryService = mockk<FeedCategoryService>()
    private val service = OpmlService(feedRepository, feedService, feedCategoryService)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        val securityContext = mockk<SecurityContext>()
        val authentication = mockk<Authentication>()
        every { securityContext.authentication } returns authentication
        every { authentication.principal } returns userId.toString()
        SecurityContextHolder.setContext(securityContext)
    }

    @AfterTest
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `exportOpml produces valid OPML with categories`() {
        val category = FeedCategory(userId = userId, name = "Tech", sortOrder = 1)
        val feed = Feed(userId = userId, url = "https://example.com/rss", category = category).apply {
            title = "Example Blog"
            siteUrl = "https://example.com"
        }
        every { feedCategoryService.listCategories() } returns listOf(category)
        every { feedRepository.findAllActiveByUserId(userId) } returns listOf(feed)

        val result = service.exportOpml()

        assertTrue(result.contains("opml"))
        assertTrue(result.contains("Tech"))
        assertTrue(result.contains("https://example.com/rss"))
        assertTrue(result.contains("Example Blog"))
    }

    @Test
    fun `importOpml skips existing feeds and creates new ones`() {
        val existingFeed = Feed(userId = userId, url = "https://existing.com/rss")
        every { feedRepository.findAllActiveByUserId(userId) } returns listOf(existingFeed)
        val newFeed = Feed(userId = userId, url = "https://new.com/rss")
        coEvery { feedService.addFeed("https://new.com/rss", any()) } returns newFeed
        val category = FeedCategory(userId = userId, name = "News")
        every { feedCategoryService.findOrCreateByName("News") } returns (category to true)

        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>Test</title></head>
              <body>
                <outline text="News" title="News">
                  <outline type="rss" text="Existing" xmlUrl="https://existing.com/rss" />
                  <outline type="rss" text="New Feed" xmlUrl="https://new.com/rss" />
                </outline>
              </body>
            </opml>
        """.trimIndent()

        val result = runBlocking { service.importOpml(opml) }

        assertEquals(1, result.feedsAdded)
        assertEquals(1, result.feedsSkipped)
    }

    @Test
    fun `importOpml handles top-level feeds without category`() {
        every { feedRepository.findAllActiveByUserId(userId) } returns emptyList()
        val newFeed = Feed(userId = userId, url = "https://example.com/rss")
        coEvery { feedService.addFeed("https://example.com/rss") } returns newFeed

        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head><title>Test</title></head>
              <body>
                <outline type="rss" text="Example" xmlUrl="https://example.com/rss" />
              </body>
            </opml>
        """.trimIndent()

        val result = runBlocking { service.importOpml(opml) }

        assertEquals(1, result.feedsAdded)
        assertEquals(0, result.feedsSkipped)
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests '*OpmlServiceTest*' -x :client:test 2>&1 | tail -20`

Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/org/sightech/memoryvault/feed/service/OpmlServiceTest.kt
git commit -m "test: OpmlService unit tests"
```

---

## Chunk 4: GraphQL Schema + Resolvers

### Task 15: Update GraphQL Schema

**Files:**
- Modify: `src/main/resources/graphql/feeds.graphqls`
- Modify: `src/main/resources/graphql/schema.graphqls`

- [ ] **Step 1: Add types to feeds.graphqls**

Append to `feeds.graphqls`:

```graphql
type FeedCategory {
    id: UUID!
    name: String!
    sortOrder: Int!
}

type FeedCategoryWithFeeds {
    category: FeedCategory!
    feeds: [FeedWithUnread!]!
    totalUnread: Int!
}

type ImportResult {
    feedsAdded: Int!
    feedsSkipped: Int!
    categoriesCreated: Int!
}

type UserPreferences {
    viewMode: String!
    sortOrder: String!
}
```

Update `Feed` type to include category:
```graphql
type Feed {
    id: UUID!
    url: String!
    title: String
    description: String
    siteUrl: String
    lastFetchedAt: Instant
    failureCount: Int!
    categoryId: UUID
}
```

Update `FeedItem` to include `starredAt`:
```graphql
type FeedItem {
    id: UUID!
    feedId: UUID!
    title: String
    url: String
    content: String
    author: String
    imageUrl: String
    publishedAt: Instant
    readAt: Instant
    starredAt: Instant
    tags: [Tag!]!
}
```

- [ ] **Step 2: Add queries and mutations to schema.graphqls**

Add to the `Query` type:
```graphql
    # Feed categories
    feedCategories: [FeedCategoryWithFeeds!]!
    feedItemsByCategory(categoryId: UUID!, limit: Int, unreadOnly: Boolean, sortOrder: String): [FeedItem!]!
    feedItemsAll(limit: Int, unreadOnly: Boolean, sortOrder: String): [FeedItem!]!
    exportFeeds: String!
    userPreferences: UserPreferences!
```

Update existing `feedItems` query to add `sortOrder`:
```graphql
    feedItems(feedId: UUID!, limit: Int, unreadOnly: Boolean, sortOrder: String): [FeedItem!]!
```

Add to the `Mutation` type:
```graphql
    # Feed categories
    addCategory(name: String!): FeedCategory!
    renameCategory(categoryId: UUID!, name: String!): FeedCategory
    deleteCategory(categoryId: UUID!): Boolean!
    reorderCategories(categoryIds: [UUID!]!): [FeedCategory!]!
    moveFeedToCategory(feedId: UUID!, categoryId: UUID!): Feed
    markCategoryRead(categoryId: UUID!): Int!
    importFeeds(opml: String!): ImportResult!
    updateUserPreferences(viewMode: String, sortOrder: String): UserPreferences!
```

Update existing `addFeed` mutation:
```graphql
    addFeed(url: String!, categoryId: UUID): Feed!
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/graphql/feeds.graphqls src/main/resources/graphql/schema.graphqls
git commit -m "feat: GraphQL schema for feed categories, preferences, OPML, starred stubs"
```

---

### Task 16: Create FeedCategoryResolver

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/graphql/FeedCategoryResolver.kt`

- [ ] **Step 1: Write the resolver**

```kotlin
package org.sightech.memoryvault.graphql

import kotlinx.coroutines.runBlocking
import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.feed.entity.FeedCategory
import org.sightech.memoryvault.feed.service.FeedCategoryService
import org.sightech.memoryvault.feed.service.FeedItemService
import org.sightech.memoryvault.feed.service.FeedService
import org.sightech.memoryvault.feed.service.ImportResult
import org.sightech.memoryvault.feed.service.OpmlService
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.time.Instant
import java.util.UUID

@Controller
class FeedCategoryResolver(
    private val feedCategoryService: FeedCategoryService,
    private val feedService: FeedService,
    private val feedItemService: FeedItemService,
    private val opmlService: OpmlService,
    private val userRepository: org.sightech.memoryvault.auth.repository.UserRepository
) {

    @QueryMapping
    fun feedCategories(): List<Map<String, Any?>> {
        val categories = feedCategoryService.listCategories()
        return categories.map { category ->
            val feedsWithUnread = feedService.listFeedsByCategory(category.id)
            val totalUnread = feedsWithUnread.sumOf { it.second }
            mapOf(
                "category" to category,
                "feeds" to feedsWithUnread.map { (feed, unread) -> mapOf("feed" to feed, "unreadCount" to unread) },
                "totalUnread" to totalUnread
            )
        }
    }

    @QueryMapping
    fun feedItemsByCategory(
        @Argument categoryId: UUID,
        @Argument limit: Int?,
        @Argument unreadOnly: Boolean?,
        @Argument sortOrder: String?
    ): List<org.sightech.memoryvault.feed.entity.FeedItem> {
        val feeds = feedService.listFeedsByCategory(categoryId)
        val feedIds = feeds.map { it.first.id }
        return feedItemService.getItemsByFeedIds(feedIds, limit, unreadOnly ?: false, sortOrder ?: "NEWEST_FIRST")
    }

    @QueryMapping
    fun feedItemsAll(
        @Argument limit: Int?,
        @Argument unreadOnly: Boolean?,
        @Argument sortOrder: String?
    ): List<org.sightech.memoryvault.feed.entity.FeedItem> {
        val feeds = feedService.listFeeds()
        val feedIds = feeds.map { it.first.id }
        return feedItemService.getItemsByFeedIds(feedIds, limit, unreadOnly ?: false, sortOrder ?: "NEWEST_FIRST")
    }

    @QueryMapping
    fun exportFeeds(): String {
        return opmlService.exportOpml()
    }

    @QueryMapping
    fun userPreferences(): Map<String, String> {
        val userId = CurrentUser.userId()
        val user = userRepository.findById(userId).orElseThrow()
        return mapOf("viewMode" to user.viewMode, "sortOrder" to user.sortOrder)
    }

    @MutationMapping
    fun addCategory(@Argument name: String): FeedCategory {
        return feedCategoryService.createCategory(name)
    }

    @MutationMapping
    fun renameCategory(@Argument categoryId: UUID, @Argument name: String): FeedCategory? {
        return feedCategoryService.renameCategory(categoryId, name)
    }

    @MutationMapping
    fun deleteCategory(@Argument categoryId: UUID): Boolean {
        return feedCategoryService.deleteCategory(categoryId)
    }

    @MutationMapping
    fun reorderCategories(@Argument categoryIds: List<UUID>): List<FeedCategory> {
        return feedCategoryService.reorderCategories(categoryIds)
    }

    @MutationMapping
    fun moveFeedToCategory(@Argument feedId: UUID, @Argument categoryId: UUID): org.sightech.memoryvault.feed.entity.Feed? {
        return feedService.moveFeedToCategory(feedId, categoryId)
    }

    @MutationMapping
    fun markCategoryRead(@Argument categoryId: UUID): Int {
        return feedItemService.markCategoryRead(categoryId)
    }

    @MutationMapping
    fun importFeeds(@Argument opml: String): ImportResult {
        return runBlocking { opmlService.importOpml(opml) }
    }

    @MutationMapping
    fun updateUserPreferences(@Argument viewMode: String?, @Argument sortOrder: String?): Map<String, String> {
        val userId = CurrentUser.userId()
        val user = userRepository.findById(userId).orElseThrow()
        if (viewMode != null) user.viewMode = viewMode
        if (sortOrder != null) user.sortOrder = sortOrder
        user.updatedAt = Instant.now()
        userRepository.save(user)
        return mapOf("viewMode" to user.viewMode, "sortOrder" to user.sortOrder)
    }
}
```

- [ ] **Step 2: Check if UserRepository exists, create if needed**

Look for `src/main/kotlin/org/sightech/memoryvault/auth/repository/UserRepository.kt`. If it doesn't exist, create it:

```kotlin
package org.sightech.memoryvault.auth.repository

import org.sightech.memoryvault.auth.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
}
```

- [ ] **Step 3: Update existing FeedResolver.addFeed to accept categoryId**

In `src/main/kotlin/org/sightech/memoryvault/graphql/FeedResolver.kt`, update the `addFeed` mutation:

```kotlin
    @MutationMapping
    fun addFeed(@Argument url: String, @Argument categoryId: UUID?): Feed {
        return runBlocking { feedService.addFeed(url, categoryId) }
    }
```

Also update the `feedItems` query to accept `sortOrder`:

```kotlin
    @QueryMapping
    fun feedItems(
        @Argument feedId: UUID,
        @Argument limit: Int?,
        @Argument unreadOnly: Boolean?,
        @Argument sortOrder: String?
    ): List<FeedItem> {
        return feedItemService.getItems(feedId, limit, unreadOnly ?: false, sortOrder ?: "NEWEST_FIRST")
    }
```

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/graphql/FeedCategoryResolver.kt src/main/kotlin/org/sightech/memoryvault/graphql/FeedResolver.kt
git commit -m "feat: FeedCategoryResolver + update FeedResolver for categories and sort order"
```

---

### Task 17: Update MCP FeedTools + Create FeedCategoryTools

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/mcp/FeedTools.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/mcp/FeedCategoryTools.kt`

- [ ] **Step 1: Update FeedTools**

Update `addFeed` to accept optional categoryId:

```kotlin
    @Tool(description = "Subscribe to an RSS feed. Use when the user wants to add, follow, or subscribe to an RSS or Atom feed by URL. Optionally assign to a category.")
    fun addFeed(url: String, categoryId: String?): String {
        val catUuid = categoryId?.let { UUID.fromString(it) }
        val feed = runBlocking { feedService.addFeed(url, catUuid) }
        return "Subscribed to feed: \"${feed.title ?: feed.url}\" (${feed.url}) — id: ${feed.id}"
    }
```

Update `getFeedItems` to accept sortOrder:

```kotlin
    @Tool(description = "Browse items from an RSS feed. Use when the user wants to read or see articles from a specific feed. Set unreadOnly to true to see only unread items. sortOrder can be NEWEST_FIRST or OLDEST_FIRST.")
    fun getFeedItems(feedId: String, limit: Int?, unreadOnly: Boolean?, sortOrder: String?): String {
        val items = feedItemService.getItems(UUID.fromString(feedId), limit, unreadOnly ?: false, sortOrder ?: "NEWEST_FIRST")
```

Add import/export tools:

```kotlin
    @Tool(description = "Export all feed subscriptions as OPML 2.0 XML. Use when the user wants to back up or migrate their feeds.")
    fun exportFeeds(): String {
        return opmlService.exportOpml()
    }

    @Tool(description = "Import feed subscriptions from OPML XML content. Use when the user wants to bulk-import feeds from another RSS reader. Automatically creates categories and skips duplicates.")
    fun importFeeds(opmlContent: String): String {
        val result = runBlocking { opmlService.importOpml(opmlContent) }
        return "Import complete: ${result.feedsAdded} feed(s) added, ${result.feedsSkipped} skipped (duplicate), ${result.categoriesCreated} new category/categories created."
    }
```

Add `OpmlService` to constructor:

```kotlin
@Component
class FeedTools(
    private val feedService: FeedService,
    private val feedItemService: FeedItemService,
    private val opmlService: OpmlService
) {
```

Add starred stubs (commented out):

```kotlin
    // TODO: Phase 7 stub — starred articles
    // @Tool(description = "Star a feed item to save it for later reading.")
    // fun starItem(itemId: String): String {
    //     val item = feedItemService.starItem(UUID.fromString(itemId))
    //         ?: return "Feed item not found."
    //     return "Starred: \"${item.title ?: "(no title)"}\""
    // }

    // @Tool(description = "Remove star from a feed item.")
    // fun unstarItem(itemId: String): String {
    //     val item = feedItemService.unstarItem(UUID.fromString(itemId))
    //         ?: return "Feed item not found."
    //     return "Unstarred: \"${item.title ?: "(no title)"}\""
    // }
```

- [ ] **Step 2: Create FeedCategoryTools**

```kotlin
package org.sightech.memoryvault.mcp

import org.sightech.memoryvault.feed.service.FeedCategoryService
import org.sightech.memoryvault.feed.service.FeedItemService
import org.sightech.memoryvault.feed.service.FeedService
import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class FeedCategoryTools(
    private val feedCategoryService: FeedCategoryService,
    private val feedService: FeedService,
    private val feedItemService: FeedItemService
) {

    @Tool(description = "Create a new feed category. Use when the user wants to organize feeds into groups like 'Tech', 'News', etc.")
    fun addCategory(name: String): String {
        val category = feedCategoryService.createCategory(name)
        return "Created category: \"${category.name}\" — id: ${category.id}"
    }

    @Tool(description = "List all feed categories with their feed counts. Use when the user wants to see how their feeds are organized.")
    fun listCategories(): String {
        val categories = feedCategoryService.listCategories()
        if (categories.isEmpty()) return "No categories found."
        val lines = categories.map { cat ->
            val feeds = feedService.listFeedsByCategory(cat.id)
            "- ${cat.name} (${feeds.size} feed(s), sortOrder: ${cat.sortOrder}) — id: ${cat.id}"
        }
        return "${categories.size} category/categories:\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Rename a feed category. Use when the user wants to change the name of a category. Cannot rename the 'Subscribed' category.")
    fun renameCategory(categoryId: String, name: String): String {
        val category = feedCategoryService.renameCategory(UUID.fromString(categoryId), name)
            ?: return "Category not found."
        return "Renamed to: \"${category.name}\""
    }

    @Tool(description = "Delete a feed category. Moves all its feeds to the 'Subscribed' category. Use when the user wants to remove a category. Cannot delete 'Subscribed'.")
    fun deleteCategory(categoryId: String): String {
        val deleted = feedCategoryService.deleteCategory(UUID.fromString(categoryId))
        return if (deleted) "Category deleted. Feeds moved to Subscribed." else "Category not found."
    }

    @Tool(description = "Move a feed to a different category. Use when the user wants to reorganize their feeds.")
    fun moveFeedToCategory(feedId: String, categoryId: String): String {
        val feed = feedService.moveFeedToCategory(UUID.fromString(feedId), UUID.fromString(categoryId))
            ?: return "Feed or category not found."
        return "Moved \"${feed.title ?: feed.url}\" to new category."
    }

    @Tool(description = "Reorder feed categories. Pass a list of category IDs in the desired order. Use when the user wants to change the order categories appear in.")
    fun reorderCategories(categoryIds: List<String>): String {
        val uuids = categoryIds.map { UUID.fromString(it) }
        feedCategoryService.reorderCategories(uuids)
        return "Categories reordered."
    }

    @Tool(description = "Mark all items in all feeds within a category as read. Use when the user wants to clear all unread items in a category at once.")
    fun markCategoryRead(categoryId: String): String {
        val count = feedItemService.markCategoryRead(UUID.fromString(categoryId))
        return "Marked $count item(s) as read."
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/mcp/FeedTools.kt src/main/kotlin/org/sightech/memoryvault/mcp/FeedCategoryTools.kt
git commit -m "feat: FeedCategoryTools MCP + update FeedTools with category/OPML/starred stubs"
```

---

## Chunk 5: Stub Entities (API Keys, OAuth)

### Task 18: Create Stub Entities and Services

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/auth/entity/ApiKey.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/auth/entity/UserAuthProvider.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/auth/service/ApiKeyService.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/auth/service/OAuthService.kt`

- [ ] **Step 1: Create ApiKey entity stub**

```kotlin
package org.sightech.memoryvault.auth.entity

// TODO: Phase 7 stub — API key authentication
// Uncomment and implement when API key auth is needed.

// import jakarta.persistence.*
// import java.time.Instant
// import java.util.UUID
//
// @Entity
// @Table(name = "api_keys")
// class ApiKey(
//     @Id
//     val id: UUID = UUID.randomUUID(),
//
//     @Column(name = "user_id", nullable = false)
//     val userId: UUID,
//
//     @Column(nullable = false, length = 255)
//     var name: String,
//
//     @Column(name = "key_hash", nullable = false, length = 255)
//     val keyHash: String,
//
//     @Column(name = "last_used_at")
//     var lastUsedAt: Instant? = null,
//
//     @Column(name = "created_at", nullable = false, updatable = false)
//     val createdAt: Instant = Instant.now(),
//
//     @Column(name = "deleted_at")
//     var deletedAt: Instant? = null,
//
//     @Version
//     val version: Long = 0
// )
```

- [ ] **Step 2: Create UserAuthProvider entity stub**

```kotlin
package org.sightech.memoryvault.auth.entity

// TODO: Phase 7 stub — OAuth provider linking
// Uncomment and implement when OAuth integration is needed.
// Will also need spring-boot-starter-oauth2-client dependency in build.gradle.kts.

// import jakarta.persistence.*
// import java.time.Instant
// import java.util.UUID
//
// @Entity
// @Table(name = "user_auth_providers")
// class UserAuthProvider(
//     @Id
//     val id: UUID = UUID.randomUUID(),
//
//     @Column(name = "user_id", nullable = false)
//     val userId: UUID,
//
//     @Column(nullable = false, length = 50)
//     val provider: String,
//
//     @Column(name = "external_id", nullable = false, length = 255)
//     val externalId: String,
//
//     @Column(name = "access_token", length = 1024)
//     var accessToken: String? = null,
//
//     @Column(name = "refresh_token", length = 1024)
//     var refreshToken: String? = null,
//
//     @Column(name = "created_at", nullable = false, updatable = false)
//     val createdAt: Instant = Instant.now(),
//
//     @Column(name = "updated_at", nullable = false)
//     var updatedAt: Instant = Instant.now(),
//
//     @Column(name = "deleted_at")
//     var deletedAt: Instant? = null,
//
//     @Version
//     val version: Long = 0
// )
```

- [ ] **Step 3: Create ApiKeyService stub**

```kotlin
package org.sightech.memoryvault.auth.service

// TODO: Phase 7 stub — API key management
// Uncomment and implement when API key auth is needed.
// Will need: ApiKeyRepository, ApiKeyController, SecurityConfig filter for API key auth header.

// import org.springframework.stereotype.Service
// import java.util.UUID
//
// @Service
// class ApiKeyService {
//     fun createKey(name: String): Pair<String, UUID> { TODO("Generate key, hash it, store hash, return raw key + id") }
//     fun validateKey(rawKey: String): UUID? { TODO("Hash raw key, look up in DB, return userId if valid") }
//     fun revokeKey(keyId: UUID): Boolean { TODO("Soft delete the key") }
//     fun listKeys(): List<Any> { TODO("Return active keys for current user (without the hash)") }
// }
```

- [ ] **Step 4: Create OAuthService stub**

```kotlin
package org.sightech.memoryvault.auth.service

// TODO: Phase 7 stub — OAuth provider integration
// Uncomment and implement when OAuth login is needed.
// Will need: UserAuthProviderRepository, SecurityConfig OAuth2 client config,
// spring-boot-starter-oauth2-client dependency, provider-specific scopes.

// import org.springframework.stereotype.Service
// import java.util.UUID
//
// @Service
// class OAuthService {
//     fun linkProvider(userId: UUID, provider: String, externalId: String, accessToken: String?, refreshToken: String?) { TODO() }
//     fun unlinkProvider(userId: UUID, provider: String) { TODO() }
//     fun findByProvider(provider: String, externalId: String): UUID? { TODO("Return userId if linked") }
// }
```

- [ ] **Step 5: Add commented-out OAuth properties to application.properties**

Append to `src/main/resources/application.properties`:

```properties

# TODO: OAuth2 Client Configuration — uncomment and configure when OAuth login is implemented
# spring.security.oauth2.client.registration.google.client-id=YOUR_GOOGLE_CLIENT_ID
# spring.security.oauth2.client.registration.google.client-secret=YOUR_GOOGLE_CLIENT_SECRET
# spring.security.oauth2.client.registration.google.scope=openid,profile,email
# spring.security.oauth2.client.registration.github.client-id=YOUR_GITHUB_CLIENT_ID
# spring.security.oauth2.client.registration.github.client-secret=YOUR_GITHUB_CLIENT_SECRET
```

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/auth/entity/ApiKey.kt src/main/kotlin/org/sightech/memoryvault/auth/entity/UserAuthProvider.kt src/main/kotlin/org/sightech/memoryvault/auth/service/ApiKeyService.kt src/main/kotlin/org/sightech/memoryvault/auth/service/OAuthService.kt src/main/resources/application.properties
git commit -m "feat: stub entities and services for API keys and OAuth"
```

---

## Chunk 6: Angular Frontend — Store + GraphQL + Category Sidebar

### Task 19: Update Angular GraphQL Operations

**Files:**
- Modify: `client/src/app/reader/reader.graphql`

- [ ] **Step 1: Add new queries and mutations**

Replace the entire file content:

```graphql
query GetFeeds {
  feeds {
    feed {
      id
      url
      title
      siteUrl
      categoryId
    }
    unreadCount
  }
}

query GetFeedCategories {
  feedCategories {
    category {
      id
      name
      sortOrder
    }
    feeds {
      feed {
        id
        url
        title
        siteUrl
        categoryId
      }
      unreadCount
    }
    totalUnread
  }
}

query GetFeedItems($feedId: UUID!, $limit: Int, $unreadOnly: Boolean, $sortOrder: String) {
  feedItems(feedId: $feedId, limit: $limit, unreadOnly: $unreadOnly, sortOrder: $sortOrder) {
    id
    feedId
    title
    url
    content
    author
    publishedAt
    readAt
  }
}

query GetFeedItemsByCategory($categoryId: UUID!, $limit: Int, $unreadOnly: Boolean, $sortOrder: String) {
  feedItemsByCategory(categoryId: $categoryId, limit: $limit, unreadOnly: $unreadOnly, sortOrder: $sortOrder) {
    id
    feedId
    title
    url
    content
    author
    publishedAt
    readAt
  }
}

query GetAllFeedItems($limit: Int, $unreadOnly: Boolean, $sortOrder: String) {
  feedItemsAll(limit: $limit, unreadOnly: $unreadOnly, sortOrder: $sortOrder) {
    id
    feedId
    title
    url
    content
    author
    publishedAt
    readAt
  }
}

query GetUserPreferences {
  userPreferences {
    viewMode
    sortOrder
  }
}

query ExportFeeds {
  exportFeeds
}

mutation MarkItemRead($itemId: UUID!) {
  markItemRead(itemId: $itemId) {
    id
    readAt
  }
}

mutation MarkItemUnread($itemId: UUID!) {
  markItemUnread(itemId: $itemId) {
    id
    readAt
  }
}

mutation MarkFeedRead($feedId: UUID!) {
  markFeedRead(feedId: $feedId)
}

mutation MarkCategoryRead($categoryId: UUID!) {
  markCategoryRead(categoryId: $categoryId)
}

mutation AddFeed($url: String!, $categoryId: UUID) {
  addFeed(url: $url, categoryId: $categoryId) {
    id
    url
    title
    categoryId
  }
}

mutation DeleteFeed($feedId: UUID!) {
  deleteFeed(feedId: $feedId) {
    id
  }
}

mutation AddCategory($name: String!) {
  addCategory(name: $name) {
    id
    name
    sortOrder
  }
}

mutation RenameCategory($categoryId: UUID!, $name: String!) {
  renameCategory(categoryId: $categoryId, name: $name) {
    id
    name
  }
}

mutation DeleteCategory($categoryId: UUID!) {
  deleteCategory(categoryId: $categoryId)
}

mutation ReorderCategories($categoryIds: [UUID!]!) {
  reorderCategories(categoryIds: $categoryIds) {
    id
    sortOrder
  }
}

mutation MoveFeedToCategory($feedId: UUID!, $categoryId: UUID!) {
  moveFeedToCategory(feedId: $feedId, categoryId: $categoryId) {
    id
    categoryId
  }
}

mutation ImportFeeds($opml: String!) {
  importFeeds(opml: $opml) {
    feedsAdded
    feedsSkipped
    categoriesCreated
  }
}

mutation UpdateUserPreferences($viewMode: String, $sortOrder: String) {
  updateUserPreferences(viewMode: $viewMode, sortOrder: $sortOrder) {
    viewMode
    sortOrder
  }
}
```

- [ ] **Step 2: Regenerate GraphQL types**

Run: `cd client && npm run codegen` (or whatever the graphql-codegen command is — check `package.json` scripts)

- [ ] **Step 3: Commit**

```bash
git add client/src/app/reader/reader.graphql client/src/app/shared/graphql/generated.ts
git commit -m "feat: Angular GraphQL operations for feed categories, preferences, OPML"
```

---

### Task 20: Refactor Reader Store

**Files:**
- Modify: `client/src/app/reader/reader.store.ts`

- [ ] **Step 1: Rewrite the store with category support**

This is a significant rewrite. The store needs to manage categories, selection state (all items / category / single feed), view preferences, and all the new mutations. Replace the entire file:

```typescript
import { signalStore, withState, withMethods, withComputed, patchState } from '@ngrx/signals';
import { inject, computed } from '@angular/core';
import { Apollo } from 'apollo-angular';
import {
  GetFeedCategoriesDocument,
  GetFeedItemsDocument,
  GetFeedItemsByCategoryDocument,
  GetAllFeedItemsDocument,
  GetUserPreferencesDocument,
  MarkItemReadDocument,
  MarkItemUnreadDocument,
  MarkFeedReadDocument,
  MarkCategoryReadDocument,
  AddFeedDocument,
  DeleteFeedDocument,
  AddCategoryDocument,
  RenameCategoryDocument,
  DeleteCategoryDocument,
  MoveFeedToCategoryDocument,
  ImportFeedsDocument,
  ExportFeedsDocument,
  UpdateUserPreferencesDocument,
} from '../shared/graphql/generated';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { pipe, switchMap, tap } from 'rxjs';

export type SelectionType = 'all' | 'category' | 'feed';

// NOTE: Replace `any[]` with proper generated types from graphql-codegen after running codegen.
// Use types like GetFeedCategoriesQuery['feedCategories'] and FeedItem[] from generated.ts.
export interface ReaderState {
  categories: any[];  // TODO: replace with generated FeedCategoryWithFeeds[] type
  selectedType: SelectionType;
  selectedId: string | null;  // categoryId or feedId, null for 'all'
  items: any[];  // TODO: replace with generated FeedItem[] type
  loadingCategories: boolean;
  loadingItems: boolean;
  unreadOnly: boolean;
  viewMode: 'LIST' | 'FULL';
  sortOrder: 'NEWEST_FIRST' | 'OLDEST_FIRST';
}

const initialState: ReaderState = {
  categories: [],
  selectedType: 'all',
  selectedId: null,
  items: [],
  loadingCategories: false,
  loadingItems: false,
  unreadOnly: true,
  viewMode: 'LIST',
  sortOrder: 'NEWEST_FIRST',
};

export const ReaderStore = signalStore(
  // Provided at component level in ReaderComponent, not root
  withState(initialState),
  withComputed((store) => ({
    selectedTitle: computed(() => {
      if (store.selectedType() === 'all') return 'All Items';
      if (store.selectedType() === 'category') {
        const cat = store.categories().find((c: any) => c.category.id === store.selectedId());
        return cat?.category.name || 'Category';
      }
      // feed
      for (const cat of store.categories()) {
        const feed = cat.feeds.find((f: any) => f.feed.id === store.selectedId());
        if (feed) return feed.feed.title || feed.feed.url;
      }
      return 'Feed';
    }),
    totalUnread: computed(() => {
      return store.categories().reduce((sum: number, cat: any) => sum + cat.totalUnread, 0);
    }),
  })),
  withMethods((store, apollo = inject(Apollo)) => {
    const loadItems = () => {
      patchState(store, { loadingItems: true });
      const type = store.selectedType();
      const id = store.selectedId();
      const unreadOnly = store.unreadOnly();
      const sortOrder = store.sortOrder();
      const limit = 100;

      let query$;
      if (type === 'all') {
        query$ = apollo.query({
          query: GetAllFeedItemsDocument,
          variables: { limit, unreadOnly, sortOrder },
          fetchPolicy: 'network-only',
        });
      } else if (type === 'category') {
        query$ = apollo.query({
          query: GetFeedItemsByCategoryDocument,
          variables: { categoryId: id, limit, unreadOnly, sortOrder },
          fetchPolicy: 'network-only',
        });
      } else {
        query$ = apollo.query({
          query: GetFeedItemsDocument,
          variables: { feedId: id, limit, unreadOnly, sortOrder },
          fetchPolicy: 'network-only',
        });
      }

      query$.subscribe((result: any) => {
        const items = result.data.feedItemsAll || result.data.feedItemsByCategory || result.data.feedItems;
        patchState(store, { items: items || [], loadingItems: false });
      });
    };

    const loadCategories = () => {
      patchState(store, { loadingCategories: true });
      apollo.query({ query: GetFeedCategoriesDocument, fetchPolicy: 'network-only' }).subscribe((result: any) => {
        patchState(store, { categories: result.data.feedCategories, loadingCategories: false });
      });
    };

    const loadPreferences = () => {
      apollo.query({ query: GetUserPreferencesDocument, fetchPolicy: 'network-only' }).subscribe((result: any) => {
        const prefs = result.data.userPreferences;
        patchState(store, { viewMode: prefs.viewMode, sortOrder: prefs.sortOrder });
      });
    };

    return {
      init: () => {
        loadCategories();
        loadPreferences();
        loadItems();
      },

      selectAll: () => {
        patchState(store, { selectedType: 'all', selectedId: null });
        loadItems();
      },

      selectCategory: (categoryId: string) => {
        patchState(store, { selectedType: 'category', selectedId: categoryId });
        loadItems();
      },

      selectFeed: (feedId: string) => {
        patchState(store, { selectedType: 'feed', selectedId: feedId });
        loadItems();
      },

      setUnreadOnly: (unreadOnly: boolean) => {
        patchState(store, { unreadOnly });
        loadItems();
      },

      setViewMode: (viewMode: 'LIST' | 'FULL') => {
        patchState(store, { viewMode });
        apollo.mutate({
          mutation: UpdateUserPreferencesDocument,
          variables: { viewMode },
        }).subscribe();
      },

      setSortOrder: (sortOrder: 'NEWEST_FIRST' | 'OLDEST_FIRST') => {
        patchState(store, { sortOrder });
        apollo.mutate({
          mutation: UpdateUserPreferencesDocument,
          variables: { sortOrder },
        }).subscribe();
        loadItems();
      },

      markAsRead: (itemId: string) => {
        apollo.mutate({ mutation: MarkItemReadDocument, variables: { itemId } }).subscribe(() => {
          loadItems();
          loadCategories();
        });
      },

      markAsUnread: (itemId: string) => {
        apollo.mutate({ mutation: MarkItemUnreadDocument, variables: { itemId } }).subscribe(() => {
          loadItems();
          loadCategories();
        });
      },

      markFeedRead: (feedId: string) => {
        apollo.mutate({ mutation: MarkFeedReadDocument, variables: { feedId } }).subscribe(() => {
          loadItems();
          loadCategories();
        });
      },

      markCategoryRead: (categoryId: string) => {
        apollo.mutate({ mutation: MarkCategoryReadDocument, variables: { categoryId } }).subscribe(() => {
          loadItems();
          loadCategories();
        });
      },

      addFeed: (url: string, categoryId?: string) => {
        apollo.mutate({ mutation: AddFeedDocument, variables: { url, categoryId } }).subscribe(() => {
          loadCategories();
        });
      },

      deleteFeed: (feedId: string) => {
        apollo.mutate({ mutation: DeleteFeedDocument, variables: { feedId } }).subscribe(() => {
          loadCategories();
          if (store.selectedType() === 'feed' && store.selectedId() === feedId) {
            patchState(store, { selectedType: 'all', selectedId: null });
          }
          loadItems();
        });
      },

      addCategory: (name: string) => {
        apollo.mutate({ mutation: AddCategoryDocument, variables: { name } }).subscribe(() => {
          loadCategories();
        });
      },

      renameCategory: (categoryId: string, name: string) => {
        apollo.mutate({ mutation: RenameCategoryDocument, variables: { categoryId, name } }).subscribe(() => {
          loadCategories();
        });
      },

      deleteCategory: (categoryId: string) => {
        apollo.mutate({ mutation: DeleteCategoryDocument, variables: { categoryId } }).subscribe(() => {
          loadCategories();
          if (store.selectedType() === 'category' && store.selectedId() === categoryId) {
            patchState(store, { selectedType: 'all', selectedId: null });
          }
          loadItems();
        });
      },

      moveFeedToCategory: (feedId: string, categoryId: string) => {
        apollo.mutate({ mutation: MoveFeedToCategoryDocument, variables: { feedId, categoryId } }).subscribe(() => {
          loadCategories();
        });
      },

      importFeeds: (opml: string, callback?: (result: any) => void) => {
        apollo.mutate({ mutation: ImportFeedsDocument, variables: { opml } }).subscribe((result: any) => {
          loadCategories();
          if (callback) callback(result.data.importFeeds);
        });
      },

      exportFeeds: (callback: (opml: string) => void) => {
        apollo.query({ query: ExportFeedsDocument, fetchPolicy: 'network-only' }).subscribe((result: any) => {
          callback(result.data.exportFeeds);
        });
      },

      refreshItems: () => {
        loadItems();
        loadCategories();
      },
    };
  })
);
```

- [ ] **Step 2: Commit**

```bash
git add client/src/app/reader/reader.store.ts
git commit -m "feat: refactor ReaderStore with categories, preferences, OPML, multi-feed selection"
```

---

### Task 21: Create Category Sidebar Component

**Files:**
- Create: `client/src/app/reader/category-sidebar/category-sidebar.ts`
- Create: `client/src/app/reader/category-sidebar/category-sidebar.html`
- Create: `client/src/app/reader/category-sidebar/category-sidebar.css`
- Create: `client/src/app/reader/category-sidebar/index.ts`

This is a large UI task. The implementer should create a sidebar component that:
- Shows "All Items" at the top with total unread badge
- Lists categories with expandable feed lists, each showing unread count
- Hides "Subscribed" category when it has zero feeds
- Right-click context menus or icon buttons for: add feed, delete feed, move feed, add/rename/delete category
- Uses Angular Material `mat-nav-list`, `mat-expansion-panel`, `mat-badge`, `mat-menu`
- Uses `@angular/cdk/drag-drop` (`cdkDropList`, `cdkDrag`) for category reordering — on drop, calls `store.reorderCategories()` with the new order

- [ ] **Step 1: Create the component with barrel export**

The component injects `ReaderStore` and exposes methods for selection and management actions. All management actions (add feed, delete feed, add/rename/delete category, move feed) open Material dialog boxes.

Template structure:
```html
<!-- All Items -->
<mat-list-item (click)="store.selectAll()" [class.selected]="store.selectedType() === 'all'">
  All Items <mat-badge [matBadge]="store.totalUnread()" ...></mat-badge>
</mat-list-item>

<!-- Categories -->
@for (cat of store.categories(); track cat.category.id) {
  @if (cat.category.name !== 'Subscribed' || cat.feeds.length > 0) {
    <mat-expansion-panel>
      <mat-expansion-panel-header (click)="store.selectCategory(cat.category.id)">
        {{ cat.category.name }} <mat-badge [matBadge]="cat.totalUnread" ...></mat-badge>
      </mat-expansion-panel-header>
      @for (f of cat.feeds; track f.feed.id) {
        <mat-list-item (click)="store.selectFeed(f.feed.id)">
          {{ f.feed.title }} <mat-badge ...></mat-badge>
        </mat-list-item>
      }
    </mat-expansion-panel>
  }
}
```

Barrel export (`index.ts`):
```typescript
export { CategorySidebarComponent } from './category-sidebar';
```

- [ ] **Step 2: Commit**

```bash
git add client/src/app/reader/category-sidebar/
git commit -m "feat: CategorySidebar component with category/feed selection and management"
```

---

### Task 22: Create Feed Toolbar Component

**Files:**
- Create: `client/src/app/reader/feed-toolbar/feed-toolbar.ts`
- Create: `client/src/app/reader/feed-toolbar/feed-toolbar.html`
- Create: `client/src/app/reader/feed-toolbar/feed-toolbar.css`
- Create: `client/src/app/reader/feed-toolbar/index.ts`

- [ ] **Step 1: Create toolbar component**

Shows: selected title, view mode toggle (list/full icons), sort order toggle, unread filter, mark-as-read button, refresh button.

Template structure:
```html
<mat-toolbar>
  <span>{{ store.selectedTitle() }}</span>
  <span class="spacer"></span>
  <!-- View mode toggle -->
  <button mat-icon-button (click)="toggleViewMode()">
    <mat-icon>{{ store.viewMode() === 'LIST' ? 'view_list' : 'view_agenda' }}</mat-icon>
  </button>
  <!-- Sort order toggle -->
  <button mat-icon-button (click)="toggleSortOrder()">
    <mat-icon>{{ store.sortOrder() === 'NEWEST_FIRST' ? 'arrow_downward' : 'arrow_upward' }}</mat-icon>
  </button>
  <!-- Unread filter -->
  <button mat-button (click)="store.setUnreadOnly(!store.unreadOnly())">
    {{ store.unreadOnly() ? 'Show All' : 'Show Unread' }}
  </button>
  <!-- Refresh -->
  <button mat-icon-button (click)="store.refreshItems()"><mat-icon>refresh</mat-icon></button>
</mat-toolbar>
```

- [ ] **Step 2: Commit**

```bash
git add client/src/app/reader/feed-toolbar/
git commit -m "feat: FeedToolbar component with view/sort/filter controls"
```

---

### Task 23: Create Feed List View and Full View Components

**Files:**
- Create: `client/src/app/reader/feed-list-view/feed-list-view.ts`
- Create: `client/src/app/reader/feed-list-view/feed-list-view.html`
- Create: `client/src/app/reader/feed-list-view/feed-list-view.css`
- Create: `client/src/app/reader/feed-list-view/index.ts`
- Create: `client/src/app/reader/feed-full-view/feed-full-view.ts`
- Create: `client/src/app/reader/feed-full-view/feed-full-view.html`
- Create: `client/src/app/reader/feed-full-view/feed-full-view.css`
- Create: `client/src/app/reader/feed-full-view/index.ts`

- [ ] **Step 1: Create list view component**

Compact expansion panels (existing pattern from current `reader.html`). Each row: title, author, date. Click to expand. Adds `IntersectionObserver` for scroll-mark-as-read and manual read/unread toggle button per article.

The `IntersectionObserver` pattern:
```typescript
private observer = new IntersectionObserver((entries) => {
  entries.forEach(entry => {
    if (!entry.isIntersecting && entry.boundingClientRect.top < 0) {
      // Article scrolled past viewport upward — mark as read
      const itemId = (entry.target as HTMLElement).dataset['itemId'];
      if (itemId) this.store.markAsRead(itemId);
    }
  });
}, { threshold: 0 });

// In ngAfterViewInit or after items load, observe each article element
```

- [ ] **Step 2: Create full view component**

Stacked articles with full content visible. No expansion panels. Same IntersectionObserver pattern for scroll-mark-as-read. Manual read/unread toggle. "View Original" link per article.

- [ ] **Step 3: Commit**

```bash
git add client/src/app/reader/feed-list-view/ client/src/app/reader/feed-full-view/
git commit -m "feat: FeedListView and FeedFullView components with scroll-mark-as-read"
```

---

### Task 24: Create Feed Management and OPML Import Dialogs

**Files:**
- Create: `client/src/app/reader/feed-management/add-feed-dialog.ts`
- Create: `client/src/app/reader/feed-management/add-category-dialog.ts`
- Create: `client/src/app/reader/feed-management/rename-category-dialog.ts`
- Create: `client/src/app/reader/feed-management/move-feed-dialog.ts`
- Create: `client/src/app/reader/feed-management/delete-confirm-dialog.ts`
- Create: `client/src/app/reader/feed-management/index.ts`
- Create: `client/src/app/reader/opml-import/opml-import.ts`
- Create: `client/src/app/reader/opml-import/opml-import.html`
- Create: `client/src/app/reader/opml-import/opml-import.css`
- Create: `client/src/app/reader/opml-import/index.ts`

- [ ] **Step 1: Create feed management dialogs**

Each dialog is its own standalone component file (one responsibility per file):
- **AddFeedDialog** (`add-feed-dialog.ts`): URL input + category dropdown (defaults to "Subscribed"). Uses `MatDialogRef` and `MAT_DIALOG_DATA`.
- **AddCategoryDialog** (`add-category-dialog.ts`): Name input
- **RenameCategoryDialog** (`rename-category-dialog.ts`): Name input (pre-filled with current name via `MAT_DIALOG_DATA`)
- **MoveFeedDialog** (`move-feed-dialog.ts`): Category dropdown, receives available categories via `MAT_DIALOG_DATA`
- **DeleteConfirmDialog** (`delete-confirm-dialog.ts`): Generic confirmation with entity name via `MAT_DIALOG_DATA`

Barrel export re-exports all dialog components.

- [ ] **Step 2: Create OPML import dialog**

File upload via `<input type="file" accept=".opml,.xml">`. Reads file content client-side with `FileReader`, sends string to `importFeeds` mutation. Shows results summary after import.

- [ ] **Step 3: Commit**

```bash
git add client/src/app/reader/feed-management/ client/src/app/reader/opml-import/
git commit -m "feat: feed management dialogs and OPML import dialog"
```

---

### Task 25: Refactor Reader Shell Component

**Files:**
- Modify: `client/src/app/reader/reader.ts`
- Modify: `client/src/app/reader/reader.html`
- Modify: `client/src/app/reader/reader.css`

- [ ] **Step 1: Update reader.ts to compose sub-components**

Replace the monolithic component with a shell that imports and uses the sub-components:

```typescript
import { Component, inject, OnInit } from '@angular/core';
import { MatSidenavModule } from '@angular/material/sidenav';
import { ReaderStore } from './reader.store';
import { CategorySidebarComponent } from './category-sidebar';
import { FeedToolbarComponent } from './feed-toolbar';
import { FeedListViewComponent } from './feed-list-view';
import { FeedFullViewComponent } from './feed-full-view';

@Component({
  selector: 'app-reader',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatSidenavModule,
    CategorySidebarComponent,
    FeedToolbarComponent,
    FeedListViewComponent,
    FeedFullViewComponent,
  ],
  providers: [ReaderStore],
  templateUrl: './reader.html',
  styleUrl: './reader.css'
})
export class ReaderComponent implements OnInit {
  readonly store = inject(ReaderStore);

  ngOnInit(): void {
    this.store.init();
  }
}
```

- [ ] **Step 2: Update reader.html**

```html
<mat-sidenav-container class="reader-container">
  <mat-sidenav mode="side" opened class="reader-sidebar">
    <app-category-sidebar />
  </mat-sidenav>

  <mat-sidenav-content class="reader-content">
    <app-feed-toolbar />

    @if (store.loadingItems()) {
      <div class="spinner-container large">
        <mat-spinner></mat-spinner>
      </div>
    } @else if (store.viewMode() === 'LIST') {
      <app-feed-list-view />
    } @else {
      <app-feed-full-view />
    }
  </mat-sidenav-content>
</mat-sidenav-container>
```

- [ ] **Step 3: Update reader.css**

Keep existing styles but remove article-specific styles that moved to sub-components.

- [ ] **Step 4: Commit**

```bash
git add client/src/app/reader/
git commit -m "feat: refactor reader into shell component composing category sidebar, toolbar, and view components"
```

---

## Chunk 7: Integration Tests + E2E + Final Verification

### Task 26: Write FeedCategory Integration Test

**Files:**
- Create: `src/test/kotlin/org/sightech/memoryvault/feed/FeedCategoryIntegrationTest.kt`

- [ ] **Step 1: Write integration test**

Follow the pattern from `FeedIntegrationTest.kt`. Test the full flow: create category, create feed in category, move feed, delete category (verify feeds move to Subscribed), OPML export/import round-trip.

- [ ] **Step 2: Run tests**

Run: `./gradlew test --tests '*FeedCategoryIntegrationTest*' -x :client:test 2>&1 | tail -30`

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/org/sightech/memoryvault/feed/FeedCategoryIntegrationTest.kt
git commit -m "test: FeedCategory integration test with OPML round-trip"
```

---

### Task 27: Write Stub Test Files

**Files:**
- Create: `src/test/kotlin/org/sightech/memoryvault/feed/service/StarredArticlesTest.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/auth/service/ApiKeyServiceTest.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/auth/service/OAuthServiceTest.kt`

- [ ] **Step 1: Create stub test files with TODO placeholders**

Each file follows this pattern:
```kotlin
package org.sightech.memoryvault.feed.service

// TODO: Phase 7 stub — uncomment and implement when starred articles feature is active
// class StarredArticlesTest {
//     @Test fun `starItem sets starredAt timestamp`() { TODO() }
//     @Test fun `unstarItem clears starredAt`() { TODO() }
//     @Test fun `starItem returns null for nonexistent item`() { TODO() }
// }
```

- [ ] **Step 2: Commit**

```bash
git add src/test/kotlin/org/sightech/memoryvault/feed/service/StarredArticlesTest.kt src/test/kotlin/org/sightech/memoryvault/auth/service/ApiKeyServiceTest.kt src/test/kotlin/org/sightech/memoryvault/auth/service/OAuthServiceTest.kt
git commit -m "test: stub test files for starred articles, API keys, OAuth"
```

---

### Task 28: Write Frontend Unit Tests

**Files:**
- Create: `client/src/app/reader/reader.store.spec.ts`

- [ ] **Step 1: Write store unit tests**

Test the key store methods: `init`, `selectAll`, `selectCategory`, `selectFeed`, `setViewMode`, `setSortOrder`, `markAsRead`, `markAsUnread`, `addFeed`, `deleteFeed`, `addCategory`, `deleteCategory`, `importFeeds`. Mock Apollo queries/mutations. Verify `patchState` calls and GraphQL variables.

- [ ] **Step 2: Run frontend tests**

Run: `cd client && npm run test 2>&1 | tail -30`

Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
git add client/src/app/reader/reader.store.spec.ts
git commit -m "test: ReaderStore unit tests"
```

---

### Task 29: Write Playwright E2E Tests

**Files:**
- Create: `client/e2e/reader-categories.spec.ts`

- [ ] **Step 1: Write E2E tests**

Test the user-visible flows (requires backend running):
- Navigate to reader page, verify "All Items" is shown
- Create a category, verify it appears in sidebar
- Add a feed (if possible with a test RSS URL), verify it appears under the category
- Toggle view mode between List and Full, verify UI changes
- Toggle sort order, verify items reorder
- OPML import via file upload dialog
- OPML export triggers download
- Mark category as read, verify unread badges update
- Delete category, verify feeds move to Subscribed

- [ ] **Step 2: Run E2E tests**

Run: `cd client && npm run e2e 2>&1 | tail -30`

Expected: Tests pass (backend must be running).

- [ ] **Step 3: Commit**

```bash
git add client/e2e/reader-categories.spec.ts
git commit -m "test: Playwright E2E tests for feed categories and reader enhancements"
```

---

### Task 30: Run Full Test Suite and Fix Failures

- [ ] **Step 1: Run backend tests**

Run: `./gradlew test -x :client:test 2>&1 | tail -30`

Expected: All tests pass.

- [ ] **Step 2: Run frontend tests**

Run: `cd client && npm run test 2>&1 | tail -30`

Expected: All tests pass (or new tests need to be written for store changes).

- [ ] **Step 3: Build frontend**

Run: `cd client && npm run build 2>&1 | tail -20`

Expected: Build succeeds with no errors.

- [ ] **Step 4: Fix any failures, commit fixes**

```bash
git add -A
git commit -m "fix: resolve test/build failures from Phase 7 integration"
```

---

### Task 31: Update Design Doc and Roadmap

**Files:**
- Modify: `docs/plans/2026-03-16-phase-7-mirror-oldreader-design.md`
- Modify: `docs/plans/2026-03-05-tooling-first-design.md`

- [ ] **Step 1: Mark spec as Implemented**

Change `**Status**: Draft` to `**Status**: Implemented` in the Phase 7 design doc.

- [ ] **Step 2: Update master roadmap**

In `2026-03-05-tooling-first-design.md`, update the Phase 7 description to reflect what was actually built:

```markdown
### Phase 7 — Mirror OldReader Functionality
Feed categories (single-level with "Subscribed" default), OPML import/export, full feed management UI (add/delete/move feeds, create/rename/delete/reorder categories), reader enhancements (list/full view toggle, newest/oldest sort, scroll-mark-as-read, manual read/unread, mark-category-read, "All Items" view), user preferences persisted on User entity. Stubs for starred articles, API keys, and OAuth (tables + commented-out code). See `docs/plans/2026-03-16-phase-7-mirror-oldreader-design.md`.
```

- [ ] **Step 3: Commit**

```bash
git add docs/plans/2026-03-16-phase-7-mirror-oldreader-design.md docs/plans/2026-03-05-tooling-first-design.md
git commit -m "docs: Phase 7 complete — update design doc status and master roadmap"
```
