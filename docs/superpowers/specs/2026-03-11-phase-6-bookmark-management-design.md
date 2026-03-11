# Phase 6: Bookmark Management — Design

**Date**: 2026-03-11
**Status**: Approved

## Overview

Phase 6 upgrades the bookmark system from a flat, manually-managed list to a full bookmark manager with folder hierarchy, automated browser ingestion, conflict resolution, and two-way portability. The user can import bookmarks from any major browser via a CLI command generated in the UI, review conflicts, and export back to standard HTML for browser import.

## Decisions

- **Folders, not tags-as-folders** — Real folder hierarchy via adjacency list (parentId). Tags remain cross-cutting across bookmarks/feeds/videos.
- **Adjacency list over materialized path** — Simpler to implement, sufficient for bookmark-scale trees (hundreds/low thousands of nodes).
- **Merge-with-review conflict resolution** — Ingest returns a preview diff; user resolves conflicts before committing. No silent overwrites or auto-merge.
- **CLI ingestion, no browser extension** — Zero-install friction. UI presents a ready-to-run command. Browser extension planned for future phase.
- **Soft-delete awareness** — Previously deleted bookmarks flagged as conflicts during ingest, not silently resurrected.
- **Command generation is fully client-side** — Angular already holds the JWT token; API URL derived from `window.location.origin`. No backend endpoint needed for command generation. Browser/OS detection and command templating happen entirely in Angular.
- **Single BookmarkService** — Folder operations absorbed into BookmarkService. Folders are part of the bookmark domain, not a separate domain.
- **IngestService separate** — Ingestion/parsing is a distinct concern from bookmark CRUD.
- **Ingest preview stored server-side** — CLI POSTs to REST, server stores the preview with an ID in the database. Angular fetches the preview by ID for conflict review. This bridges the CLI-to-UI handoff.
- **URL normalization for dedup** — Ingest matching normalizes URLs (lowercase scheme/host, strip trailing slash, sort query params, strip `www.` prefix) before comparison. No separate hash column needed.
- **Existing component refactoring** — The current `BookmarksComponent` must be refactored to remove `CommonModule`, `*ngIf`/`*ngFor`, and `subscribe()` before building the new two-panel layout.

## Data Model

### New Entity: Folder

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID | PK, gen_random_uuid() |
| `name` | VARCHAR(255) | NOT NULL |
| `parent_id` | UUID | Nullable FK to folders.id (null = root) |
| `user_id` | UUID | FK to users.id |
| `sort_order` | INT | Position among siblings |
| `created_at` | TIMESTAMPTZ | NOT NULL |
| `updated_at` | TIMESTAMPTZ | NOT NULL |
| `deleted_at` | TIMESTAMPTZ | Nullable, soft delete |
| `version` | BIGINT | Optimistic locking |

### Bookmark Changes

| Column | Type | Notes |
|--------|------|-------|
| `folder_id` | UUID | Nullable FK to folders.id (null = unfiled) |
| `sort_order` | INT | Position within folder |
| `normalized_url` | VARCHAR(2048) | Normalized form of URL for ingest dedup (lowercase scheme/host, strip trailing slash, sort query params, strip `www.`) |

### Flyway Migration (V5)

Note: V4 already exists (`V4__auth_password.sql`).

- Create `folders` table with self-referential FK
- Add `folder_id`, `sort_order`, `normalized_url` to `bookmarks`
- FK constraint: `bookmarks.folder_id -> folders.id`
- Index on `normalized_url` for fast ingest dedup
- Add tsvector column + GIN index + trigger on `folders` (searchable by name)
- Backfill `normalized_url` for existing bookmarks

## Ingest API

### Flow

1. User runs CLI command in terminal -> browser bookmark data POSTed to MemoryVault
2. `POST /api/bookmarks/ingest` stores the preview server-side, returns `IngestPreview` with a `previewId` and categorized diff
3. CLI prints the `previewId` and a summary (e.g., "12 new, 3 moved, 2 previously deleted — review at https://your-memoryvault.com/bookmarks?ingest=<previewId>")
4. User opens the link (or navigates to Bookmarks page) — Angular fetches the preview by ID via `GET /api/bookmarks/ingest/{previewId}`
5. User reviews conflicts in the conflict-review UI
6. User commits resolutions via `POST /api/bookmarks/ingest/{previewId}/commit`
7. Preview record is marked as committed (prevents replay)

### Conflict Types

| Status | Meaning |
|--------|---------|
| `NEW` | Bookmark exists in browser but not in MemoryVault |
| `UNCHANGED` | Identical in both, no action needed |
| `MOVED` | Same bookmark, different folder |
| `TITLE_CHANGED` | Same URL, different title |
| `PREVIOUSLY_DELETED` | Bookmark was soft-deleted in MemoryVault but still in browser |

### IngestPreview Response

```json
{
  "items": [
    {
      "url": "https://example.com",
      "title": "Example",
      "status": "NEW",
      "existingBookmark": null,
      "suggestedFolder": { "id": "...", "name": "Tech" },
      "browserFolder": "Bookmarks Bar/Tech"
    }
  ],
  "summary": {
    "newCount": 12,
    "unchangedCount": 42,
    "movedCount": 3,
    "titleChangedCount": 1,
    "previouslyDeletedCount": 2
  }
}
```

### Commit Request

```json
{
  "resolutions": [
    { "url": "https://example.com", "action": "ACCEPT" },
    { "url": "https://deleted.com", "action": "SKIP" },
    { "url": "https://old-title.com", "action": "ACCEPT" }
  ]
}
```

Actions: `ACCEPT` (apply change), `SKIP` (ignore), `UNDELETE` (restore soft-deleted).

## CLI Scripts & Command Generation

### No Config Endpoint Needed

The Angular frontend already holds the JWT token (from auth) and knows the API URL (from `environment.ts`). The command is assembled entirely client-side using these values. No backend call required for command generation.

### Supported Browsers

| Browser | OS | Bookmark Source |
|---------|-----|----------------|
| Chrome | macOS | `~/Library/Application Support/Google/Chrome/Default/Bookmarks` (JSON) |
| Chrome | Linux | `~/.config/google-chrome/Default/Bookmarks` (JSON) |
| Chrome | Windows | `%LOCALAPPDATA%\Google\Chrome\User Data\Default\Bookmarks` (JSON) |
| Firefox | macOS | `~/Library/Application Support/Firefox/Profiles/*.default-release/places.sqlite` |
| Firefox | Linux | `~/.mozilla/firefox/*.default-release/places.sqlite` |
| Firefox | Windows | `%APPDATA%\Mozilla\Firefox\Profiles\*.default-release\places.sqlite` |
| Safari | macOS | `~/Library/Safari/Bookmarks.plist` (binary plist, parsed via `plutil`) |
| Opera | macOS | `~/Library/Application Support/com.operasoftware.Opera/Bookmarks` (JSON) |
| Opera | Linux | `~/.config/opera/Bookmarks` (JSON) |
| Opera | Windows | `%APPDATA%\Opera Software\Opera Stable\Bookmarks` (JSON) |
| Edge | macOS/Linux/Windows | Chromium JSON format, browser-specific paths |
| Brave | macOS/Linux/Windows | Chromium JSON format, browser-specific paths |

Chromium-based browsers (Chrome, Opera, Edge, Brave, Vivaldi, Arc) share the same JSON format — only the file path differs.

### UI Placement

Top of the Bookmarks page: a collapsible "Import / Export" panel (collapsed by default).

**Import side:**
- Browser dropdown — auto-selected from `navigator.userAgent`, overridable
- OS auto-detected, overridable
- Copyable code block with the generated command
- Brief instruction: "Run this in your terminal to send bookmarks to MemoryVault"

**Export side:**
- "Download" button — downloads `memoryvault-export.html`
- Collapsible "How to import into your browser" with per-browser instructions

### Generated Command (Chrome/macOS example)

```bash
curl -s -X POST https://your-memoryvault.com/api/bookmarks/ingest \
  -H "Authorization: Bearer eyJ..." \
  -H "Content-Type: application/json" \
  -d "$(python3 -c "
import json
with open('$HOME/Library/Application Support/Google/Chrome/Default/Bookmarks') as f:
    data = json.load(f)
# extract folders + bookmarks into ingest format
result = {'bookmarks': [], 'folders': []}
def walk(node, path=''):
    if node.get('type') == 'url':
        result['bookmarks'].append({'url': node['url'], 'title': node['name'], 'folder': path})
    elif node.get('type') == 'folder':
        folder_path = f'{path}/{node[\"name\"]}' if path else node['name']
        result['folders'].append(folder_path)
        for child in node.get('children', []):
            walk(child, folder_path)
for root in data.get('roots', {}).values():
    if isinstance(root, dict):
        walk(root)
print(json.dumps(result))
")"
```

Firefox variant uses `sqlite3` CLI (with a pre-check: if `sqlite3` is not in PATH, the command prints an error directing the user to Firefox's built-in "Export Bookmarks to HTML" and the HTML ingest fallback). Safari variant uses `plutil -convert json`.

## Bookmark Manager UI

### Layout

Two-panel design (responsive — stacks vertically on mobile):
- **Left panel:** Folder tree
- **Right panel:** Bookmark list for selected folder + detail/edit

### Folder Tree

- Root node: "All Bookmarks" (virtual, shows total count)
- Unfiled bookmarks under virtual "Unfiled" node at bottom
- Right-click context menu: New Folder, Rename, Delete (soft), Move
- Drag-and-drop: folders onto folders (reparent), bookmarks onto folders (move)
- Folder counts inline (e.g., "Tech (12)")

### Bookmark List (right panel)

- Sortable columns: title, URL, date added, date modified
- Multi-select with bulk actions: Move to Folder, Tag, Delete
- Inline edit for title; click URL to open in new tab
- Tags shown as chips, editable inline

### Conflict Review (post-ingest)

Modal/drawer overlay on the bookmark manager:
- Grouped by conflict type: NEW, MOVED, TITLE_CHANGED, PREVIOUSLY_DELETED
- Each item: bookmark title/URL, what changed, resolution toggle (Accept / Skip / Undelete)
- "Accept All" / "Skip All" bulk buttons per group
- "Commit" button sends resolutions to commit endpoint
- UNCHANGED items hidden by default (expandable summary: "42 bookmarks unchanged")

### Component Structure

```
bookmarks/
  bookmark-tree/          # folder tree component
  bookmark-list/          # right-panel list
  bookmark-detail/        # edit/view single bookmark
  ingest-panel/           # collapsible import/export panel
  conflict-review/        # post-ingest conflict resolution modal
  bookmark-store.ts       # NgRx Signal Store (single store with selectedFolderId signal driving bookmark list)
  bookmarks.routes.ts
```

**Store design:** Single `BookmarkStore` with `selectedFolderId` signal. The bookmark list is a `computed()` that filters by the selected folder. Ingest preview state (items, summary, resolutions) lives in the same store since it's transient and tightly coupled to the bookmark view. If the store grows unwieldy during implementation, extract `IngestStore` as a separate concern.

## GraphQL Schema

### Types

```graphql
type Folder {
  id: ID!
  name: String!
  parent: Folder
  children: [Folder!]!       # Use @BatchMapping to avoid N+1
  bookmarks: [Bookmark!]!    # Use @BatchMapping to avoid N+1
  bookmarkCount: Int!
  sortOrder: Int!
}

type Bookmark {
  # existing fields...
  folder: Folder
  sortOrder: Int!
}

type IngestPreview {
  previewId: ID!
  items: [IngestItem!]!
  summary: IngestSummary!
}

type IngestItem {
  url: String!
  title: String!
  status: IngestStatus!
  existingBookmark: Bookmark
  suggestedFolder: Folder
  browserFolder: String
}

enum IngestStatus { NEW UNCHANGED MOVED TITLE_CHANGED PREVIOUSLY_DELETED }

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
```

### Queries

```graphql
folders: [Folder!]!                    # Returns flat list; client assembles tree
folder(id: ID!): Folder
exportBookmarks: String!               # Moved from Mutation to Query (it's a read operation)
```

Note: `exportBookmarks` was previously a Mutation in earlier phases. Moving it to Query is a breaking change — the Angular `BookmarksStore` must be updated accordingly.

### Mutations

```graphql
createFolder(name: String!, parentId: ID): Folder!
renameFolder(id: ID!, name: String!): Folder!
moveFolder(id: ID!, newParentId: ID): Folder!
deleteFolder(id: ID!): Boolean!          # Soft-delete; children reparented to deleted folder's parent

moveBookmark(id: ID!, folderId: ID): Bookmark!
reorderBookmarks(folderId: ID, bookmarkIds: [ID!]!): [Bookmark!]!  # bookmarkIds is the COMPLETE ordered list for the folder (replace-all)

ingestBookmarks(input: IngestInput!): IngestPreview!     # Used by Angular UI if direct upload is added later
commitIngest(previewId: ID!, resolutions: [IngestResolution!]!): CommitResult!
```

### REST Endpoints (for CLI and Angular)

The ingest flow uses REST throughout (CLI POSTs bookmark data, Angular fetches the preview and commits resolutions). GraphQL mutations exist for future use (e.g., direct file upload from the browser) but the primary flow is REST-based.

- `POST /api/bookmarks/ingest` — accepts raw browser bookmark data, stores preview, returns IngestPreview with `previewId`
- `GET /api/bookmarks/ingest/{previewId}` — retrieves stored preview for Angular conflict-review UI
- `POST /api/bookmarks/ingest/{previewId}/commit` — applies user's conflict resolutions

## Export

- **Format:** Netscape Bookmark File (HTML) — universal browser import standard
- `BookmarkService.exportBookmarks()` walks folder tree recursively, emits nested `<DT><H3>` / `<DL>` / `<DT><A>` structure
- Unfiled bookmarks under top-level "Unfiled" folder in export
- Tags not included in HTML export (no browser supports them)
- MCP tool `exportBookmarks` updated to include folder structure

### Browser Import Instructions (shown in UI)

- **Chrome:** `chrome://bookmarks` -> three-dot menu -> Import bookmarks -> select file
- **Firefox:** `Ctrl+Shift+O` -> Import and Backup -> Import Bookmarks from HTML
- **Safari:** File -> Import From -> Bookmarks HTML File
- **Opera:** `opera://bookmarks` -> Import bookmarks -> select file
- **Edge:** `edge://favorites` -> three-dot menu -> Import favorites -> select file

## Service Layer

| Service | Responsibility |
|---------|---------------|
| `BookmarkService` | Bookmark CRUD, folder CRUD, tree traversal, cycle detection on `moveFolder` (recursive CTE ancestor walk — returns GraphQL error "Cannot move a folder into its own descendant"), reordering, export |
| `IngestService` | Parse browser formats (Chrome JSON, Firefox SQLite, Safari plist), URL normalization, diff against existing bookmarks, generate and store preview, apply committed resolutions |

## Future Work

- **Browser extension** — Two-way sync between MemoryVault and browser bookmarks without manual CLI/import steps. Planned for a later phase.
- **Configurable conflict resolution** — Allow users to choose between merge-with-review (current), auto-accept-all, or manual-only modes.
- **Scheduled ingest** — Cron-based automatic ingestion if the user's bookmark file is accessible (e.g., on the same machine as the server).
