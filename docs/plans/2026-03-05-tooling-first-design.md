# MemoryVault — Tooling-First Design

**Date**: 2026-03-05
**Status**: Approved

## Overview

MemoryVault is a self-hosted content archival and aggregation platform — a personal RSS reader, bookmark manager, and YouTube archiver with AI interaction via MCP, a web UI, automated AWS sync jobs, and observability built in from the start. The goal is a system good enough to replace theoldreader.com, with enough polish and generality that others could use it too.

**Design strategy**: MCP tools are defined first. They serve as the living specification for the backend data model and API. Everything else is built to fulfill what those tools require.

---

## Approach: MCP Tools as the API Spec

Rather than designing the backend first and wiring AI on top, the MCP tool suite defines what the system can do. Each tool maps directly to a backend endpoint. The data model is derived from what those tools need to query and return.

---

## MCP Tool Suite (the spec)

### RSS / Feeds
- `add_feed(url)` — subscribe to an RSS feed
- `list_feeds()` — list subscribed feeds with unread counts
- `get_feed_items(feedId, limit, unreadOnly)` — browse items from a feed
- `mark_read(itemId | feedId)` — mark one item or all items in a feed as read
- `mark_unread(itemId)` — mark an item as unread
- `refresh_feed(feedId?)` — trigger sync; all feeds if feedId omitted

### Bookmarks
- `add_bookmark(url, title?, tags?)` — save a URL
- `list_bookmarks(query?, tags?)` — search and filter bookmarks
- `tag_bookmark(id, tags)` — update tags on a bookmark
- `delete_bookmark(id)` — soft-delete a bookmark
- `export_bookmarks(format?)` — export as Netscape HTML format for browser import

### YouTube Archival
- `add_youtube_list(url)` — subscribe a YouTube playlist for archival; immediately fetches metadata and queues all videos for download
- `list_youtube_lists()` — list tracked playlists with video counts, download progress, and removed count
- `list_archived_videos(listId?, query?, removedOnly?)` — browse the video archive; `removedOnly` filter surfaces videos no longer on YouTube
- `get_video_status(videoId)` — check download status, file path, and removal status for a single video
- `refresh_youtube_list(listId?)` — re-sync metadata, detect removals, queue new videos for download; all lists if listId omitted
- `delete_youtube_list(listId)` — soft-delete a tracked playlist

### Cross-cutting
- `search(query, types?)` — PostgreSQL full-text search across bookmarks, feed items, and videos with ranked results
- `getStats()` — content counts, storage used, last sync times, failure counts
- `listJobs(type?, limit?)` — view sync job execution history with status and metadata
- `getLogs(level?, service?, limit?)` — retrieve structured JSON logs from local file or CloudWatch (Phase 6)
- `get_aws_cost(billingCycle?)` — compute, storage, transfer costs per billing cycle (Phase 6)

---

## Claude Code Skills

### Install from skills.sh
These cover ground that doesn't need custom work:

| Skill | Source | Purpose |
|---|---|---|
| `mcp-builder` | `anthropics/skills` | MCP server design patterns |
| `kotlin-springboot` | `github/awesome-copilot` | Kotlin + Spring Boot conventions |
| `python-testing-patterns` | `wshobson/agents` | Python content processor testing |
| `async-python-patterns` | `wshobson/agents` | Async patterns for content processor |
| `pytest-coverage` | `github/awesome-copilot` | pytest for content processor |
| `webapp-testing` | `anthropics/skills` | End-to-end testing guidance |

### Custom skills to build (Phase 0)
These are project-specific and have no off-the-shelf equivalent:

- `/scaffold-entity <name>` — generates Kotlin JPA entity + Spring Data repository + service + REST controller following project package structure
- `/add-mcp-tool <name>` — adds a Spring AI `@Tool`-annotated method, wires it to the service layer, adds a stub test
- `/add-lambda <name>` — scaffolds an AWS Lambda function (Kotlin or Python) with Terraform resource block and local test harness
- `/add-content-processor <name>` — scaffolds a Python content processor module with standard interface and pytest stub

---

## Architecture

```
Claude (conversational)  →  MCP tools    →  Service layer  →  DB / S3
Angular UI               →  GraphQL      →  Service layer  →  DB / S3
External clients         →  REST API     →  Service layer  →  DB / S3
```

**Key decisions:**

- **MCP server runs inside Spring Boot** — `spring-ai-starter-mcp-server` handles the MCP wire protocol. Methods annotated with `@Tool` are automatically registered as MCP tools with JSON schemas derived from Kotlin types. No separate process, no TypeScript MCP server.
- **One service layer, three access paths** — MCP tools, GraphQL resolvers, and REST controllers all call the same service layer. No duplicated business logic.
- **GraphQL for the web UI** — Angular frontend communicates via GraphQL. The `/graphiql` playground is available in dev for ad-hoc queries.
- **Lambda for async jobs** — RSS sync, yt-dlp downloads, link health checks, and bookmark archival run as AWS Lambda functions triggered by EventBridge schedules. They write results to PostgreSQL and S3. Spring Boot reads those results.
- **Spring AI 2.0-M2** — milestone release, acceptable for a personal/portfolio project.

---

## Data Model

All primary keys are UUIDs. User-owned content uses soft deletes (`deletedAt`). Mutable entities carry a `version` field for optimistic locking.

### User
```
id (UUID), email, passwordHash, displayName
role (OWNER | ADMIN | VIEWER)
createdAt, updatedAt, deletedAt, version
```

### Tag
```
id (UUID), userId, name, color (nullable)
createdAt
```
Tags are per-user and shared across Bookmarks, FeedItems, and Videos via many-to-many join tables. `color` is for UI display; nullable and removable without impact if unused.

### Bookmark
```
id (UUID), userId, url, title
archivedAt, archivedHtmlPath (S3 key), screenshotPath (S3 key)
createdAt, updatedAt, deletedAt, version
tags → Tag[] (many-to-many via BookmarkTag)
```

### Feed
```
id (UUID), userId, url, title, description, siteUrl
lastFetchedAt, fetchIntervalMinutes, failureCount
createdAt, updatedAt, deletedAt, version
```

### FeedItem
```
id (UUID), feedId, guid (RSS guid for deduplication)
title, url, content, author, imageUrl
publishedAt, readAt (null = unread)
createdAt
tags → Tag[] (many-to-many via FeedItemTag)
```

### YoutubeList
```
id (UUID), userId, youtubeListId (external ID), url, name, description
lastSyncedAt, failureCount
createdAt, updatedAt, deletedAt, version
```

### Video
```
id (UUID), youtubeListId, youtubeVideoId (external ID)
title, description, channelName, thumbnailPath (S3 key)
youtubeUrl (canonical YouTube link)
filePath (S3 key, null until downloaded), downloadedAt, durationSeconds
removedFromYoutube (boolean), removedDetectedAt
createdAt, updatedAt
tags → Tag[] (many-to-many via VideoTag)
```

### SyncJob
```
id (UUID), userId
type (FEED_SYNC | YOUTUBE_SYNC | BOOKMARK_ARCHIVE)
status (PENDING | RUNNING | SUCCESS | FAILED)
startedAt, completedAt, errorMessage
triggeredBy (SCHEDULED | MANUAL)
metadata (JSONB — flexible payload per job type)
```

### AwsCostRecord
```
id (UUID), billingCycleStart, billingCycleEnd
computeCostUsd, storageCostUsd, transferCostUsd, totalCostUsd
fetchedAt
```

---

## Testing Strategy

Testing is first-class, not an afterthought. Every phase ships with scripts that exercise the running system.

### Test layers
- **Unit tests** — JUnit 5 + MockK for services and MCP tools
- **Integration tests** — TestContainers with real PostgreSQL; tests the full service → DB round trip
- **MCP tool tests** — invoke each `@Tool` method directly and assert response shape
- **Smoke tests** — `scripts/smoke-test.sh` hits a running instance (local or AWS) and verifies every endpoint
- **Python tests** — pytest + coverage for the content processor

### Scripts layout
```
scripts/
├── test-all.sh              # runs all test layers
├── test-bookmarks.sh        # full bookmark vertical slice
├── test-feeds.sh            # full RSS vertical slice
├── test-youtube.sh          # full YouTube archival vertical slice
├── smoke-test.sh            # quick sanity check against any running instance
├── logs.sh                  # tail and filter local logs by service/level
└── reset-db.sh              # wipe and reseed local dev database
```

### Logging
- Structured JSON logs via Logback — greppable locally, CloudWatch-compatible in production
- Log correlation IDs on every request — trace a single MCP tool call through the full stack
- `SyncJob` records provide a persistent audit trail for all Lambda runs
- `scripts/logs.sh` mirrors the `get_logs` MCP tool for terminal use

---

## Development Sequence

### Phase 0 — Tooling
- 4 custom Claude Code skills
- Spring AI MCP server skeleton (transport working, no tools yet)
- Docker Compose for local dev (PostgreSQL, Spring Boot app)

### Phase 1 — Bookmarks
Simplest domain. No async jobs, no external APIs. Validates the full stack: entity → service → MCP tool → talking to Claude about your bookmarks.

### Phase 2 — RSS / Feeds
The theoldreader replacement. Introduces the Lambda pattern for scheduled fetching. Validates the async job architecture.

### Phase 3 — YouTube Archival
Most complex: yt-dlp, S3 storage, long-running downloads. Built on Lambda patterns proven in Phase 2.

### Phase 4 — Cross-cutting
PostgreSQL full-text search, system stats, job history tracking with SyncJob entity, structured logging with local file reader and CloudWatch stub. AWS cost tracking deferred to Phase 6.

### Phase 5 — Web UI + Auth
JWT authentication (jjwt, BCrypt), Spring for GraphQL (schema-first), Angular 21 frontend (zoneless, Angular Material, NgRx Signal Store, Apollo Angular, graphql-codegen). Pages: login, feed reader, bookmarks, YouTube archive, admin (jobs/logs/stats), global search. Auth interceptor handles token injection and redirects to login on 401/403.

### Phase 6 — Bookmark Management
Folder hierarchy (adjacency list), full bookmark manager UI (two-panel tree + list), browser bookmark ingestion via CLI commands generated in the UI (Chrome, Firefox, Safari, Opera, Edge, Brave), conflict resolution with preview/commit flow, Netscape HTML export with folder structure. See `docs/superpowers/specs/2026-03-11-phase-6-bookmark-management-design.md`.

### Phase 7 — Mirror OldReader functionality
Import feeds. Export feeds. Support (or stubs for) OAuth from social media logins. Supporting 3rd party integrations (unclear about what all these are). Other (need to research)?

### Phase 8 — Real-Time Updates
WebSocket support for live UI updates — new feed items, sync job progress, download status changes pushed to the Angular client without polling.

### Phase 9 — Infrastructure
Terraform, GitHub Actions CI/CD, production AWS deployment (EC2, RDS, S3, Lambda, EventBridge). AWS Cognito auth swap (CurrentUser abstraction is already in place), CloudWatch log retrieval (LocalLogService/CloudWatchLogService interface is stubbed), AWS cost tracking (AwsCostRecord entity defined but not implemented).

---

## Notes

- **Browser extension for two-way bookmark sync** — push bookmarks directly into browser without manual HTML import. Deferred from Phase 6.
- **Configurable conflict resolution modes** — allow users to choose between merge-with-review, auto-accept-all, or manual-only during bookmark ingest. Deferred from Phase 6.
- **Scheduled bookmark ingest** — cron-based automatic ingestion if the user's bookmark file is accessible on the same machine. Deferred from Phase 6.
- `color` on `Tag` is nullable; can be dropped in a migration if a better theming approach is chosen later.
- Multi-tenancy (`userId` foreign keys) is in from the start. SaaS path remains open if the web UI proves compelling enough.
