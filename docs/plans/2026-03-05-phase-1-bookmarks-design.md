# Phase 1: Bookmarks — Design

**Date**: 2026-03-05
**Status**: Approved

## Overview

Phase 1 validates the full MemoryVault stack: JPA entity → service → MCP tool → Claude interaction. Bookmarks are the simplest domain — no async jobs, no external APIs, no cloud dependencies.

## Decisions

- **Archival deferred** — `archivedAt`, `archivedHtmlPath`, `screenshotPath` omitted from Bookmark. Requires S3 + content processor + Lambda; doesn't belong in Phase 1.
- **Export included** — `exportBookmarks` MCP tool ships in Phase 1. Low overhead (~30 lines), and bookmarks is the natural phase for it.
- **Seed user** — a hardcoded system user UUID in the migration. All Phase 1 operations use this user. Auth comes in Phase 5.
- **Full schema upfront** — V2 migration creates all tables from the design doc (users, tags, bookmarks, feeds, feed_items, youtube_lists, videos, all join tables). Only Bookmark and Tag get Kotlin entities in Phase 1.
- **Tag join tables upfront** — `bookmark_tags`, `feed_item_tags`, `video_tags` all created in V2 with proper FK constraints. Avoids migration coordination in later phases.

## Database Migration (V2)

Single migration creates the full data model:

### Tables

| Table            | Notes                                            |
|------------------|--------------------------------------------------|
| `users`          | Seed "system" user inserted                      |
| `tags`           | `user_id` FK, `name`, `color` (nullable)         |
| `bookmarks`      | `user_id` FK, `url`, `title`, no archival fields |
| `bookmark_tags`  | Composite PK, FKs to bookmarks + tags            |
| `feeds`          | Full schema from design doc                      |
| `feed_items`     | `read_at` nullable timestamp                     |
| `feed_item_tags` | Composite PK, FKs to feed_items + tags           |
| `youtube_lists`  | Full schema from design doc                      |
| `videos`         | `removed_from_youtube` boolean                   |
| `video_tags`     | Composite PK, FKs to videos + tags               |

All PKs are `UUID DEFAULT gen_random_uuid()`. Soft-deletable entities have `deleted_at TIMESTAMPTZ` and `version BIGINT`.

## Kotlin Entities (Phase 1 only)

### Bookmark

```
bookmark/entity/Bookmark.kt
- id: UUID
- userId: UUID
- url: String
- title: String
- createdAt, updatedAt, deletedAt, version
- tags: MutableSet<Tag> (@ManyToMany via bookmark_tags)
```

### Tag

```
tag/entity/Tag.kt
- id: UUID
- userId: UUID
- name: String
- color: String? (nullable)
- createdAt
```

Unidirectional relationship: Bookmark → Tag. Tag has no back-reference.

## Package Structure

```
src/main/kotlin/org/sightech/memoryvault/
├── bookmark/
│   ├── entity/     Bookmark.kt
│   ├── repository/ BookmarkRepository.kt
│   ├── service/    BookmarkService.kt
│   └── controller/ BookmarkController.kt
├── tag/
│   ├── entity/     Tag.kt
│   ├── repository/ TagRepository.kt
│   └── service/    TagService.kt
├── mcp/
│   └── BookmarkTools.kt
```

## Service Layer

### BookmarkService

- `create(url, title?, tagNames?)` — creates bookmark, creates-or-finds tags by name
- `findAll(query?, tagNames?)` — returns active bookmarks, filtered by title/URL substring and/or tag names
- `updateTags(bookmarkId, tagNames)` — replaces tags on a bookmark
- `softDelete(bookmarkId)` — sets `deletedAt`
- `exportNetscapeHtml()` — returns all active bookmarks as Netscape HTML string

### TagService

- `findOrCreateByName(name)` — idempotent
- `findOrCreateByNames(names)` — batch version

## MCP Tools

### BookmarkTools.kt

| Tool              | Signature                                                       | Description                             |
|-------------------|-----------------------------------------------------------------|-----------------------------------------|
| `addBookmark`     | `(url: String, title: String?, tags: List<String>?)` → `String` | Save a URL with optional title and tags |
| `listBookmarks`   | `(query: String?, tags: List<String>?)` → `String`              | Search/filter by text or tags           |
| `tagBookmark`     | `(bookmarkId: String, tags: List<String>)` → `String`           | Replace tags on a bookmark              |
| `deleteBookmark`  | `(bookmarkId: String)` → `String`                               | Soft-delete a bookmark                  |
| `exportBookmarks` | `(format: String?)` → `String`                                  | Export as Netscape HTML                 |

All tools return formatted strings for Claude readability. Tools accept `String` for IDs (parsed to UUID internally).

## Testing

- **Unit**: BookmarkServiceTest, TagServiceTest, BookmarkToolsTest (MockK)
- **Integration**: BookmarkIntegrationTest (TestContainers — service → DB round trip)
- **Script**: `scripts/test-bookmarks.sh`

## What's Deferred

- Bookmark archival (S3, content processor, Lambda)
- REST controller beyond stub (list + get) — full API in Phase 5
- Authentication — hardcoded seed user until Phase 5
- Export formats beyond Netscape HTML
