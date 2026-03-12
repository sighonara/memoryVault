# Phase 6: Bookmark Management — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the bookmark system from a flat list to a full bookmark manager with folder hierarchy, browser bookmark ingestion, conflict resolution, and Netscape HTML export.

**Architecture:** Adjacency-list folder tree (parentId on Folder entity). IngestService handles browser format parsing + diff/preview. CLI commands generated client-side in Angular. REST endpoints for ingest flow (CLI → server → Angular conflict review). GraphQL for all other bookmark/folder operations.

**Tech Stack:** Kotlin/Spring Boot 4.x, Spring for GraphQL, PostgreSQL (Flyway V5), Angular 21 (zoneless, NgRx Signal Store, Angular Material), Vitest, JUnit 5/MockK/TestContainers, Playwright.

**Spec:** `docs/superpowers/specs/2026-03-11-phase-6-bookmark-management-design.md`

**Conventions:** See `CLAUDE.md`. Key reminders:
- UUIDs for all PKs, soft deletes via `deletedAt`, optimistic locking via `version`
- `CurrentUser.userId()` for multi-tenancy in all service methods
- Angular: standalone components, OnPush, `inject()`, `@if`/`@for`, no `subscribe()` in components
- Git commits: `git commit -m "message"` with plain string, never heredoc/cat/subshell

---

## Chunk 1: Database, Entities, and Core Services

### Task 1: Flyway V5 Migration

**Files:**
- Create: `src/main/resources/db/migration/V5__folders_and_ingest.sql`

- [x] **Step 1: Write the migration SQL**

```sql
-- Folders table
CREATE TABLE folders (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    parent_id   UUID REFERENCES folders(id),
    user_id     UUID NOT NULL REFERENCES users(id),
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_folders_user_id ON folders(user_id);
CREATE INDEX idx_folders_parent_id ON folders(parent_id);

-- Full-text search on folders
ALTER TABLE folders ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (to_tsvector('english', coalesce(name, ''))) STORED;
CREATE INDEX idx_folders_search ON folders USING GIN(search_vector);

-- Bookmark changes
ALTER TABLE bookmarks ADD COLUMN folder_id UUID REFERENCES folders(id);
ALTER TABLE bookmarks ADD COLUMN sort_order INT NOT NULL DEFAULT 0;
ALTER TABLE bookmarks ADD COLUMN normalized_url VARCHAR(2048);

CREATE INDEX idx_bookmarks_folder_id ON bookmarks(folder_id);
CREATE INDEX idx_bookmarks_normalized_url ON bookmarks(normalized_url);

-- Backfill normalized_url for existing bookmarks
-- Note: This is an approximate backfill (lowercase + strip trailing slash only).
-- Full normalization (www. stripping, query param sorting) happens in IngestService.normalizeUrl().
-- A one-time Kotlin migration script should be run after deployment to fully normalize existing URLs.
UPDATE bookmarks SET normalized_url = lower(
    regexp_replace(
        regexp_replace(url, '^(https?://)www\.', '\1'),
        '/$', ''
    )
) WHERE normalized_url IS NULL;

-- Ingest previews table (stores preview state between CLI POST and UI commit)
CREATE TABLE ingest_previews (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id),
    preview_data JSONB NOT NULL,
    committed   BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '1 hour')
);

CREATE INDEX idx_ingest_previews_user_id ON ingest_previews(user_id);
```

- [x] **Step 2: Verify migration applies cleanly**

Run: `./gradlew test --tests "*MemoryVaultApplicationTests*" -q`
Expected: Application context loads, Flyway applies V5

- [x] **Step 3: Commit**

```
git add src/main/resources/db/migration/V5__folders_and_ingest.sql
git commit -m "feat: V5 migration — folders table, bookmark columns, ingest previews"
```

---

### Task 2: Folder Entity and Repository

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/bookmark/entity/Folder.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/bookmark/repository/FolderRepository.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/bookmark/entity/Bookmark.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/bookmark/repository/BookmarkRepository.kt`

- [x] **Step 1: Write Folder entity test**

Create: `src/test/kotlin/org/sightech/memoryvault/bookmark/entity/FolderEntityTest.kt`

```kotlin
package org.sightech.memoryvault.bookmark.entity

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID

class FolderEntityTest {

    @Test
    fun `folder can be created with required fields`() {
        val userId = UUID.randomUUID()
        val folder = Folder(name = "Tech", userId = userId)

        assertEquals("Tech", folder.name)
        assertEquals(userId, folder.userId)
        assertNull(folder.parentId)
        assertEquals(0, folder.sortOrder)
        assertNull(folder.deletedAt)
    }

    @Test
    fun `folder can have a parent`() {
        val userId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val folder = Folder(name = "Frontend", userId = userId, parentId = parentId)

        assertEquals(parentId, folder.parentId)
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*FolderEntityTest*" -q`
Expected: FAIL — `Folder` class does not exist

- [x] **Step 3: Create Folder entity**

```kotlin
package org.sightech.memoryvault.bookmark.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "folders")
class Folder(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(name = "parent_id")
    var parentId: UUID? = null,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

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
)
```

- [x] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "*FolderEntityTest*" -q`
Expected: PASS

- [x] **Step 5: Create FolderRepository**

```kotlin
package org.sightech.memoryvault.bookmark.repository

import org.sightech.memoryvault.bookmark.entity.Folder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface FolderRepository : JpaRepository<Folder, UUID> {

    @Query("SELECT f FROM Folder f WHERE f.userId = :userId AND f.deletedAt IS NULL ORDER BY f.sortOrder")
    fun findAllActiveByUserId(userId: UUID): List<Folder>

    @Query("SELECT f FROM Folder f WHERE f.id = :id AND f.userId = :userId AND f.deletedAt IS NULL")
    fun findActiveByIdAndUserId(id: UUID, userId: UUID): Folder?

    @Query("SELECT f FROM Folder f WHERE f.parentId = :parentId AND f.userId = :userId AND f.deletedAt IS NULL ORDER BY f.sortOrder")
    fun findChildrenByParentId(parentId: UUID, userId: UUID): List<Folder>

    fun countByUserIdAndDeletedAtIsNull(userId: UUID): Long
}
```

- [x] **Step 6: Update Bookmark entity — add folderId, sortOrder, normalizedUrl**

Modify `src/main/kotlin/org/sightech/memoryvault/bookmark/entity/Bookmark.kt`:

Add these fields to the Bookmark class:

```kotlin
    @Column(name = "folder_id")
    var folderId: UUID? = null,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "normalized_url", length = 2048)
    var normalizedUrl: String? = null,
```

- [x] **Step 7: Update BookmarkRepository — add folder-aware queries**

Add to `BookmarkRepository.kt`:

```kotlin
    @Query("SELECT b FROM Bookmark b LEFT JOIN FETCH b.tags WHERE b.folderId = :folderId AND b.userId = :userId AND b.deletedAt IS NULL ORDER BY b.sortOrder")
    fun findByFolderIdAndUserId(folderId: UUID, userId: UUID): List<Bookmark>

    @Query("SELECT b FROM Bookmark b LEFT JOIN FETCH b.tags WHERE b.folderId IS NULL AND b.userId = :userId AND b.deletedAt IS NULL ORDER BY b.sortOrder")
    fun findUnfiledByUserId(userId: UUID): List<Bookmark>

    @Query("SELECT b FROM Bookmark b WHERE b.normalizedUrl = :normalizedUrl AND b.userId = :userId")
    fun findByNormalizedUrlAndUserId(normalizedUrl: String, userId: UUID): Bookmark?
```

- [x] **Step 8: Run all tests to verify nothing is broken**

Run: `./gradlew test -q`
Expected: All existing tests pass

- [ ] **Step 9: Commit**

```
git add src/main/kotlin/org/sightech/memoryvault/bookmark/entity/Folder.kt src/main/kotlin/org/sightech/memoryvault/bookmark/repository/FolderRepository.kt src/main/kotlin/org/sightech/memoryvault/bookmark/entity/Bookmark.kt src/main/kotlin/org/sightech/memoryvault/bookmark/repository/BookmarkRepository.kt src/test/kotlin/org/sightech/memoryvault/bookmark/entity/FolderEntityTest.kt
git commit -m "feat: Folder entity, FolderRepository, Bookmark folder/sort/normalized fields"
```

---

### Task 3: BookmarkService — Folder CRUD and Cycle Detection

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/bookmark/service/BookmarkService.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/bookmark/service/FolderOperationsTest.kt`

- [x] **Step 1: Write failing tests for folder CRUD**

```kotlin
package org.sightech.memoryvault.bookmark.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.sightech.memoryvault.bookmark.entity.Folder
import org.sightech.memoryvault.bookmark.repository.BookmarkRepository
import org.sightech.memoryvault.bookmark.repository.FolderRepository
import org.sightech.memoryvault.tag.service.TagService
import org.sightech.memoryvault.auth.CurrentUser
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.util.UUID

class FolderOperationsTest {

    private val bookmarkRepository = mockk<BookmarkRepository>(relaxed = true)
    private val folderRepository = mockk<FolderRepository>(relaxed = true)
    private val tagService = mockk<TagService>(relaxed = true)
    private lateinit var service: BookmarkService
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val auth = UsernamePasswordAuthenticationToken(userId.toString(), null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
        service = BookmarkService(bookmarkRepository, folderRepository, tagService)
    }

    @Test
    fun `createFolder creates a root folder`() {
        val folder = Folder(name = "Tech", userId = userId)
        every { folderRepository.save(any()) } returns folder

        val result = service.createFolder("Tech", null)

        assertEquals("Tech", result.name)
        assertNull(result.parentId)
        verify { folderRepository.save(match { it.name == "Tech" && it.parentId == null }) }
    }

    @Test
    fun `createFolder creates a child folder`() {
        val parentId = UUID.randomUUID()
        val parent = Folder(id = parentId, name = "Dev", userId = userId)
        every { folderRepository.findActiveByIdAndUserId(parentId, userId) } returns parent
        every { folderRepository.save(any()) } answers { firstArg() }

        val result = service.createFolder("Frontend", parentId)

        assertEquals("Frontend", result.name)
        assertEquals(parentId, result.parentId)
    }

    @Test
    fun `createFolder throws when parent not found`() {
        val parentId = UUID.randomUUID()
        every { folderRepository.findActiveByIdAndUserId(parentId, userId) } returns null

        assertThrows<IllegalArgumentException> {
            service.createFolder("Orphan", parentId)
        }
    }

    @Test
    fun `renameFolder updates name`() {
        val folderId = UUID.randomUUID()
        val folder = Folder(id = folderId, name = "Old", userId = userId)
        every { folderRepository.findActiveByIdAndUserId(folderId, userId) } returns folder
        every { folderRepository.save(any()) } answers { firstArg() }

        val result = service.renameFolder(folderId, "New")

        assertEquals("New", result.name)
    }

    @Test
    fun `moveFolder updates parentId`() {
        val folderId = UUID.randomUUID()
        val newParentId = UUID.randomUUID()
        val folder = Folder(id = folderId, name = "Mobile", userId = userId)
        val newParent = Folder(id = newParentId, name = "Dev", userId = userId)

        every { folderRepository.findActiveByIdAndUserId(folderId, userId) } returns folder
        every { folderRepository.findActiveByIdAndUserId(newParentId, userId) } returns newParent
        every { folderRepository.save(any()) } answers { firstArg() }

        val result = service.moveFolder(folderId, newParentId)

        assertEquals(newParentId, result.parentId)
    }

    @Test
    fun `moveFolder detects cycle — folder cannot be its own parent`() {
        val folderId = UUID.randomUUID()
        val folder = Folder(id = folderId, name = "Self", userId = userId)

        every { folderRepository.findActiveByIdAndUserId(folderId, userId) } returns folder

        val ex = assertThrows<IllegalArgumentException> {
            service.moveFolder(folderId, folderId)
        }
        assertTrue(ex.message!!.contains("descendant"))
    }

    @Test
    fun `moveFolder detects cycle — folder cannot move into its own descendant`() {
        val grandparentId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val childId = UUID.randomUUID()

        val grandparent = Folder(id = grandparentId, name = "GP", userId = userId)
        val parent = Folder(id = parentId, name = "P", userId = userId, parentId = grandparentId)
        val child = Folder(id = childId, name = "C", userId = userId, parentId = parentId)

        every { folderRepository.findActiveByIdAndUserId(grandparentId, userId) } returns grandparent
        every { folderRepository.findActiveByIdAndUserId(childId, userId) } returns child
        // Walk ancestors of child: child -> parent -> grandparent
        every { folderRepository.findActiveByIdAndUserId(parentId, userId) } returns parent

        val ex = assertThrows<IllegalArgumentException> {
            service.moveFolder(grandparentId, childId)
        }
        assertTrue(ex.message!!.contains("descendant"))
    }

    @Test
    fun `deleteFolder soft-deletes and reparents children`() {
        val folderId = UUID.randomUUID()
        val parentId = UUID.randomUUID()
        val folder = Folder(id = folderId, name = "ToDelete", userId = userId, parentId = parentId)
        val child = Folder(id = UUID.randomUUID(), name = "Child", userId = userId, parentId = folderId)

        every { folderRepository.findActiveByIdAndUserId(folderId, userId) } returns folder
        every { folderRepository.findChildrenByParentId(folderId, userId) } returns listOf(child)
        every { folderRepository.save(any()) } answers { firstArg() }

        service.deleteFolder(folderId)

        verify { folderRepository.save(match { it.id == child.id && it.parentId == parentId }) }
        verify { folderRepository.save(match { it.id == folderId && it.deletedAt != null }) }
    }
}
```

- [x] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*FolderOperationsTest*" -q`
Expected: FAIL — BookmarkService constructor doesn't accept FolderRepository, methods don't exist

- [x] **Step 3: Implement folder CRUD in BookmarkService**

Modify `src/main/kotlin/org/sightech/memoryvault/bookmark/service/BookmarkService.kt`:

Add `FolderRepository` to the constructor. Add these methods:

```kotlin
// Add to constructor:
// private val folderRepository: FolderRepository

fun createFolder(name: String, parentId: UUID?): Folder {
    val userId = CurrentUser.userId()
    if (parentId != null) {
        folderRepository.findActiveByIdAndUserId(parentId, userId)
            ?: throw IllegalArgumentException("Parent folder not found")
    }
    val folder = Folder(name = name, userId = userId, parentId = parentId)
    return folderRepository.save(folder)
}

fun renameFolder(id: UUID, name: String): Folder {
    val userId = CurrentUser.userId()
    val folder = folderRepository.findActiveByIdAndUserId(id, userId)
        ?: throw IllegalArgumentException("Folder not found")
    folder.name = name
    folder.updatedAt = Instant.now()
    return folderRepository.save(folder)
}

fun moveFolder(id: UUID, newParentId: UUID?): Folder {
    val userId = CurrentUser.userId()
    val folder = folderRepository.findActiveByIdAndUserId(id, userId)
        ?: throw IllegalArgumentException("Folder not found")

    if (newParentId != null) {
        // Cycle detection: walk ancestors of newParentId to ensure id isn't among them
        if (newParentId == id) {
            throw IllegalArgumentException("Cannot move a folder into its own descendant")
        }
        var currentId: UUID? = newParentId
        while (currentId != null) {
            val ancestor = folderRepository.findActiveByIdAndUserId(currentId, userId) ?: break
            if (ancestor.id == id) {
                throw IllegalArgumentException("Cannot move a folder into its own descendant")
            }
            currentId = ancestor.parentId
        }
    }

    folder.parentId = newParentId
    folder.updatedAt = Instant.now()
    return folderRepository.save(folder)
}

fun deleteFolder(id: UUID) {
    val userId = CurrentUser.userId()
    val folder = folderRepository.findActiveByIdAndUserId(id, userId)
        ?: throw IllegalArgumentException("Folder not found")

    // Reparent children to deleted folder's parent
    val children = folderRepository.findChildrenByParentId(id, userId)
    children.forEach { child ->
        child.parentId = folder.parentId
        child.updatedAt = Instant.now()
        folderRepository.save(child)
    }

    folder.deletedAt = Instant.now()
    folder.updatedAt = Instant.now()
    folderRepository.save(folder)
}

fun findAllFolders(): List<Folder> {
    return folderRepository.findAllActiveByUserId(CurrentUser.userId())
}

fun findFolder(id: UUID): Folder? {
    return folderRepository.findActiveByIdAndUserId(id, CurrentUser.userId())
}
```

- [x] **Step 4: Update existing BookmarkService constructor to accept FolderRepository**

The existing constructor is `BookmarkService(bookmarkRepository: BookmarkRepository, tagService: TagService)`. Change it to:

```kotlin
@Service
class BookmarkService(
    private val bookmarkRepository: BookmarkRepository,
    private val folderRepository: FolderRepository,
    private val tagService: TagService
)
```

Then update `BookmarkServiceTest.kt` — add `private val folderRepository = mockk<FolderRepository>(relaxed = true)` and change the service instantiation to:

```kotlin
service = BookmarkService(bookmarkRepository, folderRepository, tagService)
```

Move this into a `@BeforeEach` if it's currently at declaration.

- [x] **Step 5: Run all tests**

Run: `./gradlew test -q`
Expected: All tests pass (existing + new folder tests)

- [x] **Step 6: Commit**

```
git add src/main/kotlin/org/sightech/memoryvault/bookmark/service/BookmarkService.kt src/test/kotlin/org/sightech/memoryvault/bookmark/service/FolderOperationsTest.kt src/test/kotlin/org/sightech/memoryvault/bookmark/service/BookmarkServiceTest.kt
git commit -m "feat: folder CRUD with cycle detection in BookmarkService"
```

---

### Task 4: BookmarkService — Bookmark-Folder Operations and Export Update

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/bookmark/service/BookmarkService.kt`
- Modify: `src/test/kotlin/org/sightech/memoryvault/bookmark/service/BookmarkServiceTest.kt`

- [x] **Step 1: Write failing tests for bookmark-folder operations**

Add to `BookmarkServiceTest.kt`:

```kotlin
@Test
fun `moveBookmark updates folderId`() {
    val bookmarkId = UUID.randomUUID()
    val folderId = UUID.randomUUID()
    val bookmark = Bookmark(id = bookmarkId, url = "https://example.com", title = "Ex", userId = userId)
    val folder = Folder(id = folderId, name = "Tech", userId = userId)

    every { bookmarkRepository.findActiveByIdAndUserId(bookmarkId, userId) } returns bookmark
    every { folderRepository.findActiveByIdAndUserId(folderId, userId) } returns folder
    every { bookmarkRepository.save(any()) } answers { firstArg() }

    val result = service.moveBookmark(bookmarkId, folderId)

    assertEquals(folderId, result.folderId)
}

@Test
fun `moveBookmark to null unfiled the bookmark`() {
    val bookmarkId = UUID.randomUUID()
    val bookmark = Bookmark(id = bookmarkId, url = "https://example.com", title = "Ex", userId = userId, folderId = UUID.randomUUID())

    every { bookmarkRepository.findActiveByIdAndUserId(bookmarkId, userId) } returns bookmark
    every { bookmarkRepository.save(any()) } answers { firstArg() }

    val result = service.moveBookmark(bookmarkId, null)

    assertNull(result.folderId)
}

@Test
fun `reorderBookmarks updates sortOrder for all bookmarks in folder`() {
    val folderId = UUID.randomUUID()
    val id1 = UUID.randomUUID()
    val id2 = UUID.randomUUID()
    val id3 = UUID.randomUUID()
    val b1 = Bookmark(id = id1, url = "https://a.com", title = "A", userId = userId, folderId = folderId, sortOrder = 0)
    val b2 = Bookmark(id = id2, url = "https://b.com", title = "B", userId = userId, folderId = folderId, sortOrder = 1)
    val b3 = Bookmark(id = id3, url = "https://c.com", title = "C", userId = userId, folderId = folderId, sortOrder = 2)

    every { bookmarkRepository.findByFolderIdAndUserId(folderId, userId) } returns listOf(b1, b2, b3)
    every { bookmarkRepository.save(any()) } answers { firstArg() }

    // Reorder: C, A, B
    val result = service.reorderBookmarks(folderId, listOf(id3, id1, id2))

    verify { bookmarkRepository.save(match { it.id == id3 && it.sortOrder == 0 }) }
    verify { bookmarkRepository.save(match { it.id == id1 && it.sortOrder == 1 }) }
    verify { bookmarkRepository.save(match { it.id == id2 && it.sortOrder == 2 }) }
}

@Test
fun `exportNetscapeHtml includes folder hierarchy`() {
    val folderId = UUID.randomUUID()
    val folder = Folder(id = folderId, name = "Tech", userId = userId)
    val bookmark = Bookmark(id = UUID.randomUUID(), url = "https://example.com", title = "Example", userId = userId, folderId = folderId)

    every { folderRepository.findAllActiveByUserId(userId) } returns listOf(folder)
    every { bookmarkRepository.findAllActiveByUserId(userId) } returns listOf(bookmark)

    val html = service.exportNetscapeHtml()

    assertTrue(html.contains("<H3>Tech</H3>"))
    assertTrue(html.contains("<A HREF=\"https://example.com\">Example</A>"))
}
```

- [x] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*BookmarkServiceTest*" -q`
Expected: FAIL — methods don't exist

- [x] **Step 3: Implement moveBookmark, reorderBookmarks, and update exportNetscapeHtml**

Add to `BookmarkService.kt`:

```kotlin
fun moveBookmark(bookmarkId: UUID, folderId: UUID?): Bookmark {
    val userId = CurrentUser.userId()
    val bookmark = bookmarkRepository.findActiveByIdAndUserId(bookmarkId, userId)
        ?: throw IllegalArgumentException("Bookmark not found")
    if (folderId != null) {
        folderRepository.findActiveByIdAndUserId(folderId, userId)
            ?: throw IllegalArgumentException("Folder not found")
    }
    bookmark.folderId = folderId
    bookmark.updatedAt = Instant.now()
    return bookmarkRepository.save(bookmark)
}

fun reorderBookmarks(folderId: UUID?, bookmarkIds: List<UUID>): List<Bookmark> {
    val userId = CurrentUser.userId()
    val bookmarks = if (folderId != null) {
        bookmarkRepository.findByFolderIdAndUserId(folderId, userId)
    } else {
        bookmarkRepository.findUnfiledByUserId(userId)
    }
    val bookmarkMap = bookmarks.associateBy { it.id }
    return bookmarkIds.mapIndexed { index, id ->
        val bookmark = bookmarkMap[id] ?: throw IllegalArgumentException("Bookmark $id not found in folder")
        bookmark.sortOrder = index
        bookmark.updatedAt = Instant.now()
        bookmarkRepository.save(bookmark)
    }
}
```

Update `exportNetscapeHtml()` to walk folder tree recursively:

```kotlin
fun exportNetscapeHtml(): String {
    val userId = CurrentUser.userId()
    val folders = folderRepository.findAllActiveByUserId(userId)
    val bookmarks = bookmarkRepository.findAllActiveByUserId(userId)

    val folderMap = folders.groupBy { it.parentId }
    val bookmarksByFolder = bookmarks.groupBy { it.folderId }

    val sb = StringBuilder()
    sb.appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
    sb.appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
    sb.appendLine("<TITLE>Bookmarks</TITLE>")
    sb.appendLine("<H1>Bookmarks</H1>")
    sb.appendLine("<DL><p>")

    fun writeFolder(folderId: UUID?, indent: String) {
        // Write subfolders
        folderMap[folderId]?.forEach { folder ->
            sb.appendLine("$indent<DT><H3>${folder.name}</H3>")
            sb.appendLine("$indent<DL><p>")
            writeFolder(folder.id, "$indent    ")
            sb.appendLine("$indent</DL><p>")
        }
        // Write bookmarks in this folder
        bookmarksByFolder[folderId]?.forEach { bookmark ->
            sb.appendLine("$indent<DT><A HREF=\"${bookmark.url}\">${bookmark.title}</A>")
        }
    }

    writeFolder(null, "    ")

    sb.appendLine("</DL><p>")
    return sb.toString()
}
```

- [x] **Step 4: Run all tests**

Run: `./gradlew test -q`
Expected: All tests pass

- [x] **Step 5: Commit**

```
git add src/main/kotlin/org/sightech/memoryvault/bookmark/service/BookmarkService.kt src/test/kotlin/org/sightech/memoryvault/bookmark/service/BookmarkServiceTest.kt
git commit -m "feat: moveBookmark, reorderBookmarks, folder-aware Netscape export"
```

---

### Task 5: IngestService — URL Normalization and Diff/Preview

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/bookmark/service/IngestService.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/bookmark/entity/IngestPreview.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/bookmark/service/IngestServiceTest.kt`

- [ ] **Step 1: Create IngestPreviewEntity and IngestPreviewRepository first (needed by IngestService constructor)**

Create `src/main/kotlin/org/sightech/memoryvault/bookmark/entity/IngestPreviewEntity.kt`:

```kotlin
package org.sightech.memoryvault.bookmark.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "ingest_previews")
class IngestPreviewEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preview_data", nullable = false, columnDefinition = "jsonb")
    var previewData: String,

    @Column(nullable = false)
    var committed: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant = Instant.now().plusSeconds(3600)
)
```

Create `src/main/kotlin/org/sightech/memoryvault/bookmark/repository/IngestPreviewRepository.kt`:

```kotlin
package org.sightech.memoryvault.bookmark.repository

import org.sightech.memoryvault.bookmark.entity.IngestPreviewEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface IngestPreviewRepository : JpaRepository<IngestPreviewEntity, UUID> {
    @Query("SELECT p FROM IngestPreviewEntity p WHERE p.id = :id AND p.userId = :userId AND p.committed = false AND p.expiresAt > CURRENT_TIMESTAMP")
    fun findActiveByIdAndUserId(id: UUID, userId: UUID): IngestPreviewEntity?
}
```

- [ ] **Step 2: Write failing tests for URL normalization**

Create `src/test/kotlin/org/sightech/memoryvault/bookmark/service/UrlNormalizationTest.kt`:

```kotlin
package org.sightech.memoryvault.bookmark.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class UrlNormalizationTest {

    @Test
    fun `normalizes scheme to lowercase`() {
        assertEquals("https://example.com/path", IngestService.normalizeUrl("HTTPS://Example.com/path"))
    }

    @Test
    fun `strips trailing slash`() {
        assertEquals("https://example.com", IngestService.normalizeUrl("https://example.com/"))
    }

    @Test
    fun `strips www prefix`() {
        assertEquals("https://example.com", IngestService.normalizeUrl("https://www.example.com"))
    }

    @Test
    fun `sorts query parameters`() {
        assertEquals("https://example.com?a=1&b=2", IngestService.normalizeUrl("https://example.com?b=2&a=1"))
    }

    @Test
    fun `preserves path case`() {
        assertEquals("https://example.com/MyPage", IngestService.normalizeUrl("https://example.com/MyPage"))
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests "*UrlNormalizationTest*" -q`
Expected: FAIL — IngestService does not exist

- [ ] **Step 4: Create IngestService with normalizeUrl**

```kotlin
package org.sightech.memoryvault.bookmark.service

import org.springframework.stereotype.Service
import org.sightech.memoryvault.bookmark.repository.BookmarkRepository
import org.sightech.memoryvault.bookmark.repository.FolderRepository
import org.sightech.memoryvault.bookmark.repository.IngestPreviewRepository
import tools.jackson.databind.ObjectMapper
import java.net.URI

@Service
class IngestService(
    private val bookmarkRepository: BookmarkRepository,
    private val folderRepository: FolderRepository,
    private val bookmarkService: BookmarkService,
    private val ingestPreviewRepository: IngestPreviewRepository,
    private val objectMapper: ObjectMapper  // Inject Spring-configured ObjectMapper (has Kotlin + Java 8 time modules)
) {

    companion object {
        fun normalizeUrl(url: String): String {
            val uri = URI.create(url.trim())
            val scheme = (uri.scheme ?: "https").lowercase()
            val host = (uri.host ?: "").lowercase().removePrefix("www.")
            val port = if (uri.port > 0 && uri.port != 443 && uri.port != 80) ":${uri.port}" else ""
            val path = (uri.rawPath ?: "").trimEnd('/')
            val query = uri.rawQuery?.split("&")?.sorted()?.joinToString("&")
            val queryString = if (query != null) "?$query" else ""
            return "$scheme://$host$port$path$queryString"
        }
    }
}
```

- [ ] **Step 5: Run normalization tests**

Run: `./gradlew test --tests "*UrlNormalizationTest*" -q`
Expected: PASS

- [ ] **Step 6: Write failing tests for ingest diff/preview**

Add to `IngestServiceTest.kt`:

```kotlin
package org.sightech.memoryvault.bookmark.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.sightech.memoryvault.bookmark.entity.*
import org.sightech.memoryvault.bookmark.repository.*
import org.sightech.memoryvault.auth.CurrentUser
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.time.Instant
import java.util.UUID

class IngestServiceTest {

    private val bookmarkRepository = mockk<BookmarkRepository>(relaxed = true)
    private val folderRepository = mockk<FolderRepository>(relaxed = true)
    private val bookmarkService = mockk<BookmarkService>(relaxed = true)
    private val ingestPreviewRepository = mockk<IngestPreviewRepository>(relaxed = true)
    private val objectMapper = mockk<tools.jackson.databind.ObjectMapper>(relaxed = true)
    private lateinit var service: IngestService
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        val auth = UsernamePasswordAuthenticationToken(userId.toString(), null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
        service = IngestService(bookmarkRepository, folderRepository, bookmarkService, ingestPreviewRepository, objectMapper)
    }

    @Test
    fun `new bookmark is marked as NEW`() {
        every { bookmarkRepository.findAllActiveByUserId(userId) } returns emptyList()
        every { bookmarkRepository.findByNormalizedUrlAndUserId(any(), userId) } returns null
        every { ingestPreviewRepository.save(any()) } answers { firstArg() }

        val input = listOf(IngestBookmarkInput("https://new.com", "New Site", "Tech"))
        val preview = service.generatePreview(input)

        assertEquals(1, preview.summary.newCount)
        assertEquals(IngestStatus.NEW, preview.items.first().status)
    }

    @Test
    fun `existing bookmark with same URL is UNCHANGED`() {
        val existing = Bookmark(url = "https://example.com", title = "Example", userId = userId, normalizedUrl = "https://example.com")
        every { bookmarkRepository.findAllActiveByUserId(userId) } returns listOf(existing)
        every { bookmarkRepository.findByNormalizedUrlAndUserId("https://example.com", userId) } returns existing
        every { ingestPreviewRepository.save(any()) } answers { firstArg() }

        val input = listOf(IngestBookmarkInput("https://example.com", "Example", null))
        val preview = service.generatePreview(input)

        assertEquals(1, preview.summary.unchangedCount)
    }

    @Test
    fun `existing bookmark with different title is TITLE_CHANGED`() {
        val existing = Bookmark(url = "https://example.com", title = "Old Title", userId = userId, normalizedUrl = "https://example.com")
        every { bookmarkRepository.findByNormalizedUrlAndUserId("https://example.com", userId) } returns existing
        every { ingestPreviewRepository.save(any()) } answers { firstArg() }

        val input = listOf(IngestBookmarkInput("https://example.com", "New Title", null))
        val preview = service.generatePreview(input)

        assertEquals(1, preview.summary.titleChangedCount)
    }

    @Test
    fun `existing bookmark in different folder is MOVED`() {
        val folderId = UUID.randomUUID()
        val existing = Bookmark(url = "https://example.com", title = "Example", userId = userId, normalizedUrl = "https://example.com", folderId = folderId)
        val targetFolder = Folder(id = UUID.randomUUID(), name = "Other", userId = userId)

        every { bookmarkRepository.findByNormalizedUrlAndUserId("https://example.com", userId) } returns existing
        every { folderRepository.findAllActiveByUserId(userId) } returns listOf(targetFolder)
        every { ingestPreviewRepository.save(any()) } answers { firstArg() }

        val input = listOf(IngestBookmarkInput("https://example.com", "Example", "Other"))
        val preview = service.generatePreview(input)

        assertEquals(1, preview.summary.movedCount)
    }

    @Test
    fun `soft-deleted bookmark is PREVIOUSLY_DELETED`() {
        val deleted = Bookmark(url = "https://deleted.com", title = "Deleted", userId = userId, normalizedUrl = "https://deleted.com", deletedAt = Instant.now())
        every { bookmarkRepository.findByNormalizedUrlAndUserId("https://deleted.com", userId) } returns null
        // Query that includes soft-deleted:
        every { bookmarkRepository.findByNormalizedUrlIncludingDeleted("https://deleted.com", userId) } returns deleted
        every { ingestPreviewRepository.save(any()) } answers { firstArg() }

        val input = listOf(IngestBookmarkInput("https://deleted.com", "Deleted", null))
        val preview = service.generatePreview(input)

        assertEquals(1, preview.summary.previouslyDeletedCount)
    }
}
```

- [ ] **Step 7: Run tests to verify they fail**

Run: `./gradlew test --tests "*IngestServiceTest*" -q`
Expected: FAIL — missing data classes and methods

- [ ] **Step 8: Create IngestPreview data classes**

Create `src/main/kotlin/org/sightech/memoryvault/bookmark/entity/IngestPreview.kt`:

```kotlin
package org.sightech.memoryvault.bookmark.entity

import java.util.UUID

enum class IngestStatus {
    NEW, UNCHANGED, MOVED, TITLE_CHANGED, PREVIOUSLY_DELETED
}

data class IngestBookmarkInput(
    val url: String,
    val title: String,
    val browserFolder: String?
)

data class IngestItem(
    val url: String,
    val title: String,
    val status: IngestStatus,
    val existingBookmarkId: UUID? = null,
    val suggestedFolderId: UUID? = null,
    val browserFolder: String? = null
)

data class IngestSummary(
    val newCount: Int = 0,
    val unchangedCount: Int = 0,
    val movedCount: Int = 0,
    val titleChangedCount: Int = 0,
    val previouslyDeletedCount: Int = 0
)

data class IngestPreviewResult(
    val previewId: UUID,
    val items: List<IngestItem>,
    val summary: IngestSummary
)

enum class IngestAction {
    ACCEPT, SKIP, UNDELETE
}

data class IngestResolution(
    val url: String,
    val action: IngestAction
)

data class CommitResult(
    val accepted: Int,
    val skipped: Int,
    val undeleted: Int
)
```

Note: IngestPreviewEntity and IngestPreviewRepository were already created in Step 1 of this task.

- [ ] **Step 9: Add findByNormalizedUrlIncludingDeleted to BookmarkRepository**

```kotlin
@Query("SELECT b FROM Bookmark b WHERE b.normalizedUrl = :normalizedUrl AND b.userId = :userId AND b.deletedAt IS NOT NULL")
fun findByNormalizedUrlIncludingDeleted(normalizedUrl: String, userId: UUID): Bookmark?
```

- [ ] **Step 10: Implement generatePreview in IngestService**

Note: Use `objectMapper` (injected via constructor) instead of instantiating a new one.

```kotlin
fun generatePreview(input: List<IngestBookmarkInput>): IngestPreviewResult {
    val userId = CurrentUser.userId()
    val folders = folderRepository.findAllActiveByUserId(userId)
    val folderByName = folders.associateBy { it.name }

    val items = input.map { item ->
        val normalizedUrl = normalizeUrl(item.url)
        val existing = bookmarkRepository.findByNormalizedUrlAndUserId(normalizedUrl, userId)
        val deleted = if (existing == null) {
            bookmarkRepository.findByNormalizedUrlIncludingDeleted(normalizedUrl, userId)
        } else null

        when {
            deleted != null -> IngestItem(
                url = item.url, title = item.title,
                status = IngestStatus.PREVIOUSLY_DELETED,
                existingBookmarkId = deleted.id,
                browserFolder = item.browserFolder
            )
            existing == null -> IngestItem(
                url = item.url, title = item.title,
                status = IngestStatus.NEW,
                suggestedFolderId = item.browserFolder?.let { folderByName[it]?.id },
                browserFolder = item.browserFolder
            )
            existing.title != item.title -> IngestItem(
                url = item.url, title = item.title,
                status = IngestStatus.TITLE_CHANGED,
                existingBookmarkId = existing.id,
                browserFolder = item.browserFolder
            )
            item.browserFolder != null && existing.folderId != folderByName[item.browserFolder]?.id -> IngestItem(
                url = item.url, title = item.title,
                status = IngestStatus.MOVED,
                existingBookmarkId = existing.id,
                suggestedFolderId = folderByName[item.browserFolder]?.id,
                browserFolder = item.browserFolder
            )
            else -> IngestItem(
                url = item.url, title = item.title,
                status = IngestStatus.UNCHANGED,
                existingBookmarkId = existing.id
            )
        }
    }

    val summary = IngestSummary(
        newCount = items.count { it.status == IngestStatus.NEW },
        unchangedCount = items.count { it.status == IngestStatus.UNCHANGED },
        movedCount = items.count { it.status == IngestStatus.MOVED },
        titleChangedCount = items.count { it.status == IngestStatus.TITLE_CHANGED },
        previouslyDeletedCount = items.count { it.status == IngestStatus.PREVIOUSLY_DELETED }
    )

    // Store preview in DB
    val previewEntity = IngestPreviewEntity(
        userId = userId,
        previewData = objectMapper.writeValueAsString(mapOf("items" to items, "summary" to summary))
    )
    ingestPreviewRepository.save(previewEntity)

    return IngestPreviewResult(previewId = previewEntity.id, items = items, summary = summary)
}
```

- [ ] **Step 11: Run tests**

Run: `./gradlew test --tests "*IngestServiceTest*" -q`
Expected: PASS

- [ ] **Step 12: Run all tests**

Run: `./gradlew test -q`
Expected: All pass

- [ ] **Step 13: Commit**

Note: IngestPreviewEntity.kt and IngestPreviewRepository.kt were created in Step 1.

```
git add src/main/kotlin/org/sightech/memoryvault/bookmark/entity/IngestPreview.kt src/main/kotlin/org/sightech/memoryvault/bookmark/entity/IngestPreviewEntity.kt src/main/kotlin/org/sightech/memoryvault/bookmark/repository/IngestPreviewRepository.kt src/main/kotlin/org/sightech/memoryvault/bookmark/service/IngestService.kt src/main/kotlin/org/sightech/memoryvault/bookmark/repository/BookmarkRepository.kt src/test/kotlin/org/sightech/memoryvault/bookmark/service/IngestServiceTest.kt src/test/kotlin/org/sightech/memoryvault/bookmark/service/UrlNormalizationTest.kt
git commit -m "feat: IngestService with URL normalization and diff/preview generation"
```

---

### Task 6: IngestService — Commit Resolutions

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/bookmark/service/IngestService.kt`
- Modify: `src/test/kotlin/org/sightech/memoryvault/bookmark/service/IngestServiceTest.kt`

- [ ] **Step 1: Write failing tests for commit**

Add to `IngestServiceTest.kt`:

```kotlin
@Test
fun `commitResolutions accepts NEW bookmarks`() {
    val previewId = UUID.randomUUID()
    val previewData = """{"items":[{"url":"https://new.com","title":"New","status":"NEW","browserFolder":"Tech"}],"summary":{"newCount":1}}"""
    val previewEntity = IngestPreviewEntity(id = previewId, userId = userId, previewData = previewData)

    every { ingestPreviewRepository.findActiveByIdAndUserId(previewId, userId) } returns previewEntity
    every { bookmarkService.create(any(), any(), any()) } returns mockk(relaxed = true)
    every { ingestPreviewRepository.save(any()) } answers { firstArg() }

    val resolutions = listOf(IngestResolution("https://new.com", IngestAction.ACCEPT))
    val result = service.commitResolutions(previewId, resolutions)

    assertEquals(1, result.accepted)
    verify { bookmarkService.create("https://new.com", "New", emptyList()) }
}

@Test
fun `commitResolutions skips SKIP actions`() {
    val previewId = UUID.randomUUID()
    val previewData = """{"items":[{"url":"https://skip.com","title":"Skip","status":"NEW"}],"summary":{"newCount":1}}"""
    val previewEntity = IngestPreviewEntity(id = previewId, userId = userId, previewData = previewData)

    every { ingestPreviewRepository.findActiveByIdAndUserId(previewId, userId) } returns previewEntity
    every { ingestPreviewRepository.save(any()) } answers { firstArg() }

    val resolutions = listOf(IngestResolution("https://skip.com", IngestAction.SKIP))
    val result = service.commitResolutions(previewId, resolutions)

    assertEquals(1, result.skipped)
    verify(exactly = 0) { bookmarkService.create(any(), any(), any()) }
}

@Test
fun `commitResolutions undeletes PREVIOUSLY_DELETED bookmarks`() {
    val previewId = UUID.randomUUID()
    val bookmarkId = UUID.randomUUID()
    val previewData = """{"items":[{"url":"https://deleted.com","title":"Deleted","status":"PREVIOUSLY_DELETED","existingBookmarkId":"$bookmarkId"}],"summary":{"previouslyDeletedCount":1}}"""
    val previewEntity = IngestPreviewEntity(id = previewId, userId = userId, previewData = previewData)
    val deletedBookmark = Bookmark(id = bookmarkId, url = "https://deleted.com", title = "Deleted", userId = userId, deletedAt = Instant.now())

    every { ingestPreviewRepository.findActiveByIdAndUserId(previewId, userId) } returns previewEntity
    every { bookmarkRepository.findById(bookmarkId) } returns java.util.Optional.of(deletedBookmark)
    every { bookmarkRepository.save(any()) } answers { firstArg() }
    every { ingestPreviewRepository.save(any()) } answers { firstArg() }

    val resolutions = listOf(IngestResolution("https://deleted.com", IngestAction.UNDELETE))
    val result = service.commitResolutions(previewId, resolutions)

    assertEquals(1, result.undeleted)
    verify { bookmarkRepository.save(match { it.id == bookmarkId && it.deletedAt == null }) }
}

@Test
fun `commitResolutions marks preview as committed`() {
    val previewId = UUID.randomUUID()
    val previewData = """{"items":[],"summary":{}}"""
    val previewEntity = IngestPreviewEntity(id = previewId, userId = userId, previewData = previewData)

    every { ingestPreviewRepository.findActiveByIdAndUserId(previewId, userId) } returns previewEntity
    every { ingestPreviewRepository.save(any()) } answers { firstArg() }

    service.commitResolutions(previewId, emptyList())

    verify { ingestPreviewRepository.save(match { it.committed }) }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*IngestServiceTest*commitResolutions*" -q`
Expected: FAIL — commitResolutions does not exist

- [ ] **Step 3: Implement commitResolutions**

Add to `IngestService.kt`:

```kotlin
fun commitResolutions(previewId: UUID, resolutions: List<IngestResolution>): CommitResult {
    val userId = CurrentUser.userId()
    val preview = ingestPreviewRepository.findActiveByIdAndUserId(previewId, userId)
        ?: throw IllegalArgumentException("Preview not found or expired")

    // Use this.objectMapper (injected via constructor, not instantiated inline)
    val data = objectMapper.readTree(preview.previewData)
    val items = data["items"].map { node ->
        IngestItem(
            url = node["url"].asText(),
            title = node["title"].asText(),
            status = IngestStatus.valueOf(node["status"].asText()),
            existingBookmarkId = node["existingBookmarkId"]?.asText()?.let { UUID.fromString(it) },
            suggestedFolderId = node["suggestedFolderId"]?.asText()?.let { UUID.fromString(it) },
            browserFolder = node["browserFolder"]?.asText()
        )
    }

    val resolutionMap = resolutions.associateBy { normalizeUrl(it.url) }
    var accepted = 0
    var skipped = 0
    var undeleted = 0

    items.forEach { item ->
        val normalizedUrl = normalizeUrl(item.url)
        val resolution = resolutionMap[normalizedUrl] ?: return@forEach
        when (resolution.action) {
            IngestAction.SKIP -> skipped++
            IngestAction.ACCEPT -> {
                when (item.status) {
                    IngestStatus.NEW -> {
                        val bookmark = bookmarkService.create(item.url, item.title, emptyList())
                        item.suggestedFolderId?.let { folderId ->
                            bookmarkService.moveBookmark(bookmark.id, folderId)
                        }
                    }
                    IngestStatus.TITLE_CHANGED -> {
                        item.existingBookmarkId?.let { id ->
                            val bookmark = bookmarkRepository.findById(id).orElse(null)
                            if (bookmark != null) {
                                bookmark.title = item.title
                                bookmark.updatedAt = Instant.now()
                                bookmarkRepository.save(bookmark)
                            }
                        }
                    }
                    IngestStatus.MOVED -> {
                        item.existingBookmarkId?.let { id ->
                            item.suggestedFolderId?.let { folderId ->
                                bookmarkService.moveBookmark(id, folderId)
                            }
                        }
                    }
                    else -> {}
                }
                accepted++
            }
            IngestAction.UNDELETE -> {
                item.existingBookmarkId?.let { id ->
                    val bookmark = bookmarkRepository.findById(id).orElse(null)
                    if (bookmark != null) {
                        bookmark.deletedAt = null
                        bookmark.updatedAt = Instant.now()
                        bookmarkRepository.save(bookmark)
                    }
                }
                undeleted++
            }
        }
    }

    preview.committed = true
    ingestPreviewRepository.save(preview)

    return CommitResult(accepted = accepted, skipped = skipped, undeleted = undeleted)
}

fun getPreview(previewId: UUID): IngestPreviewResult? {
    val userId = CurrentUser.userId()
    val preview = ingestPreviewRepository.findActiveByIdAndUserId(previewId, userId) ?: return null
    // Use this.objectMapper (injected via constructor, not instantiated inline)
    val data = objectMapper.readTree(preview.previewData)
    val items = data["items"].map { node ->
        IngestItem(
            url = node["url"].asText(),
            title = node["title"].asText(),
            status = IngestStatus.valueOf(node["status"].asText()),
            existingBookmarkId = node["existingBookmarkId"]?.asText()?.let { UUID.fromString(it) },
            suggestedFolderId = node["suggestedFolderId"]?.asText()?.let { UUID.fromString(it) },
            browserFolder = node["browserFolder"]?.asText()
        )
    }
    val summary = IngestSummary(
        newCount = items.count { it.status == IngestStatus.NEW },
        unchangedCount = items.count { it.status == IngestStatus.UNCHANGED },
        movedCount = items.count { it.status == IngestStatus.MOVED },
        titleChangedCount = items.count { it.status == IngestStatus.TITLE_CHANGED },
        previouslyDeletedCount = items.count { it.status == IngestStatus.PREVIOUSLY_DELETED }
    )
    return IngestPreviewResult(previewId = preview.id, items = items, summary = summary)
}
```

- [ ] **Step 4: Run all tests**

Run: `./gradlew test -q`
Expected: All pass

- [ ] **Step 5: Commit**

```
git add src/main/kotlin/org/sightech/memoryvault/bookmark/service/IngestService.kt src/test/kotlin/org/sightech/memoryvault/bookmark/service/IngestServiceTest.kt
git commit -m "feat: IngestService commit resolutions — accept, skip, undelete"
```

---

### Task 7: REST Controllers for Ingest

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/bookmark/controller/IngestController.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/bookmark/controller/IngestControllerTest.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt`

- [ ] **Step 1: Write failing tests for ingest endpoints**

```kotlin
package org.sightech.memoryvault.bookmark.controller

import org.junit.jupiter.api.Test
import org.sightech.memoryvault.bookmark.entity.*
import org.sightech.memoryvault.bookmark.service.IngestService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.get
import java.util.UUID

// Integration test — uses real services with TestContainers PostgreSQL.
// Follow the same pattern as BookmarkIntegrationTest.kt for @Testcontainers setup.
@SpringBootTest
@AutoConfigureMockMvc
class IngestControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    // Auth setup: Follow the pattern in BookmarkIntegrationTest.kt.
    // Obtain a JWT token via POST /api/auth/login with seed user credentials
    // (email: "system@memoryvault.local", password: "memoryvault").
    // Pass it as "Authorization: Bearer <token>" header on all requests.

    @Test
    fun `POST ingest returns preview with previewId`() {
        val token = obtainJwtToken() // helper method — see BookmarkIntegrationTest

        mockMvc.post("/api/bookmarks/ingest") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"bookmarks":[{"url":"https://new-site.com","title":"New Site","browserFolder":"Tech"}]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.previewId") { exists() }
            jsonPath("$.summary.newCount") { value(1) }
            jsonPath("$.items[0].status") { value("NEW") }
        }
    }

    @Test
    fun `GET ingest preview returns 404 for unknown previewId`() {
        val token = obtainJwtToken()

        mockMvc.get("/api/bookmarks/ingest/${UUID.randomUUID()}") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isNotFound() }
        }
    }

    // Helper — extract from BookmarkIntegrationTest or create a shared TestAuthHelper
    private fun obtainJwtToken(): String {
        val result = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"system@memoryvault.local","password":"memoryvault"}"""
        }.andReturn()
        return result.response.contentAsString // adjust based on actual response shape
    }
}
```

Note: The exact test setup (JWT tokens, SecurityContext) should follow the pattern established in `BookmarkIntegrationTest.kt`. Read that file for the auth test helper pattern before implementing.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*IngestControllerTest*" -q`
Expected: FAIL — IngestController does not exist

- [ ] **Step 3: Create IngestController**

```kotlin
package org.sightech.memoryvault.bookmark.controller

import org.sightech.memoryvault.bookmark.entity.*
import org.sightech.memoryvault.bookmark.service.IngestService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/bookmarks/ingest")
class IngestController(
    private val ingestService: IngestService
) {

    data class IngestRequest(
        val bookmarks: List<IngestBookmarkInput>
    )

    data class CommitRequest(
        val resolutions: List<IngestResolution>
    )

    @PostMapping
    fun ingest(@RequestBody request: IngestRequest): ResponseEntity<IngestPreviewResult> {
        val preview = ingestService.generatePreview(request.bookmarks)
        return ResponseEntity.ok(preview)
    }

    @GetMapping("/{previewId}")
    fun getPreview(@PathVariable previewId: UUID): ResponseEntity<IngestPreviewResult> {
        val preview = ingestService.getPreview(previewId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(preview)
    }

    @PostMapping("/{previewId}/commit")
    fun commit(
        @PathVariable previewId: UUID,
        @RequestBody request: CommitRequest
    ): ResponseEntity<CommitResult> {
        val result = ingestService.commitResolutions(previewId, request.resolutions)
        return ResponseEntity.ok(result)
    }
}
```

- [ ] **Step 4: Update SecurityConfig to permit ingest endpoints (they require auth, already covered by default)**

Verify that `/api/bookmarks/ingest/**` is NOT in the permit list — it should require authentication (which is the default). No changes needed to SecurityConfig unless the endpoints need special handling.

- [ ] **Step 5: Run all tests**

Run: `./gradlew test -q`
Expected: All pass

- [ ] **Step 6: Commit**

```
git add src/main/kotlin/org/sightech/memoryvault/bookmark/controller/IngestController.kt src/test/kotlin/org/sightech/memoryvault/bookmark/controller/IngestControllerTest.kt
git commit -m "feat: REST endpoints for bookmark ingest — preview, get, commit"
```

---

### Task 8: GraphQL Schema and Resolver Updates

**Files:**
- Modify: `src/main/resources/graphql/schema.graphqls`
- Modify: `src/main/kotlin/org/sightech/memoryvault/graphql/BookmarkResolver.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/graphql/FolderResolver.kt`

- [ ] **Step 1: Update GraphQL schema**

Add to `schema.graphqls`:

```graphql
type Folder {
    id: UUID!
    name: String!
    parentId: UUID
    children: [Folder!]!
    bookmarks: [Bookmark!]!
    bookmarkCount: Int!
    sortOrder: Int!
}

type IngestPreview {
    previewId: UUID!
    items: [IngestItem!]!
    summary: IngestSummary!
}

type IngestItem {
    url: String!
    title: String!
    status: IngestStatus!
    existingBookmarkId: UUID
    suggestedFolderId: UUID
    browserFolder: String
}

enum IngestStatus {
    NEW
    UNCHANGED
    MOVED
    TITLE_CHANGED
    PREVIOUSLY_DELETED
}

type IngestSummary {
    newCount: Int!
    unchangedCount: Int!
    movedCount: Int!
    titleChangedCount: Int!
    previouslyDeletedCount: Int!
}

type CommitResult {
    accepted: Int!
    skipped: Int!
    undeleted: Int!
}

input IngestInput {
    bookmarks: [IngestBookmarkInputGql!]!
}

input IngestBookmarkInputGql {
    url: String!
    title: String!
    browserFolder: String
}

input IngestResolutionInput {
    url: String!
    action: IngestAction!
}

enum IngestAction {
    ACCEPT
    SKIP
    UNDELETE
}
```

Move `exportBookmarks` from Mutation to Query. Add to Query:

```graphql
    folders: [Folder!]!
    folder(id: UUID!): Folder
    exportBookmarks: String!
```

Add to Mutation:

```graphql
    createFolder(name: String!, parentId: UUID): Folder!
    renameFolder(id: UUID!, name: String!): Folder!
    moveFolder(id: UUID!, newParentId: UUID): Folder!
    deleteFolder(id: UUID!): Boolean!
    moveBookmark(id: UUID!, folderId: UUID): Bookmark!
    reorderBookmarks(folderId: UUID, bookmarkIds: [UUID!]!): [Bookmark!]!
    ingestBookmarks(input: IngestInput!): IngestPreview!
    commitIngest(previewId: UUID!, resolutions: [IngestResolutionInput!]!): CommitResult!
```

Add to Bookmark type:

```graphql
    folderId: UUID
    sortOrder: Int!
```

- [ ] **Step 2: Create FolderResolver**

```kotlin
package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.bookmark.entity.Folder
import org.sightech.memoryvault.bookmark.service.BookmarkService
import org.springframework.graphql.data.method.annotation.*
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class FolderResolver(
    private val bookmarkService: BookmarkService,
    private val bookmarkRepository: BookmarkRepository
) {
    @QueryMapping
    fun folders(): List<Folder> = bookmarkService.findAllFolders()

    @QueryMapping
    fun folder(@Argument id: UUID): Folder? = bookmarkService.findFolder(id)

    @MutationMapping
    fun createFolder(@Argument name: String, @Argument parentId: UUID?): Folder =
        bookmarkService.createFolder(name, parentId)

    @MutationMapping
    fun renameFolder(@Argument id: UUID, @Argument name: String): Folder =
        bookmarkService.renameFolder(id, name)

    @MutationMapping
    fun moveFolder(@Argument id: UUID, @Argument newParentId: UUID?): Folder =
        bookmarkService.moveFolder(id, newParentId)

    @MutationMapping
    fun deleteFolder(@Argument id: UUID): Boolean {
        bookmarkService.deleteFolder(id)
        return true
    }

    // Batch resolvers to avoid N+1 on Folder.children and Folder.bookmarks
    @BatchMapping(typeName = "Folder", field = "children")
    fun children(folders: List<Folder>): Map<Folder, List<Folder>> {
        val userId = CurrentUser.userId()
        val allFolders = bookmarkService.findAllFolders()
        val childrenByParent = allFolders.groupBy { it.parentId }
        return folders.associateWith { folder -> childrenByParent[folder.id] ?: emptyList() }
    }

    @BatchMapping(typeName = "Folder", field = "bookmarks")
    fun bookmarks(folders: List<Folder>): Map<Folder, List<Bookmark>> {
        val userId = CurrentUser.userId()
        val allBookmarks = bookmarkRepository.findAllActiveByUserId(userId)
        val bookmarksByFolder = allBookmarks.groupBy { it.folderId }
        return folders.associateWith { folder -> bookmarksByFolder[folder.id] ?: emptyList() }
    }

    @BatchMapping(typeName = "Folder", field = "bookmarkCount")
    fun bookmarkCount(folders: List<Folder>): Map<Folder, Int> {
        val userId = CurrentUser.userId()
        val allBookmarks = bookmarkRepository.findAllActiveByUserId(userId)
        val countByFolder = allBookmarks.groupBy { it.folderId }.mapValues { it.value.size }
        return folders.associateWith { folder -> countByFolder[folder.id] ?: 0 }
    }
}
```

- [ ] **Step 3: Update BookmarkResolver**

Move `exportBookmarks` from `@MutationMapping` to `@QueryMapping`. Add new mutations:

```kotlin
    @QueryMapping
    fun exportBookmarks(): String = bookmarkService.exportNetscapeHtml()

    @MutationMapping
    fun moveBookmark(@Argument id: UUID, @Argument folderId: UUID?): Bookmark =
        bookmarkService.moveBookmark(id, folderId)

    @MutationMapping
    fun reorderBookmarks(@Argument folderId: UUID?, @Argument bookmarkIds: List<UUID>): List<Bookmark> =
        bookmarkService.reorderBookmarks(folderId, bookmarkIds)
```

Add `IngestService` to BookmarkResolver constructor. Add ingest mutations:

```kotlin
    @MutationMapping
    fun ingestBookmarks(@Argument input: IngestInput): IngestPreviewResult {
        return ingestService.generatePreview(input.bookmarks.map {
            IngestBookmarkInput(url = it.url, title = it.title, browserFolder = it.browserFolder)
        })
    }

    @MutationMapping
    fun commitIngest(@Argument previewId: UUID, @Argument resolutions: List<IngestResolutionInput>): CommitResult {
        return ingestService.commitResolutions(previewId, resolutions.map {
            IngestResolution(url = it.url, action = IngestAction.valueOf(it.action.name))
        })
    }
```

Create matching input DTOs for GraphQL argument binding:

```kotlin
// These can go in a separate file or in the resolver
data class IngestInput(val bookmarks: List<IngestBookmarkInputGql>)
data class IngestBookmarkInputGql(val url: String, val title: String, val browserFolder: String?)
data class IngestResolutionInput(val url: String, val action: IngestAction)
```

Also verify that `UUID` scalar is registered in `ScalarConfig.kt` — the schema uses `UUID!` throughout. If not registered, the app will fail to start.

- [ ] **Step 4: Run all tests**

Run: `./gradlew test -q`
Expected: All pass

- [ ] **Step 5: Commit**

```
git add src/main/resources/graphql/schema.graphqls src/main/kotlin/org/sightech/memoryvault/graphql/FolderResolver.kt src/main/kotlin/org/sightech/memoryvault/graphql/BookmarkResolver.kt
git commit -m "feat: GraphQL schema + resolvers for folders, ingest, reorder"
```

---

### Task 9: MCP Tool Updates

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/mcp/BookmarkTools.kt`

- [ ] **Step 1: Update MCP tools for folder awareness**

Add folder parameter to `addBookmark`, add new tools:

```kotlin
@Tool(description = "Create a bookmark folder. Use when the user wants to organize bookmarks into folders.")
fun createFolder(name: String, parentFolderName: String?): String {
    val parentId = if (parentFolderName != null) {
        bookmarkService.findAllFolders().find { it.name == parentFolderName }?.id
    } else null
    val folder = bookmarkService.createFolder(name, parentId)
    return "Created folder: ${folder.name}" + if (folder.parentId != null) " (inside $parentFolderName)" else ""
}

@Tool(description = "List all bookmark folders. Use when the user wants to see their folder structure.")
fun listFolders(): String {
    val folders = bookmarkService.findAllFolders()
    if (folders.isEmpty()) return "No folders found."
    return folders.joinToString("\n") { "- ${it.name} (${it.id})" }
}

@Tool(description = "Move a bookmark to a folder. Use when the user wants to organize a bookmark into a specific folder.")
fun moveBookmarkToFolder(bookmarkId: String, folderName: String?): String {
    val folderId = if (folderName != null) {
        bookmarkService.findAllFolders().find { it.name == folderName }?.id
            ?: return "Folder '$folderName' not found"
    } else null
    bookmarkService.moveBookmark(UUID.fromString(bookmarkId), folderId)
    return "Moved bookmark to ${folderName ?: "Unfiled"}"
}
```

- [ ] **Step 2: Run all tests**

Run: `./gradlew test -q`
Expected: All pass

- [ ] **Step 3: Commit**

```
git add src/main/kotlin/org/sightech/memoryvault/mcp/BookmarkTools.kt
git commit -m "feat: MCP tools for folder create, list, and bookmark move"
```

---

### Task 10: Backend Integration Tests

**Files:**
- Create: `src/test/kotlin/org/sightech/memoryvault/bookmark/FolderIntegrationTest.kt`
- Modify: `src/test/kotlin/org/sightech/memoryvault/bookmark/BookmarkIntegrationTest.kt`

- [ ] **Step 1: Write integration tests for folder CRUD via GraphQL**

Follow the pattern in `BookmarkIntegrationTest.kt` — use TestContainers, authenticate with JWT, send GraphQL queries/mutations via MockMvc.

Scaffold:

```kotlin
package org.sightech.memoryvault.bookmark

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class FolderIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        // Login with seed user to get JWT token
        val result = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"system@memoryvault.local","password":"memoryvault"}"""
        }.andReturn()
        token = /* extract token from response — follow BookmarkIntegrationTest pattern */
    }

    @Test
    fun `create root folder via GraphQL`() {
        mockMvc.post("/graphql") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"query":"mutation { createFolder(name: \"Tech\") { id name parentId } }"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.createFolder.name") { value("Tech") }
            jsonPath("$.data.createFolder.parentId") { doesNotExist() }
        }
    }

    // Additional tests follow same pattern...
}
```

Test cases:
- Create root folder
- Create nested folder
- Rename folder
- Move folder (valid)
- Move folder (cycle detection — expect error)
- Delete folder (verify children reparented)
- Query all folders
- Move bookmark to folder
- Export with folder structure

- [ ] **Step 2: Write integration tests for ingest REST endpoints**

Test cases:
- POST /api/bookmarks/ingest with new bookmarks — verify preview response
- GET /api/bookmarks/ingest/{previewId} — verify stored preview matches
- POST /api/bookmarks/ingest/{previewId}/commit — verify bookmarks created
- Expired preview returns 404
- Already-committed preview returns 404

- [ ] **Step 3: Run all tests**

Run: `./gradlew test -q`
Expected: All pass

- [ ] **Step 4: Commit**

```
git add src/test/kotlin/org/sightech/memoryvault/bookmark/FolderIntegrationTest.kt src/test/kotlin/org/sightech/memoryvault/bookmark/BookmarkIntegrationTest.kt
git commit -m "test: integration tests for folders, ingest, and bookmark-folder operations"
```

---

## Chunk 2: Angular Frontend

### Task 11: Refactor Existing BookmarksComponent

**Files:**
- Modify: `client/src/app/bookmarks/bookmarks.ts`
- Modify: `client/src/app/bookmarks/bookmarks.spec.ts`

**Prerequisites:** The existing component uses `CommonModule`, `*ngIf`/`*ngFor`, and `subscribe()`. These must be removed before building the new two-panel layout.

- [ ] **Step 1: Read the current bookmarks.ts file to understand the full template and logic**

- [ ] **Step 2: Replace `CommonModule` import with nothing (standalone components don't need it)**

- [ ] **Step 3: Replace all `*ngIf` with `@if` blocks**

- [ ] **Step 4: Replace all `*ngFor` with `@for` blocks**

- [ ] **Step 5: Remove any `subscribe()` calls — use signals/store methods instead**

The `openAddDialog()` method uses `dialogRef.afterClosed().subscribe(...)`. Replace with:

```typescript
openAddDialog() {
    const dialogRef = this.dialog.open(BookmarkDialogComponent);
    dialogRef.afterClosed().pipe(
        takeUntilDestroyed(this.destroyRef),
        filter((result): result is BookmarkDialogResult => !!result)
    ).subscribe(result => this.store.addBookmark(result));
}
```

This uses `takeUntilDestroyed` (import from `@angular/core/rxjs-interop`) with `inject(DestroyRef)` — this is the standard Angular pattern for one-shot subscriptions that need cleanup. Add `private destroyRef = inject(DestroyRef);` to the component.

- [ ] **Step 6: Run frontend tests**

Run: `cd client && npm test`
Expected: All pass

- [ ] **Step 7: Commit**

```
git add client/src/app/bookmarks/bookmarks.ts client/src/app/bookmarks/bookmarks.spec.ts
git commit -m "refactor: BookmarksComponent — remove CommonModule, use @if/@for, remove subscribe"
```

---

### Task 12: Update Bookmark Store and GraphQL

**Files:**
- Modify: `client/src/app/bookmarks/bookmarks.store.ts`
- Modify: `client/src/app/bookmarks/bookmarks.graphql`
- Regenerate: `client/src/app/shared/graphql/generated.ts`

- [ ] **Step 1: Update bookmarks.graphql with folder queries and mutations**

Add:

```graphql
query GetFolders {
    folders {
        id
        name
        parentId
        bookmarkCount
        sortOrder
    }
}

mutation CreateFolder($name: String!, $parentId: UUID) {
    createFolder(name: $name, parentId: $parentId) {
        id
        name
        parentId
        sortOrder
    }
}

mutation RenameFolder($id: UUID!, $name: String!) {
    renameFolder(id: $id, name: $name) {
        id
        name
    }
}

mutation MoveFolder($id: UUID!, $newParentId: UUID) {
    moveFolder(id: $id, newParentId: $newParentId) {
        id
        parentId
    }
}

mutation DeleteFolder($id: UUID!) {
    deleteFolder(id: $id)
}

mutation MoveBookmark($id: UUID!, $folderId: UUID) {
    moveBookmark(id: $id, folderId: $folderId) {
        id
        folderId
    }
}

query ExportBookmarks {
    exportBookmarks
}
```

Update `GetBookmarks` query to include `folderId` and `sortOrder` in the response fields.

- [ ] **Step 2: Regenerate GraphQL types**

Run: `cd client && npx graphql-codegen`
Expected: `generated.ts` updated with new types

- [ ] **Step 3: Expand bookmarks.store.ts**

Add to state:

```typescript
interface BookmarkState {
    bookmarks: Bookmark[];
    folders: Folder[];
    selectedFolderId: string | null;
    loading: boolean;
    searchQuery: string;
    selectedTags: string[];
    // Ingest state
    ingestPreview: IngestPreview | null;
    ingestLoading: boolean;
}
```

Inject both `Apollo` and `HttpClient` in `withMethods`:

```typescript
withMethods((store, apollo = inject(Apollo), http = inject(HttpClient)) => ({
```

Add methods (implement each as a full rxMethod following the existing `loadBookmarks` pattern):

```typescript
loadFolders: rxMethod<void>(pipe(
    switchMap(() => apollo.query({ query: GetFoldersDocument, fetchPolicy: 'network-only' })),
    tap(result => patchState(store, { folders: result.data.folders }))
)),
selectFolder: (folderId: string | null) => patchState(store, { selectedFolderId: folderId }),
createFolder: rxMethod<{ name: string; parentId?: string }>(pipe(
    switchMap(input => apollo.mutate({ mutation: CreateFolderDocument, variables: input })),
    tap(() => /* reload folders */)
)),
// renameFolder, moveFolder, deleteFolder — same pattern as createFolder
moveBookmark: rxMethod<{ id: string; folderId?: string }>(pipe(
    switchMap(input => apollo.mutate({ mutation: MoveBookmarkDocument, variables: input })),
    tap(() => /* reload bookmarks */)
)),
// Ingest uses REST (HttpClient), not GraphQL:
fetchIngestPreview: rxMethod<string>(pipe(
    switchMap(previewId => http.get<IngestPreviewResult>(`/api/bookmarks/ingest/${previewId}`)),
    tap(preview => patchState(store, { ingestPreview: preview }))
)),
commitIngest: rxMethod<{ previewId: string; resolutions: IngestResolutionInput[] }>(pipe(
    switchMap(({ previewId, resolutions }) =>
        http.post<CommitResult>(`/api/bookmarks/ingest/${previewId}/commit`, { resolutions })),
    tap(() => patchState(store, { ingestPreview: null }))
)),
exportBookmarks: rxMethod<void>(pipe(
    switchMap(() => apollo.query({ query: ExportBookmarksDocument, fetchPolicy: 'no-cache' })),
    tap(result => {
        const blob = new Blob([result.data.exportBookmarks], { type: 'text/html' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'memoryvault-export.html';
        a.click();
        URL.revokeObjectURL(url);
    })
)),
```

Note: Use `IngestPreviewResult` and `IngestResolutionInput` types from `generated.ts`. If codegen doesn't produce them (since ingest uses REST, not GraphQL), define them manually in a `bookmarks.model.ts` file.

Add computed — filtering is **client-side** (bookmarks are loaded once, filtered by folder locally):

```typescript
filteredBookmarks: computed(() => {
    const folderId = store.selectedFolderId();
    const bookmarks = store.bookmarks();
    if (folderId === null) return bookmarks; // show all
    if (folderId === 'unfiled') return bookmarks.filter(b => !b.folderId);
    return bookmarks.filter(b => b.folderId === folderId);
}),
```

The `loadBookmarks` method should load ALL bookmarks for the user (remove server-side folder filtering). Search query and tag filtering remain server-side.

- [ ] **Step 4: Run frontend tests**

Run: `cd client && npm test`
Expected: All pass (update existing tests for new store shape)

- [ ] **Step 5: Commit**

```
git add client/src/app/bookmarks/bookmarks.graphql client/src/app/bookmarks/bookmarks.store.ts client/src/app/shared/graphql/generated.ts
git commit -m "feat: bookmark store with folders, ingest, and export support"
```

---

### Task 13: Folder Tree Component

**Files:**
- Create: `client/src/app/bookmarks/bookmark-tree/bookmark-tree.ts`
- Create: `client/src/app/bookmarks/bookmark-tree/bookmark-tree.spec.ts`

- [ ] **Step 1: Write failing test**

```typescript
import { describe, it, expect } from 'vitest';
import { signal } from '@angular/core';

describe('BookmarkTreeComponent logic', () => {
    it('builds tree from flat folder list', () => {
        const folders = signal([
            { id: '1', name: 'Tech', parentId: null, bookmarkCount: 5, sortOrder: 0 },
            { id: '2', name: 'Frontend', parentId: '1', bookmarkCount: 3, sortOrder: 0 },
            { id: '3', name: 'Work', parentId: null, bookmarkCount: 2, sortOrder: 1 },
        ]);

        // Tree-building logic (will be extracted as a utility or computed)
        function buildTree(flatFolders: any[]) {
            const root: any[] = [];
            const map = new Map<string, any>();
            flatFolders.forEach(f => map.set(f.id, { ...f, children: [] }));
            flatFolders.forEach(f => {
                const node = map.get(f.id)!;
                if (f.parentId && map.has(f.parentId)) {
                    map.get(f.parentId)!.children.push(node);
                } else {
                    root.push(node);
                }
            });
            return root;
        }

        const tree = buildTree(folders());
        expect(tree.length).toBe(2); // Tech, Work
        expect(tree[0].children.length).toBe(1); // Frontend under Tech
        expect(tree[0].children[0].name).toBe('Frontend');
    });

    it('emits folder selection', () => {
        let selectedId: string | null = null;
        const onSelect = (id: string | null) => { selectedId = id; };
        onSelect('folder-1');
        expect(selectedId).toBe('folder-1');
    });
});
```

- [ ] **Step 2: Run test to verify it passes (logic test, no component import)**

Run: `cd client && npm test -- --grep "BookmarkTreeComponent"`
Expected: PASS

- [ ] **Step 3: Create bookmark-tree component**

```typescript
@Component({
    selector: 'app-bookmark-tree',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MatTreeModule, MatIconModule, MatMenuModule, MatButtonModule],
    templateUrl: './bookmark-tree.html',
})
export class BookmarkTreeComponent {
    folders = input.required<Folder[]>();
    folderSelected = output<string | null>();
    // ... implementation
}
```

The component should:
- Accept `folders` signal input (flat list from store)
- Build tree structure via `computed()` using the `buildTree()` function from the test
- Use Angular Material `mat-tree` with `MatTreeFlatDataSource` and `FlatTreeControl`
- Emit `folderSelected` output when a folder is clicked
- Show "All Bookmarks" root node (emits `null`) and "Unfiled" at the bottom (emits `'unfiled'`)
- Show folder bookmark count inline (e.g., "Tech (12)")
- Right-click context menu via `MatMenu` triggered by `(contextmenu)` event: New Folder, Rename, Delete, Move

- [ ] **Step 4: Run all frontend tests**

Run: `cd client && npm test`
Expected: All pass

- [ ] **Step 5: Commit**

```
git add client/src/app/bookmarks/bookmark-tree/
git commit -m "feat: BookmarkTree component with folder hierarchy and context menu"
```

---

### Task 14: Bookmark List Component

**Files:**
- Create: `client/src/app/bookmarks/bookmark-list/bookmark-list.ts`
- Create: `client/src/app/bookmarks/bookmark-list/bookmark-list.spec.ts`

- [ ] **Step 1: Write failing test for bookmark list logic**

Create `client/src/app/bookmarks/bookmark-list/bookmark-list.spec.ts`:

```typescript
import { describe, it, expect } from 'vitest';
import { signal } from '@angular/core';

describe('BookmarkListComponent logic', () => {
    it('sorts bookmarks by sortOrder', () => {
        const bookmarks = [
            { id: '1', title: 'C', sortOrder: 2 },
            { id: '2', title: 'A', sortOrder: 0 },
            { id: '3', title: 'B', sortOrder: 1 },
        ];
        const sorted = [...bookmarks].sort((a, b) => a.sortOrder - b.sortOrder);
        expect(sorted[0].title).toBe('A');
        expect(sorted[1].title).toBe('B');
        expect(sorted[2].title).toBe('C');
    });

    it('tracks multi-selection', () => {
        const selected = new Set<string>();
        selected.add('1');
        selected.add('3');
        expect(selected.size).toBe(2);
        expect(selected.has('2')).toBe(false);
    });

    it('bulk delete builds list of IDs', () => {
        const selected = new Set(['1', '3']);
        const ids = Array.from(selected);
        expect(ids).toEqual(['1', '3']);
    });
});
```

- [ ] **Step 2: Create bookmark-list component**

```typescript
@Component({
    selector: 'app-bookmark-list',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MatTableModule, MatCheckboxModule, MatChipsModule, MatIconModule, MatButtonModule, MatMenuModule],
    templateUrl: './bookmark-list.html',
})
export class BookmarkListComponent {
    bookmarks = input.required<Bookmark[]>();
    bookmarkDeleted = output<string>();
    bookmarkMoved = output<{ id: string; folderId: string }>();
    // ... implementation
}
```

The component should:
- Accept `bookmarks` signal input (already filtered by store)
- Display as a sortable list/table (title, URL, date added, tags)
- Support multi-select with checkboxes
- Bulk actions toolbar: Move to Folder, Tag, Delete
- Inline edit for title
- Click URL opens in new tab
- Tags as `mat-chip` (editable)

- [ ] **Step 3: Run all frontend tests**

Run: `cd client && npm test`
Expected: All pass

- [ ] **Step 4: Commit**

```
git add client/src/app/bookmarks/bookmark-list/
git commit -m "feat: BookmarkList component with sorting, multi-select, bulk actions"
```

---

### Task 15: Ingest Panel Component

**Files:**
- Create: `client/src/app/bookmarks/ingest-panel/ingest-panel.ts`
- Create: `client/src/app/bookmarks/ingest-panel/ingest-panel.spec.ts`

- [ ] **Step 1: Write test for CLI command generation**

```typescript
describe('IngestPanel command generation', () => {
    it('generates Chrome macOS command with token and URL', () => {
        const token = 'eyJhbGciOiJIUzI1NiJ9.test';
        const apiUrl = 'http://localhost:8080';
        const browser = 'chrome';
        const os = 'macos';

        const command = generateIngestCommand(browser, os, token, apiUrl);

        expect(command).toContain('curl');
        expect(command).toContain(token);
        expect(command).toContain(apiUrl);
        expect(command).toContain('Google/Chrome/Default/Bookmarks');
    });

    it('generates Firefox macOS command with sqlite3', () => {
        const command = generateIngestCommand('firefox', 'macos', 'token', 'http://localhost:8080');
        expect(command).toContain('sqlite3');
        expect(command).toContain('places.sqlite');
    });

    it('generates Safari command with plutil', () => {
        const command = generateIngestCommand('safari', 'macos', 'token', 'http://localhost:8080');
        expect(command).toContain('plutil');
        expect(command).toContain('Bookmarks.plist');
    });

    it('Firefox command includes sqlite3 availability check', () => {
        const command = generateIngestCommand('firefox', 'macos', 'token', 'http://localhost:8080');
        expect(command).toContain('command -v sqlite3');
    });

    it('detects browser from user agent', () => {
        const chromeUA = 'Mozilla/5.0 ... Chrome/120.0.0.0 Safari/537.36';
        expect(detectBrowser(chromeUA)).toBe('chrome');
    });

    it('detects OS from user agent', () => {
        const macUA = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)';
        expect(detectOS(macUA)).toBe('macos');
    });
});
```

- [ ] **Step 2: Create ingest-panel component**

The component should:
- Collapsible panel (expanded by default? No — collapsed by default per spec)
- **Import side:**
  - Browser dropdown (auto-detected from `navigator.userAgent`, overridable)
  - OS auto-detected, overridable
  - Copyable code block with generated command (`mat-icon` copy button)
  - Brief instruction text
- **Export side:**
  - "Download" button — calls store `exportBookmarks()`, triggers file download
  - Collapsible "How to import into your browser" with per-browser instructions
- `generateIngestCommand(browser, os, token, apiUrl)` as a pure exported function in `ingest-panel.ts` (testable, imported in spec)
- `detectBrowser(userAgent)` and `detectOS(userAgent)` as pure exported functions
- Token from `inject(AuthService).getToken()`
- API URL from `window.location.origin`
- Component uses `changeDetection: ChangeDetectionStrategy.OnPush`
- Firefox command must include `command -v sqlite3` check with fallback message directing user to Firefox's built-in HTML export

- [ ] **Step 3: Run all frontend tests**

Run: `cd client && npm test`
Expected: All pass

- [ ] **Step 4: Commit**

```
git add client/src/app/bookmarks/ingest-panel/
git commit -m "feat: IngestPanel component with CLI command generation and export"
```

---

### Task 16: Conflict Review Component

**Files:**
- Create: `client/src/app/bookmarks/conflict-review/conflict-review.ts`
- Create: `client/src/app/bookmarks/conflict-review/conflict-review.spec.ts`

- [ ] **Step 1: Write test for conflict grouping and resolution logic**

```typescript
describe('ConflictReview logic', () => {
    it('groups items by status', () => {
        const items = [
            { url: 'a.com', status: 'NEW' },
            { url: 'b.com', status: 'NEW' },
            { url: 'c.com', status: 'MOVED' },
            { url: 'd.com', status: 'PREVIOUSLY_DELETED' },
        ];
        const grouped = groupByStatus(items);
        expect(grouped['NEW'].length).toBe(2);
        expect(grouped['MOVED'].length).toBe(1);
        expect(grouped['PREVIOUSLY_DELETED'].length).toBe(1);
    });

    it('accept all sets all items in group to ACCEPT', () => {
        const resolutions = new Map<string, IngestAction>();
        const items = [
            { url: 'a.com', status: 'NEW' },
            { url: 'b.com', status: 'NEW' },
        ];
        items.forEach(item => resolutions.set(item.url, 'ACCEPT' as IngestAction));
        expect(resolutions.get('a.com')).toBe('ACCEPT');
        expect(resolutions.get('b.com')).toBe('ACCEPT');
    });

    it('builds resolution array from map', () => {
        const resolutions = new Map<string, string>();
        resolutions.set('a.com', 'ACCEPT');
        resolutions.set('b.com', 'SKIP');
        const array = Array.from(resolutions.entries()).map(([url, action]) => ({ url, action }));
        expect(array).toEqual([
            { url: 'a.com', action: 'ACCEPT' },
            { url: 'b.com', action: 'SKIP' },
        ]);
    });
});
```

- [ ] **Step 2: Create conflict-review component**

```typescript
@Component({
    selector: 'app-conflict-review',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [MatDialogModule, MatButtonModule, MatIconModule, MatListModule, MatButtonToggleModule],
    templateUrl: './conflict-review.html',
})
export class ConflictReviewComponent {
    // ...
}
```

The component should:
- Use `mat-dialog` (opened by parent when ingestPreview is loaded)
- Accept `ingestPreview` data via `MAT_DIALOG_DATA` injection
- Group items by status: NEW, MOVED, TITLE_CHANGED, PREVIOUSLY_DELETED
- Each item: bookmark title/URL, what changed, resolution toggle (Accept/Skip/Undelete)
- "Accept All" / "Skip All" per group
- UNCHANGED hidden by default (expandable summary)
- "Commit" button calls store `commitIngest()`
- "Cancel" closes the modal

- [ ] **Step 3: Run all frontend tests**

Run: `cd client && npm test`
Expected: All pass

- [ ] **Step 4: Commit**

```
git add client/src/app/bookmarks/conflict-review/
git commit -m "feat: ConflictReview component with grouping, bulk actions, commit"
```

---

### Task 17: Wire Up Two-Panel Layout

**Files:**
- Modify: `client/src/app/bookmarks/bookmarks.ts`
- Modify: `client/src/app/bookmarks/bookmarks.spec.ts`

- [ ] **Step 1: Rewrite bookmarks.ts template to two-panel layout**

Structure:
```
<ingest-panel> (collapsible, top)
<div class="bookmark-manager">
  <bookmark-tree> (left panel)
  <bookmark-list> (right panel)
</div>
<conflict-review> (modal, shown when ingestPreview is present)
```

- Import all new sub-components (BookmarkTreeComponent, BookmarkListComponent, IngestPanelComponent)
- Wire store signals to component inputs
- Wire component outputs to store methods
- Handle route query param `?ingest=<previewId>` to auto-open conflict review:

```typescript
private route = inject(ActivatedRoute);
private dialog = inject(MatDialog);
private destroyRef = inject(DestroyRef);

// In constructor or ngOnInit equivalent:
toSignal(this.route.queryParamMap).effect(() => {
    const params = this.route.snapshot.queryParamMap;
    const previewId = params.get('ingest');
    if (previewId) {
        this.store.fetchIngestPreview(previewId);
    }
});

// Watch for ingestPreview becoming non-null to open dialog:
effect(() => {
    const preview = this.store.ingestPreview();
    if (preview) {
        this.dialog.open(ConflictReviewComponent, { data: preview, width: '800px' });
    }
});
```

- [ ] **Step 2: Update bookmarks.spec.ts**

Update tests for new component structure. Test:
- Folder selection changes bookmark list
- Ingest panel generates correct command
- Conflict review opens when preview is loaded

- [ ] **Step 3: Run all frontend tests**

Run: `cd client && npm test`
Expected: All pass

- [ ] **Step 4: Verify production build**

Run: `cd client && npm run build`
Expected: Build succeeds with no errors

- [ ] **Step 5: Commit**

```
git add client/src/app/bookmarks/
git commit -m "feat: two-panel bookmark manager layout with tree, list, ingest, and conflict review"
```

---

### Task 18: Barrel Exports and Route Updates

**Files:**
- Create: `client/src/app/bookmarks/index.ts`
- Verify: `client/src/app/bookmarks/bookmarks.routes.ts` (create if missing)

- [ ] **Step 1: Create barrel export**

```typescript
export { BookmarksComponent } from './bookmarks';
export { BookmarkStore } from './bookmarks.store';
export { BookmarkTreeComponent } from './bookmark-tree/bookmark-tree';
export { BookmarkListComponent } from './bookmark-list/bookmark-list';
export { IngestPanelComponent } from './ingest-panel/ingest-panel';
export { ConflictReviewComponent } from './conflict-review/conflict-review';
```

- [ ] **Step 2: Create sub-component barrel exports**

Each sub-component folder also needs an `index.ts` per CLAUDE.md conventions:

```bash
# Create index.ts in each sub-component folder:
echo 'export { BookmarkTreeComponent } from "./bookmark-tree";' > client/src/app/bookmarks/bookmark-tree/index.ts
echo 'export { BookmarkListComponent } from "./bookmark-list";' > client/src/app/bookmarks/bookmark-list/index.ts
echo 'export { IngestPanelComponent } from "./ingest-panel";' > client/src/app/bookmarks/ingest-panel/index.ts
echo 'export { ConflictReviewComponent } from "./conflict-review";' > client/src/app/bookmarks/conflict-review/index.ts
```

Then update the feature barrel (`bookmarks/index.ts`) to re-export from sub-folder barrels:

```typescript
export { BookmarksComponent } from './bookmarks';
export { BookmarkStore } from './bookmarks.store';
export { BookmarkTreeComponent } from './bookmark-tree';
export { BookmarkListComponent } from './bookmark-list';
export { IngestPanelComponent } from './ingest-panel';
export { ConflictReviewComponent } from './conflict-review';
```

- [ ] **Step 3: Verify route handles ingest query param**

Ensure the bookmarks route can accept `?ingest=<previewId>` query param and the component reads it to auto-load the ingest preview.

- [ ] **Step 3: Commit**

```
git add client/src/app/bookmarks/index.ts client/src/app/bookmarks/bookmarks.routes.ts
git commit -m "chore: barrel exports and route updates for bookmarks"
```

---

## Chunk 3: End-to-End Testing and Documentation

### Task 19: Playwright E2E Tests

**Files:**
- Create: `client/e2e/bookmarks.spec.ts`

**Prerequisites:** Backend must be running (`./gradlew bootRun`)

- [ ] **Step 1: Write E2E tests**

Test cases:

**Folder management:**
- Navigate to bookmarks page, verify two-panel layout renders
- Create a root folder via context menu, verify it appears in tree
- Create a nested folder, verify it appears under parent
- Rename a folder via context menu
- Move a folder via context menu (verify reparented in tree)
- Delete a folder, verify children reparented and bookmarks remain

**Bookmark-folder interaction:**
- Create a bookmark and move it to a folder (verify it appears in that folder's list)
- Click "All Bookmarks" node, verify all bookmarks shown
- Click "Unfiled" node, verify only unfiled bookmarks shown
- Multi-select bookmarks, bulk move to folder
- Verify folder bookmark counts update correctly

**Ingest panel:**
- Expand/collapse ingest panel
- Change browser dropdown, verify command updates
- Copy ingest command to clipboard (verify clipboard content contains curl)

**Conflict review (end-to-end ingest flow):**
- Seed a preview by calling `POST /api/bookmarks/ingest` via Playwright's `request.post()` API with a JSON body containing test bookmarks. Capture the `previewId` from the response.
- Navigate to `/bookmarks?ingest=<previewId>`, verify conflict review dialog opens
- Verify items grouped by status (NEW, MOVED, etc.)
- Click "Accept All" on NEW group, verify all toggled
- Click "Commit", verify dialog closes and bookmarks appear

**Export:**
- Click export button, verify file download triggers

- [ ] **Step 2: Run E2E tests**

Run: `cd client && npm run e2e`
Expected: All pass

- [ ] **Step 3: Commit**

```
git add client/e2e/bookmarks.spec.ts
git commit -m "test: Playwright E2E tests for bookmark management"
```

---

### Task 20: Documentation and Roadmap Updates

**Files:**
- Modify: `docs/plans/2026-03-05-tooling-first-design.md` (mark Phase 6 complete when done)
- Modify: `CLAUDE.md` (if any new commands or conventions)

- [ ] **Step 1: Update master roadmap**

Mark Phase 6 status as complete in the master design doc.

- [ ] **Step 2: Update CLAUDE.md**

Add to the Backend package structure: `bookmark/entity/Folder.kt`, `bookmark/service/IngestService.kt`, `bookmark/controller/IngestController.kt`

Add to Frontend component structure note: the bookmarks feature now has sub-components (bookmark-tree, bookmark-list, ingest-panel, conflict-review)

- [ ] **Step 3: Update MEMORY.md**

Add a "Phase 6 Completed (Bookmark Management)" section with key technical facts:
- Folder entity: adjacency list (parentId), cycle detection via ancestor walk
- IngestService: URL normalization (lowercase scheme/host, strip www/trailing slash, sort query params)
- Ingest flow: CLI POST → server stores preview with ID → Angular fetches by ID → conflict review → commit
- REST endpoints: POST/GET/POST at `/api/bookmarks/ingest[/{previewId}[/commit]]`
- V5 migration: folders table, bookmark folder_id/sort_order/normalized_url columns, ingest_previews table
- CLI command generation: fully client-side (token from AuthService, URL from window.location.origin)
- ObjectMapper: inject Spring-configured bean, don't instantiate inline (Spring Boot 4.x uses `tools.jackson.databind.ObjectMapper`)

- [ ] **Step 4: Run full test suite**

Run: `./scripts/test-all.sh`
Expected: All tests pass across all services

- [ ] **Step 5: Commit**

```
git add docs/plans/2026-03-05-tooling-first-design.md CLAUDE.md
git commit -m "docs: Phase 6 Bookmark Management complete"
```

---

## Summary

| Task | Description                                   | Key Files                                                                                                                               |
|------|-----------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| 1    | Flyway V5 Migration                           | `V5__folders_and_ingest.sql` — folders table, bookmark columns (folder_id, sort_order, normalized_url), ingest_previews table           |
| 2    | Folder Entity and Repository                  | `Folder.kt`, `FolderRepository.kt`, `Bookmark.kt` (add folderId/sortOrder/normalizedUrl), `BookmarkRepository.kt` (folder-aware queries) |
| 3    | Folder CRUD with Cycle Detection              | `BookmarkService.kt` (createFolder, renameFolder, moveFolder, deleteFolder, getSubtree), `FolderOperationsTest.kt`                      |
| 4    | Bookmark-Folder Operations and Export Update  | `BookmarkService.kt` (moveBookmark, reorderBookmarks, listByFolder, exportBookmarks with folders), `BookmarkServiceTest.kt`             |
| 5    | IngestService — URL Normalization and Preview  | `IngestService.kt`, `IngestPreviewEntity.kt`, `IngestPreviewRepository.kt`, `IngestPreview.kt` (DTO)                                    |
| 6    | IngestService — Commit Resolutions            | `IngestService.kt` (commitResolutions, applyResolution), `IngestServiceTest.kt`                                                         |
| 7    | REST Controllers for Ingest                   | `IngestController.kt` (POST upload, GET preview, POST commit), `IngestControllerTest.kt`                                                |
| 8    | GraphQL Schema and Resolver Updates           | `schema.graphqls`, `FolderResolver.kt`, `BookmarkResolver.kt` (folder-aware queries)                                                    |
| 9    | MCP Tool Updates                              | `BookmarkTools.kt` (folder params on addBookmark/listBookmarks, createFolder, moveBookmark, importBookmarks)                            |
| 10   | Backend Integration Tests                     | `FolderIntegrationTest.kt`, `BookmarkIntegrationTest.kt` (folder operations, ingest flow, cycle detection)                              |
| 11   | Refactor Existing BookmarksComponent          | `client/src/app/bookmarks/bookmarks.ts` (remove CommonModule, use @if/@for, drop subscribe)                                             |
| 12   | Update Bookmark Store and GraphQL             | `bookmarks.graphql`, `bookmarks.store.ts`, `generated.ts` (folder queries, folder state)                                                |
| 13   | Folder Tree Component                         | `bookmark-tree/bookmark-tree.ts`, `bookmark-tree.spec.ts` (recursive tree, drag-drop, context menu)                                     |
| 14   | Bookmark List Component                       | `bookmark-list/bookmark-list.ts`, `bookmark-list.spec.ts` (folder-scoped list, sort, drag reorder)                                      |
| 15   | Ingest Panel Component                        | `ingest-panel/ingest-panel.ts`, `ingest-panel.spec.ts` (file upload, CLI command display, preview trigger)                              |
| 16   | Conflict Review Component                     | `conflict-review/conflict-review.ts`, `conflict-review.spec.ts` (diff view, resolution actions, commit)                                 |
| 17   | Wire Up Two-Panel Layout                      | `bookmarks.ts` (two-panel: folder tree left, bookmark list right, ingest/conflict overlays)                                             |
| 18   | Barrel Exports and Route Updates              | `bookmarks/index.ts`, sub-component `index.ts` files, `bookmarks.routes.ts`                                                             |
| 19   | Playwright E2E Tests                          | `client/e2e/bookmarks.spec.ts` (folder CRUD, drag-drop, ingest flow, export)                                                            |
| 20   | Documentation and Roadmap Updates             | `docs/plans/2026-03-05-tooling-first-design.md`, `CLAUDE.md`, `MEMORY.md`                                                               |


