# Phase 7 — Mirror OldReader Functionality

**Date**: 2026-03-16
**Status**: Draft
**Branch**: `phase-7-mirror-oldreader` (branched from `phase-6-bookmark-management`)

---

## Overview

Phase 7 makes the feed reader feel like a complete RSS reader by adding the features that TheOldReader users rely on: feed categories, OPML import/export, full feed management in the UI, and reader view enhancements (list/full view, sort order, scroll-mark-as-read). Starred articles, API keys, and OAuth are stubbed for future work.

---

## Must-Haves

### 1. Feed Categories

Single-level categories for organizing feeds. No nested hierarchy.

**New entity: `FeedCategory`**
```
id (UUID)
userId (UUID, FK to users)
name (String)
sortOrder (Int)
createdAt (Instant)
updatedAt (Instant)
deletedAt (Instant, nullable — soft delete)
version (Long — optimistic locking)
```

**Changes to `Feed`:**
- Add `categoryId (UUID, non-nullable, FK to FeedCategory)` — every feed belongs to a category

**"Subscribed" default category:**
- A real `FeedCategory` row, created per user (seeded for existing users in migration)
- `sortOrder = 0` — pinned at the top
- New feeds default to the "Subscribed" category
- Hidden in the sidebar when it contains zero feeds
- The "Subscribed" category cannot be deleted or renamed
- Deleting a user-created category moves its feeds to "Subscribed"

### 2. OPML Import/Export

**Export:**
- GraphQL query: `exportFeeds: String!` — returns OPML 2.0 XML
- MCP tool: `exportFeeds()` — same output
- Feeds grouped by category name in `<outline>` elements

**Import:**
- REST endpoint: `POST /api/feeds/import` — accepts OPML file upload
- GraphQL mutation: `importFeeds(opml: String!): ImportResult!` — accepts OPML content as string (Angular reads file client-side)
- MCP tool: `importFeeds(opmlContent)` — same behavior
- Parsing: JDK built-in XML parsing (`javax.xml.parsers` / `jakarta.xml.parsers` depending on Jakarta migration status)
- Category matching: find existing category by name (case-insensitive) or create new one
- Feed dedup: skip if URL already subscribed
- Returns: `{ feedsAdded: Int, feedsSkipped: Int, categoriesCreated: Int }`
- No preview/commit flow — immediate execution

### 3. Feed Management UI

**Sidebar (category-aware):**
- "All Items" entry at the top — shows merged timeline of every feed
- Categories listed below in `sortOrder`, each expandable to show feeds
- "Subscribed" shown only when it has feeds
- Each category shows aggregate unread count (sum of its feeds' unread counts)
- Clicking a category shows merged timeline of all feeds in that category
- Clicking a feed shows that feed's items (current behavior)

**Feed management actions:**
- Add feed: dialog with URL input + category picker (defaults to "Subscribed")
- Delete feed: context menu or icon on feed in sidebar
- Move feed to category: context menu with category picker

**Category management:**
- Add category: button/dialog with name input
- Rename category: inline edit or dialog
- Delete category: moves feeds to "Subscribed", then deletes
- Reorder categories: drag-and-drop in sidebar

### 4. Reader Enhancements

**Global user preferences (new columns on `users` table):**
- `view_mode`: `LIST` | `FULL` (default: `LIST`)
- `sort_order`: `NEWEST_FIRST` | `OLDEST_FIRST` (default: `NEWEST_FIRST`)

**List View:**
- Compact rows: feed name, article title, date — one line per article
- Click to expand inline (existing `mat-expansion-panel` behavior)

**Full View:**
- Each article rendered with full content visible, stacked vertically
- Scrollable feed, no click to expand

**Sort order:**
- Newest first (reverse chronological by `publishedAt`) or oldest first
- User-toggleable via toolbar controls

**Toggle location:** toolbar above the article list — icon buttons for list/full view and sort direction.

**Applies uniformly to:** single feed, category selection, and "All Items" view.

### 5. Scroll-Mark-As-Read

- When an article scrolls past the viewport (leaves upward), automatically marked as read via `markItemRead` mutation
- Uses `IntersectionObserver` for efficiency (no scroll event listeners)
- Manual override: per-article read/unread toggle button (can mark something back to unread after auto-read)

### 6. Bulk Mark-As-Read

- Mark feed as read: button per feed (existing `markFeedRead` mutation)
- Mark category as read: button per category header — new `markCategoryRead(categoryId)` GraphQL mutation and MCP tool that marks all items in all feeds in the category as read in a single transaction

---

## Stubs (Nice-to-Haves)

### Starred Articles

- `starredAt (Instant, nullable)` column on `feed_items` table — in V6 migration, functional
- `FeedItemService.starItem(itemId)` / `unstarItem(itemId)` — working methods, commented out with TODO
- GraphQL mutations `starItem` / `unstarItem` — defined in schema, resolvers commented out with TODO
- MCP tools `starItem` / `unstarItem` — commented out with TODO
- UI: star icon on articles — commented out in template with TODO
- "Starred" view in sidebar — commented out with TODO
- Tests: stubbed test files with TODO placeholders, no assertions

### API Keys

- `api_keys` table in V6 migration:
  ```
  id (UUID), userId (UUID, FK), name (String), keyHash (String),
  lastUsedAt (Instant, nullable), createdAt (Instant), deletedAt (Instant, nullable), version (Long)
  ```
- `ApiKey` entity — skeleton class, commented out with TODO
- `ApiKeyService` — skeleton with commented-out CRUD methods and TODO
- `ApiKeyController` — skeleton with commented-out endpoints and TODO
- No UI, no tests beyond stubs

### OAuth

- `user_auth_providers` table in V6 migration:
  ```
  id (UUID), userId (UUID, FK), provider (String, e.g. "google", "github"),
  externalId (String), accessToken (String, nullable), refreshToken (String, nullable),
  createdAt (Instant), updatedAt (Instant), deletedAt (Instant, nullable), version (Long)
  UNIQUE(provider, externalId)
  ```
- `UserAuthProvider` entity — skeleton class, commented out with TODO
- `OAuthService` — skeleton with commented-out methods: `linkProvider`, `unlinkProvider`, `findByProvider` with TODO
- Commented-out Spring Security OAuth2 client properties in `application.properties` with TODO noting where provider client IDs/secrets would go
- Design note: The existing `spring-boot-starter-oauth2-resource-server` dependency handles the resource server side. Full implementation would add `spring-boot-starter-oauth2-client`, configure providers in `SecurityConfig`, and wire account linking through `OAuthService`.

---

## Migration: V6

Single migration covering all schema changes:

```sql
-- Feed categories
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

-- Add categoryId to feeds (populate with user's "Subscribed" category)
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

---

## Backend Components

**New:**
- `FeedCategory` entity, repository, service
- `FeedCategoryService`: CRUD + reorder + "move feeds on delete"
- `OpmlService`: parse OPML XML, generate OPML XML
- `FeedCategoryResolver` (GraphQL)
- `FeedCategoryTools` (MCP)
- REST: `POST /api/feeds/import` on existing `FeedController` or new `FeedImportController`

**Modified:**
- `Feed` entity: add `categoryId` field + JPA relationship
- `FeedService`: `addFeed` now accepts optional `categoryId` (defaults to "Subscribed")
- `FeedItemService`: add sort direction parameter to `getItems`
- `User` entity: add `viewMode`, `sortOrder` fields
- `FeedResolver`: update queries to include category info, add preference mutations
- `FeedTools` (MCP): update `addFeed` to accept category, add `exportFeeds`/`importFeeds`

**Stubs (commented out):**
- `ApiKey` entity, `ApiKeyService`, `ApiKeyController`
- `UserAuthProvider` entity, `OAuthService`
- `FeedItemService.starItem/unstarItem`
- GraphQL starred mutations
- MCP starred tools

---

## Frontend Components

**Modified:**
- `reader/` — currently a single component (`reader.ts`, `reader.html`, `reader.store.ts`, `reader.graphql`, `reader.css`) with a flat feed list sidebar and expansion-panel article list. This gets refactored: the sidebar is extracted into `category-sidebar/`, the article area is split into `feed-list-view/` and `feed-full-view/`, and the store gains category/preference state. The existing `reader.ts` becomes a shell that composes the sub-components.

**New sub-components (inside `reader/`):**
- `category-sidebar/` — category tree with feeds, unread counts, management actions
- `feed-toolbar/` — view mode toggle, sort order toggle, mark-as-read button
- `feed-list-view/` — compact article list
- `feed-full-view/` — expanded article view
- `feed-management/` — dialogs for add feed, add/rename/delete category, move feed
- `opml-import/` — file upload dialog with import results summary

**New store state:**
- `categories: FeedCategory[]`
- `selectedCategoryId: string | null` (null = "All Items")
- `viewMode: 'LIST' | 'FULL'`
- `sortOrder: 'NEWEST_FIRST' | 'OLDEST_FIRST'`

**GraphQL additions:**
- Queries: `feedCategories`, `exportFeeds`
- Mutations: `addCategory`, `renameCategory`, `deleteCategory`, `reorderCategories`, `moveFeedToCategory`, `importFeeds`, `updateUserPreferences`

---

## MCP Tools

**New:**
- `addCategory(name)` — create a feed category
- `renameCategory(categoryId, name)` — rename a category
- `deleteCategory(categoryId)` — delete category, move feeds to "Subscribed"
- `listCategories()` — list all categories with feed counts
- `moveFeedToCategory(feedId, categoryId)` — move a feed
- `reorderCategories(categoryIds)` — set category sort order (list of IDs in desired order)
- `markCategoryRead(categoryId)` — mark all items in all feeds in the category as read
- `importFeeds(opmlContent)` — bulk import from OPML
- `exportFeeds()` — export subscriptions as OPML

**Modified:**
- `addFeed(url, categoryId?)` — now accepts optional category (defaults to "Subscribed")
- `getFeedItems(feedId, limit?, unreadOnly?, sortOrder?)` — add sort direction

---

## Testing

- Unit tests: `FeedCategoryServiceTest`, `OpmlServiceTest` (MockK)
- Integration tests: `FeedCategoryIntegrationTest`, OPML import/export round-trip (TestContainers)
- MCP tool tests: `FeedCategoryToolsTest`
- GraphQL resolver tests: `FeedCategoryResolverTest`
- Frontend: Vitest unit tests for new store methods and components
- E2E: Playwright tests for category management, OPML import, view toggles
- Stubs: empty test files with TODO for starred/API key features
