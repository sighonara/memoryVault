# Phase 1: Bookmarks Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the Bookmarks domain — Tag and Bookmark entities, BookmarkService, and 5 MCP tools — validating the full MemoryVault stack from entity to Claude interaction.

**Architecture:** Flyway migration creates the full data model (all tables from the design doc). Only Bookmark and Tag get Kotlin entity classes in this phase. BookmarkService handles CRUD + export. BookmarkTools exposes 5 `@Tool` methods. A hardcoded seed user (UUID `00000000-0000-0000-0000-000000000001`) is used for all operations — auth comes in Phase 5.

**Tech Stack:** Kotlin 2.x, Spring Boot 4.x, Spring Data JPA, Spring AI `@Tool`, Flyway, PostgreSQL 16, TestContainers, JUnit 5, MockK

---

## Task 1: Flyway Migration — Full Data Model

**Files:**
- Create: `src/main/resources/db/migration/V2__full_schema.sql`

**Step 1: Create the migration**

```sql
-- V2__full_schema.sql
-- Full data model: users, tags, bookmarks, feeds, feed_items, youtube_lists, videos, and all join tables.

-- ============================================================
-- Users
-- ============================================================
CREATE TABLE users (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email        VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    role         VARCHAR(20)  NOT NULL DEFAULT 'OWNER',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    version      BIGINT       NOT NULL DEFAULT 0
);

-- Seed system user for Phase 1 (no auth yet)
INSERT INTO users (id, email, password_hash, display_name, role)
VALUES ('00000000-0000-0000-0000-000000000001', 'system@memoryvault.local', 'nologin', 'System', 'OWNER');

-- ============================================================
-- Tags (shared across bookmarks, feed items, videos)
-- ============================================================
CREATE TABLE tags (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id),
    name       VARCHAR(100) NOT NULL,
    color      VARCHAR(7),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, name)
);

-- ============================================================
-- Bookmarks
-- ============================================================
CREATE TABLE bookmarks (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id),
    url        VARCHAR(2048) NOT NULL,
    title      VARCHAR(500)  NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    version    BIGINT        NOT NULL DEFAULT 0
);

CREATE TABLE bookmark_tags (
    bookmark_id UUID NOT NULL REFERENCES bookmarks(id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (bookmark_id, tag_id)
);

-- ============================================================
-- Feeds
-- ============================================================
CREATE TABLE feeds (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID         NOT NULL REFERENCES users(id),
    url                    VARCHAR(2048) NOT NULL,
    title                  VARCHAR(500),
    description            TEXT,
    site_url               VARCHAR(2048),
    last_fetched_at        TIMESTAMPTZ,
    fetch_interval_minutes INT          NOT NULL DEFAULT 60,
    failure_count          INT          NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ,
    version                BIGINT       NOT NULL DEFAULT 0
);

-- ============================================================
-- Feed Items
-- ============================================================
CREATE TABLE feed_items (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    feed_id      UUID        NOT NULL REFERENCES feeds(id) ON DELETE CASCADE,
    guid         VARCHAR(2048) NOT NULL,
    title        VARCHAR(500),
    url          VARCHAR(2048),
    content      TEXT,
    author       VARCHAR(255),
    image_url    VARCHAR(2048),
    published_at TIMESTAMPTZ,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (feed_id, guid)
);

CREATE TABLE feed_item_tags (
    feed_item_id UUID NOT NULL REFERENCES feed_items(id) ON DELETE CASCADE,
    tag_id       UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (feed_item_id, tag_id)
);

-- ============================================================
-- YouTube Lists
-- ============================================================
CREATE TABLE youtube_lists (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id),
    youtube_list_id VARCHAR(255) NOT NULL,
    url             VARCHAR(2048) NOT NULL,
    name            VARCHAR(500),
    description     TEXT,
    last_synced_at  TIMESTAMPTZ,
    failure_count   INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0
);

-- ============================================================
-- Videos
-- ============================================================
CREATE TABLE videos (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    youtube_list_id       UUID         NOT NULL REFERENCES youtube_lists(id) ON DELETE CASCADE,
    youtube_video_id      VARCHAR(255) NOT NULL,
    title                 VARCHAR(500),
    description           TEXT,
    channel_name          VARCHAR(255),
    thumbnail_path        VARCHAR(1024),
    youtube_url           VARCHAR(2048) NOT NULL,
    file_path             VARCHAR(1024),
    downloaded_at         TIMESTAMPTZ,
    duration_seconds      INT,
    removed_from_youtube  BOOLEAN      NOT NULL DEFAULT false,
    removed_detected_at   TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE video_tags (
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    tag_id   UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (video_id, tag_id)
);

-- ============================================================
-- Indexes
-- ============================================================
CREATE INDEX idx_bookmarks_user_id ON bookmarks(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_tags_user_id ON tags(user_id);
CREATE INDEX idx_feeds_user_id ON feeds(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_feed_items_feed_id ON feed_items(feed_id);
CREATE INDEX idx_videos_youtube_list_id ON videos(youtube_list_id);
```

**Step 2: Verify migration runs**

```bash
./gradlew test --tests "*MemoryVaultApplicationTests"
```

Expected: PASS — TestContainers starts PostgreSQL, Flyway runs V1 + V2, context loads, Hibernate validates the schema.

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V2__full_schema.sql
git commit -m "feat: add V2 migration with full data model and seed user"
```

---

## Task 2: Tag Entity, Repository, and Service

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/tag/entity/Tag.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/tag/repository/TagRepository.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/tag/service/TagServiceTest.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/tag/service/TagService.kt`

**Step 1: Create the Tag entity**

```kotlin
package org.sightech.memoryvault.tag.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tags")
class Tag(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(length = 7)
    val color: String? = null,

    val createdAt: Instant = Instant.now()
)
```

**Step 2: Create the TagRepository**

```kotlin
package org.sightech.memoryvault.tag.repository

import org.sightech.memoryvault.tag.entity.Tag
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TagRepository : JpaRepository<Tag, UUID> {

    fun findByUserIdAndNameIn(userId: UUID, names: List<String>): List<Tag>

    fun findByUserIdAndName(userId: UUID, name: String): Tag?
}
```

**Step 3: Write the failing TagService test**

```kotlin
package org.sightech.memoryvault.tag.service

import io.mockk.*
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.tag.entity.Tag
import org.sightech.memoryvault.tag.repository.TagRepository
import java.util.UUID
import kotlin.test.assertEquals

class TagServiceTest {

    private val repository = mockk<TagRepository>()
    private val service = TagService(repository)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `findOrCreateByName returns existing tag`() {
        val existing = Tag(userId = userId, name = "kotlin")
        every { repository.findByUserIdAndName(userId, "kotlin") } returns existing

        val result = service.findOrCreateByName("kotlin")

        assertEquals(existing, result)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `findOrCreateByName creates new tag when not found`() {
        every { repository.findByUserIdAndName(userId, "new-tag") } returns null
        every { repository.save(any()) } answers { firstArg() }

        val result = service.findOrCreateByName("new-tag")

        assertEquals("new-tag", result.name)
        assertEquals(userId, result.userId)
        verify { repository.save(any()) }
    }

    @Test
    fun `findOrCreateByNames returns mix of existing and new tags`() {
        val existing = Tag(userId = userId, name = "kotlin")
        every { repository.findByUserIdAndNameIn(userId, listOf("kotlin", "spring")) } returns listOf(existing)
        every { repository.save(any()) } answers { firstArg() }

        val result = service.findOrCreateByNames(listOf("kotlin", "spring"))

        assertEquals(2, result.size)
        verify(exactly = 1) { repository.save(any()) }
    }
}
```

**Step 4: Run test to verify it fails**

```bash
./gradlew test --tests "*TagServiceTest"
```

Expected: FAIL — `TagService` does not exist.

**Step 5: Create TagService**

```kotlin
package org.sightech.memoryvault.tag.service

import org.sightech.memoryvault.tag.entity.Tag
import org.sightech.memoryvault.tag.repository.TagRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class TagService(private val repository: TagRepository) {

    companion object {
        val SYSTEM_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    fun findOrCreateByName(name: String): Tag {
        return repository.findByUserIdAndName(SYSTEM_USER_ID, name)
            ?: repository.save(Tag(userId = SYSTEM_USER_ID, name = name))
    }

    fun findOrCreateByNames(names: List<String>): List<Tag> {
        val existing = repository.findByUserIdAndNameIn(SYSTEM_USER_ID, names)
        val existingNames = existing.map { it.name }.toSet()
        val newTags = names.filter { it !in existingNames }
            .map { repository.save(Tag(userId = SYSTEM_USER_ID, name = it)) }
        return existing + newTags
    }
}
```

**Step 6: Run tests to verify they pass**

```bash
./gradlew test --tests "*TagServiceTest"
```

Expected: PASS.

**Step 7: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/tag/ src/test/kotlin/org/sightech/memoryvault/tag/
git commit -m "feat: add Tag entity, repository, and service with tests"
```

---

## Task 3: Bookmark Entity and Repository

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/bookmark/entity/Bookmark.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/bookmark/repository/BookmarkRepository.kt`

**Step 1: Create the Bookmark entity**

```kotlin
package org.sightech.memoryvault.bookmark.entity

import jakarta.persistence.*
import org.sightech.memoryvault.tag.entity.Tag
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "bookmarks")
class Bookmark(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false, length = 2048)
    val url: String,

    @Column(nullable = false, length = 500)
    var title: String,

    val createdAt: Instant = Instant.now(),

    var updatedAt: Instant = Instant.now(),

    var deletedAt: Instant? = null,

    @Version
    val version: Long = 0,

    @ManyToMany
    @JoinTable(
        name = "bookmark_tags",
        joinColumns = [JoinColumn(name = "bookmark_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")]
    )
    val tags: MutableSet<Tag> = mutableSetOf()
)
```

**Step 2: Create the BookmarkRepository**

```kotlin
package org.sightech.memoryvault.bookmark.repository

import org.sightech.memoryvault.bookmark.entity.Bookmark
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BookmarkRepository : JpaRepository<Bookmark, UUID> {

    @Query("SELECT b FROM Bookmark b LEFT JOIN FETCH b.tags WHERE b.deletedAt IS NULL AND b.userId = :userId ORDER BY b.createdAt DESC")
    fun findAllActiveByUserId(userId: UUID): List<Bookmark>

    @Query("SELECT b FROM Bookmark b LEFT JOIN FETCH b.tags WHERE b.id = :id AND b.deletedAt IS NULL")
    fun findActiveById(id: UUID): Bookmark?
}
```

**Step 3: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

**Step 4: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/bookmark/
git commit -m "feat: add Bookmark entity and repository"
```

---

## Task 4: BookmarkService with Tests

**Files:**
- Create: `src/test/kotlin/org/sightech/memoryvault/bookmark/service/BookmarkServiceTest.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/bookmark/service/BookmarkService.kt`

**Step 1: Write the failing BookmarkService test**

```kotlin
package org.sightech.memoryvault.bookmark.service

import io.mockk.*
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.bookmark.entity.Bookmark
import org.sightech.memoryvault.bookmark.repository.BookmarkRepository
import org.sightech.memoryvault.tag.entity.Tag
import org.sightech.memoryvault.tag.service.TagService
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BookmarkServiceTest {

    private val bookmarkRepository = mockk<BookmarkRepository>()
    private val tagService = mockk<TagService>()
    private val service = BookmarkService(bookmarkRepository, tagService)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `create saves bookmark without tags`() {
        every { bookmarkRepository.save(any()) } answers { firstArg() }

        val result = service.create("https://example.com", "Example", null)

        assertNotNull(result)
        assertEquals("https://example.com", result.url)
        assertEquals("Example", result.title)
        verify { bookmarkRepository.save(any()) }
    }

    @Test
    fun `create saves bookmark with tags`() {
        val tags = listOf(Tag(userId = userId, name = "kotlin"), Tag(userId = userId, name = "spring"))
        every { tagService.findOrCreateByNames(listOf("kotlin", "spring")) } returns tags
        every { bookmarkRepository.save(any()) } answers { firstArg() }

        val result = service.create("https://example.com", "Example", listOf("kotlin", "spring"))

        assertEquals(2, result.tags.size)
        verify { tagService.findOrCreateByNames(listOf("kotlin", "spring")) }
    }

    @Test
    fun `create uses URL as title when title is null`() {
        every { bookmarkRepository.save(any()) } answers { firstArg() }

        val result = service.create("https://example.com", null, null)

        assertEquals("https://example.com", result.title)
    }

    @Test
    fun `findAll returns active bookmarks`() {
        val bookmarks = listOf(Bookmark(userId = userId, url = "https://a.com", title = "A"))
        every { bookmarkRepository.findAllActiveByUserId(userId) } returns bookmarks

        val result = service.findAll(null, null)

        assertEquals(1, result.size)
    }

    @Test
    fun `findAll filters by query`() {
        val bookmarks = listOf(
            Bookmark(userId = userId, url = "https://kotlin.dev", title = "Kotlin"),
            Bookmark(userId = userId, url = "https://spring.io", title = "Spring")
        )
        every { bookmarkRepository.findAllActiveByUserId(userId) } returns bookmarks

        val result = service.findAll("kotlin", null)

        assertEquals(1, result.size)
        assertEquals("Kotlin", result[0].title)
    }

    @Test
    fun `findAll filters by tags`() {
        val kotlinTag = Tag(userId = userId, name = "kotlin")
        val b1 = Bookmark(userId = userId, url = "https://a.com", title = "A").apply { tags.add(kotlinTag) }
        val b2 = Bookmark(userId = userId, url = "https://b.com", title = "B")
        every { bookmarkRepository.findAllActiveByUserId(userId) } returns listOf(b1, b2)

        val result = service.findAll(null, listOf("kotlin"))

        assertEquals(1, result.size)
        assertEquals("A", result[0].title)
    }

    @Test
    fun `updateTags replaces tags on bookmark`() {
        val bookmark = Bookmark(userId = userId, url = "https://a.com", title = "A")
        val newTags = listOf(Tag(userId = userId, name = "new-tag"))
        every { bookmarkRepository.findActiveById(bookmark.id) } returns bookmark
        every { tagService.findOrCreateByNames(listOf("new-tag")) } returns newTags
        every { bookmarkRepository.save(any()) } answers { firstArg() }

        val result = service.updateTags(bookmark.id, listOf("new-tag"))

        assertNotNull(result)
        assertEquals(1, result.tags.size)
    }

    @Test
    fun `updateTags returns null for nonexistent bookmark`() {
        val id = UUID.randomUUID()
        every { bookmarkRepository.findActiveById(id) } returns null

        val result = service.updateTags(id, listOf("tag"))

        assertNull(result)
    }

    @Test
    fun `softDelete sets deletedAt`() {
        val bookmark = Bookmark(userId = userId, url = "https://a.com", title = "A")
        every { bookmarkRepository.findActiveById(bookmark.id) } returns bookmark
        every { bookmarkRepository.save(any()) } answers { firstArg() }

        val result = service.softDelete(bookmark.id)

        assertNotNull(result)
        assertNotNull(result.deletedAt)
    }

    @Test
    fun `softDelete returns null for nonexistent bookmark`() {
        val id = UUID.randomUUID()
        every { bookmarkRepository.findActiveById(id) } returns null

        val result = service.softDelete(id)

        assertNull(result)
    }

    @Test
    fun `exportNetscapeHtml produces valid Netscape format`() {
        val bookmark = Bookmark(userId = userId, url = "https://example.com", title = "Example")
        every { bookmarkRepository.findAllActiveByUserId(userId) } returns listOf(bookmark)

        val result = service.exportNetscapeHtml()

        assert(result.contains("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
        assert(result.contains("HREF=\"https://example.com\""))
        assert(result.contains("Example"))
    }
}
```

**Step 2: Run test to verify it fails**

```bash
./gradlew test --tests "*BookmarkServiceTest"
```

Expected: FAIL — `BookmarkService` does not exist.

**Step 3: Create BookmarkService**

```kotlin
package org.sightech.memoryvault.bookmark.service

import org.sightech.memoryvault.bookmark.entity.Bookmark
import org.sightech.memoryvault.bookmark.repository.BookmarkRepository
import org.sightech.memoryvault.tag.service.TagService
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class BookmarkService(
    private val bookmarkRepository: BookmarkRepository,
    private val tagService: TagService
) {

    companion object {
        val SYSTEM_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    fun create(url: String, title: String?, tagNames: List<String>?): Bookmark {
        val bookmark = Bookmark(
            userId = SYSTEM_USER_ID,
            url = url,
            title = title ?: url
        )
        if (!tagNames.isNullOrEmpty()) {
            val tags = tagService.findOrCreateByNames(tagNames)
            bookmark.tags.addAll(tags)
        }
        return bookmarkRepository.save(bookmark)
    }

    fun findAll(query: String?, tagNames: List<String>?): List<Bookmark> {
        var bookmarks = bookmarkRepository.findAllActiveByUserId(SYSTEM_USER_ID)

        if (!query.isNullOrBlank()) {
            val q = query.lowercase()
            bookmarks = bookmarks.filter {
                it.title.lowercase().contains(q) || it.url.lowercase().contains(q)
            }
        }

        if (!tagNames.isNullOrEmpty()) {
            bookmarks = bookmarks.filter { bookmark ->
                val bookmarkTagNames = bookmark.tags.map { it.name }.toSet()
                tagNames.any { it in bookmarkTagNames }
            }
        }

        return bookmarks
    }

    fun updateTags(bookmarkId: UUID, tagNames: List<String>): Bookmark? {
        val bookmark = bookmarkRepository.findActiveById(bookmarkId) ?: return null
        val tags = tagService.findOrCreateByNames(tagNames)
        bookmark.tags.clear()
        bookmark.tags.addAll(tags)
        bookmark.updatedAt = Instant.now()
        return bookmarkRepository.save(bookmark)
    }

    fun softDelete(bookmarkId: UUID): Bookmark? {
        val bookmark = bookmarkRepository.findActiveById(bookmarkId) ?: return null
        bookmark.deletedAt = Instant.now()
        bookmark.updatedAt = Instant.now()
        return bookmarkRepository.save(bookmark)
    }

    fun exportNetscapeHtml(): String {
        val bookmarks = bookmarkRepository.findAllActiveByUserId(SYSTEM_USER_ID)
        val sb = StringBuilder()
        sb.appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
        sb.appendLine("<!-- This is an automatically generated file. -->")
        sb.appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
        sb.appendLine("<TITLE>Bookmarks</TITLE>")
        sb.appendLine("<H1>Bookmarks</H1>")
        sb.appendLine("<DL><p>")
        for (bookmark in bookmarks) {
            val addDate = bookmark.createdAt.epochSecond
            val tagAttr = if (bookmark.tags.isNotEmpty()) {
                " TAGS=\"${bookmark.tags.joinToString(",") { it.name }}\""
            } else ""
            sb.appendLine("    <DT><A HREF=\"${bookmark.url}\" ADD_DATE=\"$addDate\"$tagAttr>${bookmark.title}</A>")
        }
        sb.appendLine("</DL><p>")
        return sb.toString()
    }
}
```

**Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "*BookmarkServiceTest"
```

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/bookmark/service/ src/test/kotlin/org/sightech/memoryvault/bookmark/service/
git commit -m "feat: add BookmarkService with create, findAll, updateTags, softDelete, export"
```

---

## Task 5: BookmarkTools MCP Class with Tests

**Files:**
- Create: `src/test/kotlin/org/sightech/memoryvault/mcp/BookmarkToolsTest.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/mcp/BookmarkTools.kt`

**Step 1: Write the failing BookmarkTools test**

```kotlin
package org.sightech.memoryvault.mcp

import io.mockk.*
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.bookmark.entity.Bookmark
import org.sightech.memoryvault.bookmark.service.BookmarkService
import org.sightech.memoryvault.tag.entity.Tag
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals

class BookmarkToolsTest {

    private val bookmarkService = mockk<BookmarkService>()
    private val tools = BookmarkTools(bookmarkService)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `addBookmark returns confirmation with title`() {
        val bookmark = Bookmark(userId = userId, url = "https://example.com", title = "Example")
        every { bookmarkService.create("https://example.com", "Example", null) } returns bookmark

        val result = tools.addBookmark("https://example.com", "Example", null)

        assertContains(result, "Example")
        assertContains(result, "https://example.com")
    }

    @Test
    fun `addBookmark with tags includes tag names`() {
        val tag = Tag(userId = userId, name = "dev")
        val bookmark = Bookmark(userId = userId, url = "https://example.com", title = "Example").apply { tags.add(tag) }
        every { bookmarkService.create("https://example.com", "Example", listOf("dev")) } returns bookmark

        val result = tools.addBookmark("https://example.com", "Example", listOf("dev"))

        assertContains(result, "dev")
    }

    @Test
    fun `listBookmarks returns formatted list`() {
        val b1 = Bookmark(userId = userId, url = "https://a.com", title = "A")
        val b2 = Bookmark(userId = userId, url = "https://b.com", title = "B")
        every { bookmarkService.findAll(null, null) } returns listOf(b1, b2)

        val result = tools.listBookmarks(null, null)

        assertContains(result, "A")
        assertContains(result, "B")
        assertContains(result, "2 bookmark")
    }

    @Test
    fun `listBookmarks returns message when empty`() {
        every { bookmarkService.findAll(null, null) } returns emptyList()

        val result = tools.listBookmarks(null, null)

        assertContains(result, "No bookmarks found")
    }

    @Test
    fun `tagBookmark returns updated bookmark info`() {
        val tag = Tag(userId = userId, name = "kotlin")
        val bookmark = Bookmark(userId = userId, url = "https://a.com", title = "A").apply { tags.add(tag) }
        every { bookmarkService.updateTags(bookmark.id, listOf("kotlin")) } returns bookmark

        val result = tools.tagBookmark(bookmark.id.toString(), listOf("kotlin"))

        assertContains(result, "kotlin")
        assertContains(result, "A")
    }

    @Test
    fun `tagBookmark returns not found message`() {
        val id = UUID.randomUUID()
        every { bookmarkService.updateTags(id, listOf("tag")) } returns null

        val result = tools.tagBookmark(id.toString(), listOf("tag"))

        assertContains(result, "not found")
    }

    @Test
    fun `deleteBookmark returns confirmation`() {
        val bookmark = Bookmark(userId = userId, url = "https://a.com", title = "A")
        every { bookmarkService.softDelete(bookmark.id) } returns bookmark

        val result = tools.deleteBookmark(bookmark.id.toString())

        assertContains(result, "Deleted")
        assertContains(result, "A")
    }

    @Test
    fun `deleteBookmark returns not found message`() {
        val id = UUID.randomUUID()
        every { bookmarkService.softDelete(id) } returns null

        val result = tools.deleteBookmark(id.toString())

        assertContains(result, "not found")
    }

    @Test
    fun `exportBookmarks returns HTML content`() {
        every { bookmarkService.exportNetscapeHtml() } returns "<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<H1>Bookmarks</H1>"

        val result = tools.exportBookmarks(null)

        assertContains(result, "NETSCAPE")
    }
}
```

**Step 2: Run test to verify it fails**

```bash
./gradlew test --tests "*BookmarkToolsTest"
```

Expected: FAIL — `BookmarkTools` does not exist.

**Step 3: Create BookmarkTools**

```kotlin
package org.sightech.memoryvault.mcp

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Component
import org.sightech.memoryvault.bookmark.service.BookmarkService
import java.util.UUID

@Component
class BookmarkTools(private val bookmarkService: BookmarkService) {

    @Tool(description = "Save a URL as a bookmark. Use when the user wants to save, bookmark, or remember a web page. Optionally provide a title and tags.")
    fun addBookmark(url: String, title: String?, tags: List<String>?): String {
        val bookmark = bookmarkService.create(url, title, tags)
        val tagStr = if (bookmark.tags.isNotEmpty()) " [${bookmark.tags.joinToString(", ") { it.name }}]" else ""
        return "Saved bookmark: \"${bookmark.title}\" (${bookmark.url})$tagStr — id: ${bookmark.id}"
    }

    @Tool(description = "List and search bookmarks. Use when the user wants to see their bookmarks, search by text, or filter by tags. Both query and tags are optional filters.")
    fun listBookmarks(query: String?, tags: List<String>?): String {
        val bookmarks = bookmarkService.findAll(query, tags)
        if (bookmarks.isEmpty()) return "No bookmarks found."

        val lines = bookmarks.map { b ->
            val tagStr = if (b.tags.isNotEmpty()) " [${b.tags.joinToString(", ") { it.name }}]" else ""
            "- ${b.title} (${b.url})$tagStr — id: ${b.id}"
        }
        return "${bookmarks.size} bookmark(s):\n${lines.joinToString("\n")}"
    }

    @Tool(description = "Update the tags on a bookmark. Use when the user wants to tag, retag, categorize, or label a bookmark. Replaces all existing tags.")
    fun tagBookmark(bookmarkId: String, tags: List<String>): String {
        val bookmark = bookmarkService.updateTags(UUID.fromString(bookmarkId), tags)
            ?: return "Bookmark not found."
        val tagStr = bookmark.tags.joinToString(", ") { it.name }
        return "Updated tags on \"${bookmark.title}\": [$tagStr]"
    }

    @Tool(description = "Delete a bookmark. Use when the user wants to remove or delete a saved bookmark. This is a soft delete — the bookmark can be recovered.")
    fun deleteBookmark(bookmarkId: String): String {
        val bookmark = bookmarkService.softDelete(UUID.fromString(bookmarkId))
            ?: return "Bookmark not found."
        return "Deleted bookmark: \"${bookmark.title}\" (${bookmark.url})"
    }

    @Tool(description = "Export all bookmarks as a Netscape HTML file. Use when the user wants to export their bookmarks for import into a web browser.")
    fun exportBookmarks(format: String?): String {
        return bookmarkService.exportNetscapeHtml()
    }
}
```

**Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "*BookmarkToolsTest"
```

Expected: PASS.

**Step 5: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/mcp/BookmarkTools.kt src/test/kotlin/org/sightech/memoryvault/mcp/BookmarkToolsTest.kt
git commit -m "feat: add BookmarkTools with 5 MCP tools"
```

---

## Task 6: Bookmark REST Controller Stub

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/bookmark/controller/BookmarkController.kt`

**Step 1: Create the controller**

```kotlin
package org.sightech.memoryvault.bookmark.controller

import org.sightech.memoryvault.bookmark.entity.Bookmark
import org.sightech.memoryvault.bookmark.service.BookmarkService
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/bookmarks")
class BookmarkController(private val service: BookmarkService) {

    @GetMapping
    fun findAll(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) tags: List<String>?
    ): List<Bookmark> = service.findAll(query, tags)
}
```

**Step 2: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/bookmark/controller/
git commit -m "feat: add BookmarkController stub with list endpoint"
```

---

## Task 7: Integration Test

**Files:**
- Create: `src/test/kotlin/org/sightech/memoryvault/bookmark/BookmarkIntegrationTest.kt`

This test verifies the full round trip: service → JPA → PostgreSQL (via TestContainers) → Flyway migrations.

**Step 1: Write the integration test**

```kotlin
package org.sightech.memoryvault.bookmark

import org.junit.jupiter.api.Test
import org.sightech.memoryvault.bookmark.service.BookmarkService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class BookmarkIntegrationTest {

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

    @Autowired
    lateinit var bookmarkService: BookmarkService

    @Test
    fun `create and retrieve bookmark`() {
        val bookmark = bookmarkService.create("https://example.com", "Example Site", null)
        assertNotNull(bookmark.id)
        assertEquals("Example Site", bookmark.title)

        val found = bookmarkService.findAll(null, null)
        assert(found.any { it.id == bookmark.id })
    }

    @Test
    fun `create bookmark with tags`() {
        val bookmark = bookmarkService.create("https://kotlin.dev", "Kotlin", listOf("lang", "jvm"))
        assertEquals(2, bookmark.tags.size)

        val found = bookmarkService.findAll(null, listOf("lang"))
        assert(found.any { it.id == bookmark.id })
    }

    @Test
    fun `update tags on bookmark`() {
        val bookmark = bookmarkService.create("https://spring.io", "Spring", listOf("java"))
        val updated = bookmarkService.updateTags(bookmark.id, listOf("kotlin", "framework"))

        assertNotNull(updated)
        assertEquals(2, updated.tags.size)
        assert(updated.tags.any { it.name == "kotlin" })
        assert(updated.tags.none { it.name == "java" })
    }

    @Test
    fun `soft delete bookmark`() {
        val bookmark = bookmarkService.create("https://delete-me.com", "Delete Me", null)
        val deleted = bookmarkService.softDelete(bookmark.id)

        assertNotNull(deleted)
        assertNotNull(deleted.deletedAt)

        val found = bookmarkService.findAll(null, null)
        assert(found.none { it.id == bookmark.id })
    }

    @Test
    fun `export bookmarks as Netscape HTML`() {
        bookmarkService.create("https://export-test.com", "Export Test", listOf("test"))
        val html = bookmarkService.exportNetscapeHtml()

        assert(html.contains("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
        assert(html.contains("https://export-test.com"))
        assert(html.contains("Export Test"))
        assert(html.contains("TAGS=\"test\""))
    }

    @Test
    fun `tags are reused across bookmarks`() {
        bookmarkService.create("https://a.com", "A", listOf("shared-tag"))
        bookmarkService.create("https://b.com", "B", listOf("shared-tag"))

        val results = bookmarkService.findAll(null, listOf("shared-tag"))
        assert(results.size >= 2)
    }
}
```

**Step 2: Run the integration test**

```bash
./gradlew test --tests "*BookmarkIntegrationTest"
```

Expected: PASS — TestContainers starts PostgreSQL, Flyway runs migrations, service operations work end-to-end.

**Step 3: Run the full test suite**

```bash
./gradlew test
```

Expected: All tests pass (SystemToolsTest, TagServiceTest, BookmarkServiceTest, BookmarkToolsTest, BookmarkIntegrationTest, MemoryVaultApplicationTests).

**Step 4: Commit**

```bash
git add src/test/kotlin/org/sightech/memoryvault/bookmark/BookmarkIntegrationTest.kt
git commit -m "feat: add BookmarkIntegrationTest with full round-trip verification"
```

---

## Task 8: Test Script

**Files:**
- Create: `scripts/test-bookmarks.sh`

**Step 1: Create the script**

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "=== MemoryVault: Bookmark tests ==="
echo ""

echo "--- Unit tests ---"
./gradlew test --tests "*TagServiceTest" --tests "*BookmarkServiceTest" --tests "*BookmarkToolsTest"

echo ""
echo "--- Integration tests ---"
./gradlew test --tests "*BookmarkIntegrationTest"

echo ""
echo "=== All bookmark tests passed ==="
```

**Step 2: Make executable**

```bash
chmod +x scripts/test-bookmarks.sh
```

**Step 3: Run it**

```bash
./scripts/test-bookmarks.sh
```

Expected: All bookmark tests pass.

**Step 4: Commit**

```bash
git add scripts/test-bookmarks.sh
git commit -m "feat: add test-bookmarks.sh script"
```

---

## Phase 1 Complete

At the end of Phase 1 you have:

- Full database schema (all tables from design doc) via V2 migration
- Tag entity + repository + service (idempotent find-or-create)
- Bookmark entity + repository + service (CRUD, filtering, Netscape HTML export)
- BookmarkTools with 5 MCP tools: `addBookmark`, `listBookmarks`, `tagBookmark`, `deleteBookmark`, `exportBookmarks`
- BookmarkController stub (list endpoint)
- Integration test proving the full stack works with real PostgreSQL
- `scripts/test-bookmarks.sh`

**Next:** Phase 2 — RSS / Feeds. Use `/scaffold-entity` for Feed + FeedItem entities (tables already exist), then `/add-mcp-tool` for feed tools.
