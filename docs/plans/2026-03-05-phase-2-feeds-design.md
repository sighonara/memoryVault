# Phase 2: RSS / Feeds — Design

**Date**: 2026-03-05
**Status**: Approved

## Overview

Phase 2 adds RSS feed subscription and reading via MCP tools. Introduces a scheduler abstraction that runs locally via Spring's TaskScheduler and can be swapped for AWS Lambda+EventBridge in production.

## Decisions

- **prof18/RSS-Parser** for RSS/Atom parsing — Kotlin-native, actively maintained, clean API
- **In-process feed fetching** with Spring `@Scheduled` — no Lambda/EventBridge in this phase
- **Scheduler abstraction** via `JobScheduler` interface — local Spring impl now, AWS impl later
- **Configurable cron** via `memoryvault.feeds.sync-cron` property, defaulting to `-` (disabled)
- **Two separate mark-read tools** — `markItemRead` and `markFeedRead` for clearer Claude intent
- **GUID fallback chain** — use `<guid>` if present, fall back to `<link>`, then hash of title+pubDate
- **New items only** — no update detection for existing items (deferred)
- **No SyncJob records yet** — table exists but not written to in this phase
- **No failure tracking** — `failureCount`/backoff deferred

## Database

No new migrations — `feeds`, `feed_items`, and `feed_item_tags` tables already exist from V2.

## Kotlin Entities

### Feed

```
feed/entity/Feed.kt
- id: UUID, userId: UUID, url: String, title: String?
- description: String?, siteUrl: String?
- lastFetchedAt: Instant?, fetchIntervalMinutes: Int
- failureCount: Int, createdAt, updatedAt, deletedAt, version
```

### FeedItem

```
feed/entity/FeedItem.kt
- id: UUID, feed: Feed (@ManyToOne)
- guid: String (dedup key — RSS guid, link, or hash fallback)
- title: String?, url: String?, content: String?
- author: String?, imageUrl: String?
- publishedAt: Instant?, readAt: Instant? (null = unread)
- createdAt: Instant
- tags: MutableSet<Tag> (@ManyToMany via feed_item_tags)
```

## Package Structure

```
src/main/kotlin/org/sightech/memoryvault/
├── feed/
│   ├── entity/     Feed.kt, FeedItem.kt
│   ├── repository/ FeedRepository.kt, FeedItemRepository.kt
│   ├── service/    FeedService.kt, FeedItemService.kt, RssFetchService.kt
│   └── controller/ FeedController.kt
├── scheduling/
│   ├── JobScheduler.kt (interface)
│   └── SpringJobScheduler.kt
├── mcp/
│   └── FeedTools.kt
```

## Service Layer

### FeedService
- `addFeed(url)` — subscribe, fetch title/metadata on first add
- `listFeeds()` — all active feeds with unread counts
- `deleteFeed(feedId)` — soft delete
- `refreshFeed(feedId?)` — fetch + parse + store new items; all feeds if null

### FeedItemService
- `getItems(feedId, limit?, unreadOnly?)` — paginated item listing
- `markItemRead(itemId)` — sets readAt to now
- `markItemUnread(itemId)` — sets readAt to null
- `markFeedRead(feedId)` — marks all items in feed as read, returns count

### RssFetchService
- `fetchAndStore(feed)` — uses prof18 RSS-Parser, deduplicates by guid/link/hash fallback, inserts new items
- Pure logic — called by both scheduler and MCP tool
- Tests use static XML fixtures, not live HTTP

## Scheduler Abstraction

```kotlin
interface JobScheduler {
    fun schedule(jobName: String, cron: String, task: () -> Unit)
    fun triggerNow(jobName: String)
}
```

**SpringJobScheduler** — reads `memoryvault.feeds.sync-cron`, uses Spring's `TaskScheduler`. Disabled by default.

```properties
# Feed sync schedule (cron syntax). Set to "-" to disable.
# Examples: "0 */30 * * * *" = every 30 min, "0 0 * * * *" = hourly
memoryvault.feeds.sync-cron=-
```

## MCP Tools

### FeedTools.kt — 7 tools

| Tool | Signature | Description |
|------|-----------|-------------|
| `addFeed` | `(url: String)` | Subscribe to an RSS feed |
| `listFeeds` | `()` | List feeds with unread counts |
| `getFeedItems` | `(feedId: String, limit: Int?, unreadOnly: Boolean?)` | Browse items from a feed |
| `markItemRead` | `(itemId: String)` | Mark one item as read |
| `markItemUnread` | `(itemId: String)` | Mark one item as unread |
| `markFeedRead` | `(feedId: String)` | Mark all items in a feed as read |
| `refreshFeed` | `(feedId: String?)` | Trigger feed sync |

## Testing

- Unit: FeedServiceTest, FeedItemServiceTest, RssFetchServiceTest, FeedToolsTest (MockK)
- Integration: FeedIntegrationTest (TestContainers)
- RssFetchService: static XML fixtures, no live HTTP
- Script: `scripts/test-feeds.sh`

## What's Deferred

- Lambda/EventBridge/Terraform — interface ready, only Spring local impl ships
- SyncJob records
- Feed failure tracking (failureCount increment, backoff)
- Update detection for changed items
