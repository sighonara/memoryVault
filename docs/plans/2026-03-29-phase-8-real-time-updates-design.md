# Phase 8: Real-Time Updates — Design Spec

**Date**: 2026-03-29
**Status**: Approved

## Overview

Add WebSocket support to MemoryVault so the Angular UI receives live signals when server-side state changes — new feed items, sync job progress, video download completions, bookmark ingests, and cross-tab mutations. The client reacts by refetching data through existing GraphQL/REST queries. No polling.

## Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Signal model | Lightweight signals (no payloads) | Client refetches via existing queries; server stays simple |
| Transport | WebSocket + STOMP (simple broker) | Decouples from app server; swappable to external broker (Amazon MQ, Redis) on AWS |
| Auth | JWT validated on STOMP CONNECT | Reuses existing JwtService; prevents unauthenticated connections |
| Internal eventing | Spring ApplicationEvents | Services stay decoupled from WebSocket; same event shape works when services move to Lambda |

## Event Types

All five event categories are in scope:

1. **Feed sync completion** — feed refreshed, N new items
2. **Sync job progress** — job status transitions (PENDING → RUNNING → SUCCESS/FAILED)
3. **Video download status** — per-video download completion
4. **Bookmark ingest ready** — CLI-submitted ingest available for review
5. **Cross-tab content sync** — mutations in one tab reflected in others

---

## Backend Architecture

### Dependencies

Add `spring-boot-starter-websocket` to `build.gradle.kts`.

### WebSocket Configuration

`config/WebSocketConfig.kt`:
- `@EnableWebSocketMessageBroker`
- Simple in-memory STOMP broker on prefix `/topic`
- Application destination prefix `/app` (reserved for future client→server messages)
- WebSocket endpoint at `/ws` with SockJS fallback
- Allowed origins: `http://localhost:4200` (matches existing CORS config)

### JWT Authentication on Connect

`config/WebSocketAuthInterceptor.kt`:
- `ChannelInterceptor` on the inbound CONNECT frame
- Extracts JWT from STOMP `Authorization` header (query param as fallback)
- Validates with existing `JwtService`
- Sets `Principal` on the STOMP session
- Rejects invalid/missing tokens by closing the connection

### Security Config Update

Permit `/ws/**` in the HTTP security chain (WebSocket upgrade starts as HTTP). STOMP-level interceptor handles auth after upgrade.

---

## Domain Events

### Event Type Enum

```kotlin
enum class VaultEventType {
    FEED_SYNC_COMPLETED,
    JOB_STATUS_CHANGED,
    VIDEO_DOWNLOAD_COMPLETED,
    INGEST_READY,
    CONTENT_MUTATED
}
```

### Event Hierarchy

```
VaultEvent (sealed interface)
├── eventType: VaultEventType
├── userId: UUID
├── timestamp: Instant
│
├── FeedSyncCompleted(userId, feedId?, newItemCount, feedsRefreshed)
├── JobStatusChanged(userId, jobId, jobType, oldStatus, newStatus)
├── VideoDownloadCompleted(userId, videoId, listId, success)
├── IngestReady(userId, previewId, itemCount)
└── ContentMutated(userId, contentType, mutationType, entityId?)
```

`ContentMutated` uses two enums:
- `contentType`: BOOKMARK, FEED_ITEM, VIDEO, FOLDER, CATEGORY
- `mutationType`: CREATED, UPDATED, DELETED

### Publishing Points

| Service | Event Published | Trigger |
|---------|----------------|---------|
| `FeedSyncService` / `RssFetchService` | `FeedSyncCompleted` | After sync completes |
| `SyncJobService` | `JobStatusChanged` | On `recordStart`, `recordSuccess`, `recordFailure` |
| `VideoSyncService` | `VideoDownloadCompleted` | After each video download attempt |
| `IngestService` | `IngestReady` | After ingest preview is stored |
| `BookmarkService`, `FeedItemService`, `VideoService`, `FolderService`, `CategoryService` | `ContentMutated` | On create, update, delete mutations |

Services inject `ApplicationEventPublisher` and call `publishEvent()` at the end of their operations.

---

## WebSocket Relay

### WebSocketEventRelay

`websocket/WebSocketEventRelay.kt`:
- Single `@Component` with `@EventListener` methods for each `VaultEvent` subtype
- Injects `SimpMessagingTemplate`
- Converts each event to a lightweight JSON signal and sends to the appropriate STOMP topic

### STOMP Topic Structure

All topics are user-scoped: `/topic/user/{userId}/...`

| Topic | Event | Signal Payload |
|-------|-------|----------------|
| `/topic/user/{userId}/feeds` | `FeedSyncCompleted` | `{ eventType, feedId?, newItemCount }` |
| `/topic/user/{userId}/jobs` | `JobStatusChanged` | `{ eventType, jobId, jobType, newStatus }` |
| `/topic/user/{userId}/videos` | `VideoDownloadCompleted` | `{ eventType, videoId, listId, success }` |
| `/topic/user/{userId}/ingests` | `IngestReady` | `{ eventType, previewId, itemCount }` |
| `/topic/user/{userId}/sync` | `ContentMutated` | `{ eventType, contentType, mutationType, entityId? }` |

User-scoped topics ensure clients only receive their own events. The relay reads `userId` from the domain event and targets that user's topic. Explicit topic paths (not `convertAndSendToUser()`) for consistency with future external broker routing.

---

## Angular Client

### Dependencies

Add `@stomp/rx-stomp` — RxJS-native STOMP client with built-in auto-reconnect.

### WebSocketService

`core/services/websocket.service.ts`:
- Connects on login, disconnects on logout
- STOMP connection to `ws://localhost:8080/ws` with JWT in STOMP headers
- Exposes `on(topic: string): Observable<StompMessage>` for topic subscriptions
- Auto-reconnect with exponential backoff (rx-stomp built-in)
- Reads `userId` from decoded JWT to construct user-scoped topic paths

### Store Integration

Each NgRx Signal Store subscribes to its relevant topic(s) and triggers a refetch:

| Store | Subscribes To | Action on Signal |
|-------|--------------|-----------------|
| `ReaderStore` | `/feeds`, `/sync` (FEED_ITEM) | `loadCategories()`, `loadItems()` |
| `BookmarksStore` | `/sync` (BOOKMARK, FOLDER), `/ingests` | `loadBookmarks()`, `loadFolders()`, `loadPendingIngests()` |
| `YoutubeStore` | `/videos` | `loadLists()`, reload selected list videos |
| `AdminStore` | `/jobs` | `loadJobs()` |

### Debounce

Rapid-fire signals (e.g., 50 `ContentMutated` events from a bulk sync) are debounced with a 500ms window per store, so only one refetch fires.

### Cross-Tab

Each tab maintains its own WebSocket connection. When tab A performs a mutation, the service publishes `ContentMutated`, and tab B's connection receives it independently and refetches.

---

## Testing Strategy

### Backend Unit Tests (MockK)

- **Event publishing**: Verify each service calls `applicationEventPublisher.publishEvent()` with the correct event type and data
- **WebSocketEventRelay**: Mock `SimpMessagingTemplate`, verify `convertAndSend` is called with correct topic path and payload for each event type
- **WebSocketAuthInterceptor**: Verify accepts valid JWT, rejects invalid/missing tokens

### Backend Integration Tests (TestContainers + STOMP test client)

One integration test per event type:
- Connect a STOMP test client to `/ws` with valid JWT
- Subscribe to the relevant user topic
- Trigger the service action (feed sync, job status change, video download, ingest, content mutation)
- Assert the correct signal arrives on the subscription within a timeout

### Frontend Unit Tests (Vitest)

- **WebSocketService**: Mock STOMP client, verify connect/disconnect lifecycle and topic subscription wiring
- **Store integration**: Mock `WebSocketService.on()` to emit a signal, verify the store calls the appropriate load method
- **Debounce**: Emit multiple rapid signals, verify only one refetch fires

### E2E Test (Playwright)

Single smoke test proving the full chain:
- Open two browser tabs
- Mark a feed item read in tab A
- Assert tab B reflects the change without manual refresh

---

## AWS Migration Path

The architecture is designed for a clean AWS transition:

**Today**: `Service → ApplicationEvent → WebSocketEventRelay → Simple STOMP Broker → Client`

**On AWS**: `Lambda → SNS/SQS/Redis → External Broker (Amazon MQ) → Client`

- Services already speak domain events, not STOMP messages
- Swapping the simple broker for Amazon MQ requires only config changes in `WebSocketConfig`
- Lambdas publish the same event shape to SNS/Redis; a bridge listener forwards to the broker
- ALB and API Gateway both support WebSocket connections natively

---

## Files Changed/Created

### New Files
- `src/.../config/WebSocketConfig.kt` — STOMP broker and endpoint config
- `src/.../config/WebSocketAuthInterceptor.kt` — JWT validation on CONNECT
- `src/.../websocket/VaultEvent.kt` — sealed interface + data classes + enums
- `src/.../websocket/WebSocketEventRelay.kt` — event listener → STOMP relay
- `client/src/app/core/services/websocket.service.ts` — STOMP client service
- Integration tests (one per event type)
- Unit tests for relay, interceptor, WebSocketService, store subscriptions

### Modified Files
- `build.gradle.kts` — add `spring-boot-starter-websocket`
- `client/package.json` — add `@stomp/rx-stomp`
- `src/.../config/SecurityConfig.kt` — permit `/ws/**`
- `src/.../scheduling/SyncJobService.kt` — publish `JobStatusChanged`
- `src/.../feed/service/FeedSyncService.kt` (or `RssFetchService`) — publish `FeedSyncCompleted`
- `src/.../youtube/service/VideoSyncService.kt` — publish `VideoDownloadCompleted`
- `src/.../bookmark/service/IngestService.kt` — publish `IngestReady`
- `src/.../bookmark/service/BookmarkService.kt` — publish `ContentMutated`
- `src/.../feed/service/FeedItemService.kt` — publish `ContentMutated`
- `src/.../youtube/service/VideoService.kt` — publish `ContentMutated`
- `client/src/app/reader/reader.store.ts` — subscribe to WebSocket signals
- `client/src/app/bookmarks/bookmarks.store.ts` — subscribe to WebSocket signals
- `client/src/app/youtube/youtube.store.ts` — subscribe to WebSocket signals
- `client/src/app/admin/admin.store.ts` — subscribe to WebSocket signals (replaces 3s polling)
