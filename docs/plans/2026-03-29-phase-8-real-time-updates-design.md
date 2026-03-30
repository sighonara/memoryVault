# Phase 8: Real-Time Updates — Design Spec

**Date**: 2026-03-29
**Status**: Approved

## Overview

Add WebSocket support to MemoryVault so the Angular UI receives live signals when server-side state changes — new feed items, sync job progress, video download completions, bookmark ingests, and cross-tab mutations. The client reacts by refetching data through existing GraphQL/REST queries. No polling.

## Decisions

| Decision          | Choice                                 | Rationale                                                                                    |
|-------------------|----------------------------------------|----------------------------------------------------------------------------------------------|
| Signal model      | Lightweight signals (no payloads)      | Client refetches via existing queries; server stays simple                                   |
| Transport         | WebSocket + STOMP (simple broker)      | Decouples from app server; swappable to external broker (Amazon MQ, Redis) on AWS            |
| Auth              | JWT validated on STOMP CONNECT         | Reuses existing JwtService; prevents unauthenticated connections                             |
| Internal eventing | Spring ApplicationEvents               | Services stay decoupled from WebSocket; same event shape works when services move to Lambda  |
| Threading         | `@Async` relay with dedicated executor | Isolates relay failures from sync services; mirrors AWS architecture (decoupled consumption) |
| Error handling    | Best-effort signals, catch-and-swallow | Missed signal = stale until next signal or manual refresh; never break a sync job            |

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
- Allowed origins: read from `memoryvault.cors.allowed-origins` property (`http://localhost:4200` in dev, production domain in prod)

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

| Service                                                                                  | Event Published          | Trigger                                            |
|------------------------------------------------------------------------------------------|--------------------------|----------------------------------------------------|
| `FeedSyncService` / `RssFetchService`                                                    | `FeedSyncCompleted`      | After sync completes                               |
| `SyncJobService`                                                                         | `JobStatusChanged`       | On `recordStart`, `recordSuccess`, `recordFailure` |
| `VideoSyncService`                                                                       | `VideoDownloadCompleted` | After each video download attempt                  |
| `IngestService`                                                                          | `IngestReady`            | After ingest preview is stored                     |
| `BookmarkService`, `FeedItemService`, `VideoService`, `FolderService`, `CategoryService` | `ContentMutated`         | On create, update, delete mutations                |

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

| Topic                          | Event                    | Signal Payloa d                                       |
|--------------------------------|--------------------------|-------------------------------------------------------|
| `/topic/user/{userId}/feeds`   | `FeedSyncCompleted`      | `{ eventType, feedId?, newItemCount }`                |
| `/topic/user/{userId}/jobs`    | `JobStatusChanged`       | `{ eventType, jobId, jobType, newStatus }`            |
| `/topic/user/{userId}/videos`  | `VideoDownloadCompleted` | `{ eventType, videoId, listId, success }`             |
| `/topic/user/{userId}/ingests` | `IngestReady`            | `{ eventType, previewId, itemCount }`                 |
| `/topic/user/{userId}/sync`    | `ContentMutated`         | `{ eventType, contentType, mutationType, entityId? }` |

User-scoped topics ensure clients only receive their own events. The relay reads `userId` from the domain event and targets that user's topic. Explicit topic paths (not `convertAndSendToUser()`) for consistency with future external broker routing.

---

## Angular Client

### Dependencies

Add `@stomp/rx-stomp` — RxJS-native STOMP client with built-in auto-reconnect.

### WebSocketService

`core/services/websocket.service.ts`:
- Connects on login, disconnects on logout
- STOMP connection to the WebSocket endpoint from `environment.wsUrl` (`ws://localhost:8080/ws` in dev, `wss://yourdomain.com/ws` in prod) with JWT in STOMP headers
- Exposes `on(topic: string): Observable<StompMessage>` for topic subscriptions
- Auto-reconnect with exponential backoff (rx-stomp built-in)
- Reads `userId` from decoded JWT to construct user-scoped topic paths

### Store Integration

Each NgRx Signal Store subscribes to its relevant topic(s) and triggers a refetch:

| Store            | Subscribes To                          | Action on Signal                                           |
|------------------|----------------------------------------|------------------------------------------------------------|
| `ReaderStore`    | `/feeds`, `/sync` (FEED_ITEM)          | `loadCategories()`, `loadItems()`                          |
| `BookmarksStore` | `/sync` (BOOKMARK, FOLDER), `/ingests` | `loadBookmarks()`, `loadFolders()`, `loadPendingIngests()` |
| `YoutubeStore`   | `/videos`                              | `loadLists()`, reload selected list videos                 |
| `AdminStore`     | `/jobs`                                | `loadJobs()`                                               |

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

## Logging

Per project conventions, all new classes get `private val log = LoggerFactory.getLogger(javaClass)`.

| Component                  | Level  | What                                                      |
|----------------------------|--------|-----------------------------------------------------------|
| `WebSocketAuthInterceptor` | INFO   | Successful WebSocket connect (userId)                     |
| `WebSocketAuthInterceptor` | WARN   | Auth failure — invalid/missing JWT (remote address)       |
| `WebSocketAuthInterceptor` | INFO   | WebSocket disconnect (userId, session duration)           |
| `WebSocketEventRelay`      | DEBUG  | Event received and signal sent (eventType, userId, topic) |
| `WebSocketEventRelay`      | WARN   | Failed to relay signal (eventType, userId, error message) |
| Domain services            | DEBUG  | Event published (eventType, userId, relevant IDs)         |

DEBUG for relay signals keeps logs quiet under normal operation; bump to INFO via Logback config when debugging WebSocket issues.

---

## Threading

`@Async` on the relay's `@EventListener` methods, with a dedicated thread pool executor:

- `WebSocketConfig` (or a separate `AsyncConfig`) defines a `ThreadPoolTaskExecutor` named `websocketRelayExecutor` — small pool (2–4 threads), since `convertAndSend()` is non-blocking
- `@Async("websocketRelayExecutor")` on each `@EventListener` method in `WebSocketEventRelay`
- `@EnableAsync` on the config class

**Why `@Async`:**
- Isolates relay exceptions from the publishing service — a relay failure never breaks a sync job
- Mirrors the eventual AWS architecture where event consumption is always on a separate thread (external broker consumer)
- Switching to `@Async` later would require verifying no ordering assumptions crept in; doing it from the start avoids that

**Ordering**: Signals are lightweight refetch triggers, not ordered data streams. If two signals arrive out of order, the client refetches the same data either way. No ordering guarantees needed.

---

## Error Handling

### Backend — WebSocket Auth Interceptor
- Invalid/missing JWT: log WARN, reject the STOMP CONNECT frame (send ERROR frame, close session). Do not throw — Spring's channel interceptor contract expects a clean return or `null` message.

### Backend — WebSocket Event Relay
- `convertAndSend()` failure: catch, log WARN with event details, swallow. A failed signal means the client misses one update and stays on stale data until the next signal or manual refresh. This is acceptable — signals are best-effort, not guaranteed delivery.
- Exception in event deserialization/mapping: same — catch, log ERROR, swallow. Never propagate back to the async executor (would only log to stderr and be lost).

### Backend — Domain Event Publishing
- `publishEvent()` is fire-and-forget from the service's perspective (async relay). If `ApplicationEventPublisher` itself throws (should not happen), it would propagate — but this is a Spring framework failure, not something we handle.

### Frontend — WebSocket Connection
- Connection failure / server unreachable: rx-stomp auto-reconnects with exponential backoff. No user-facing error — the UI works normally via manual fetches, just without live updates.
- Auth rejection (ERROR frame): log to console. If the JWT expired, the next HTTP request will also 401 and trigger the existing redirect-to-login flow.
- Stale connection (server restart): rx-stomp detects disconnect and reconnects. Stores re-subscribe automatically on reconnect.

### Frontend — Signal Processing
- Malformed signal payload: catch in the `WebSocketService.on()` observable, log to console, skip. Don't break the subscription.
- Refetch failure after signal: handled by existing store error handling (stores already handle failed GraphQL queries).

---

## Connection Lifecycle

### STOMP Heartbeats

Configure server and client heartbeats to detect dead connections:
- Server sends heartbeat every 10s, expects client heartbeat every 10s
- Configured in `WebSocketConfig` via `configureMessageBroker`: `setHeartbeatValue(longArrayOf(10000, 10000))`
- rx-stomp client configured to match: `heartbeatIncoming: 10000, heartbeatOutgoing: 10000`
- If no heartbeat received within the timeout window, Spring closes the session and cleans up resources

### JWT Expiry Mid-Session

WebSocket connections are long-lived and may outlive the JWT used at CONNECT time. Policy: **validate once at connect, do not re-validate mid-session.** Rationale:
- Signals are lightweight and contain no sensitive data (just event types and IDs)
- The JWT expiry (24h per current config) is long enough that sessions rarely outlive it
- If the JWT expires, the next HTTP request (GraphQL refetch triggered by a signal) will 401 and redirect to login, which also tears down the WebSocket connection via the logout flow
- Re-validating mid-session would require either periodic checks on a timer (complexity for no real security gain) or validating on every message (performance overhead on a high-frequency channel)

### Graceful Shutdown

On server shutdown (restart, deploy):
- Spring's `@PreDestroy` on the WebSocket config closes all active STOMP sessions
- Clients receive a disconnect event; rx-stomp begins auto-reconnect with backoff
- No custom shutdown message needed — the client's reconnect behavior handles this transparently
- After reconnect, stores re-subscribe and refetch current state (same as initial page load)

---

## Resource Limits

### Connection Limits

For a self-hosted, single-user application, aggressive connection limits are unnecessary. Sensible defaults:
- **No per-user connection cap** — a handful of tabs is the expected usage pattern
- **Spring's default send buffer limit** (512KB) and send timeout (10s) are sufficient
- If buffer fills (slow client), Spring closes the session — client reconnects automatically

These defaults are revisited if the application moves to multi-tenant (Phase 9+).

### Message Rate

Signals are produced by sync operations, not user input — the rate is naturally bounded:
- Feed sync: one `FeedSyncCompleted` + N `ContentMutated` per sync (N = new items, typically < 100)
- Video sync: one `VideoDownloadCompleted` per video download (slow, minutes apart)
- Content mutations: one per user action (bookmark, mark-read, etc.)

No rate limiting needed on the server side. Client-side debounce (500ms) in the stores handles burst scenarios.

---

## Configuration

### Step 0: Establish Profile-Aware Config (Pre-Requisite)

Before adding Phase 8 config, split existing hardcoded dev values into proper Spring profiles. This is a foundational step that benefits all phases.

**Current state**: `application.properties` has everything (dev DB, dev CORS, dev JWT secret). Only `application-test.properties` exists for TestContainers.

**Target state**: Three-tier config:
- `application.properties` — shared defaults and non-environment-specific settings
- `application-dev.properties` — local development overrides (active by default when no profile set)
- `application-prod.properties` — production overrides (activated via `SPRING_PROFILES_ACTIVE=prod`)

**Settings to migrate from `application.properties`:**

| Setting                            | Dev Value                                      | Prod Value                             |
|------------------------------------|------------------------------------------------|----------------------------------------|
| `spring.datasource.url`            | `jdbc:postgresql://localhost:5433/memoryvault` | RDS endpoint                           |
| `memoryvault.cors.allowed-origins` | `http://localhost:4200`                        | `https://yourdomain.com`               |
| `memoryvault.jwt.secret`           | Dev secret                                     | Environment variable / Secrets Manager |
| `spring.graphql.graphiql.enabled`  | `true`                                         | `false`                                |

**SecurityConfig update**: Read CORS origins from `memoryvault.cors.allowed-origins` property instead of hardcoded string.

**Angular environment files**: Create `environment.ts` (dev) and `environment.prod.ts` with `apiUrl` and `wsUrl`. Angular CLI handles the file swap at build time.

### Phase 8 Configuration

With profiles in place, add WebSocket-specific properties:

**Backend (`application.properties` — shared defaults):**
```properties
# WebSocket
memoryvault.websocket.heartbeat-interval-ms=10000
memoryvault.websocket.relay-executor-pool-size=4
```

**Backend (`application-dev.properties`):**
```properties
memoryvault.websocket.allowed-origins=${memoryvault.cors.allowed-origins}
memoryvault.websocket.broker-type=simple
```

**Backend (`application-prod.properties`):**
```properties
memoryvault.websocket.allowed-origins=${memoryvault.cors.allowed-origins}
memoryvault.websocket.broker-type=external
memoryvault.websocket.broker-host=<amazon-mq-endpoint>
memoryvault.websocket.broker-port=61613
memoryvault.websocket.broker-username=<from-secrets-manager>
memoryvault.websocket.broker-password=<from-secrets-manager>
```

**Frontend (`environment.ts` — dev):**
```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080',
  wsUrl: 'ws://localhost:8080/ws',
};
```

**Frontend (`environment.prod.ts`):**
```typescript
export const environment = {
  production: true,
  apiUrl: 'https://yourdomain.com',
  wsUrl: 'wss://yourdomain.com/ws',
};
```

**Frontend constant (hardcoded, not per-environment):**
- Debounce window: 500ms (in each store's subscription setup)

`WebSocketConfig.kt` reads all values from injected `@ConfigurationProperties` — no hardcoded strings.

---

## Cross-Cutting Concerns Checklist

| Concern                                                        | Status    | Section                                                                                        |
|----------------------------------------------------------------|-----------|------------------------------------------------------------------------------------------------|
| Auth / security                                                | Addressed | JWT Authentication on Connect                                                                  |
| Error handling                                                 | Addressed | Error Handling                                                                                 |
| Threading / concurrency                                        | Addressed | Threading                                                                                      |
| Logging / observability                                        | Addressed | Logging                                                                                        |
| Connection lifecycle (heartbeats, expiry, reconnect, shutdown) | Addressed | Connection Lifecycle                                                                           |
| Resource limits (connections, message rate)                    | Addressed | Resource Limits                                                                                |
| Configuration (dev/prod profiles, tunables)                    | Addressed | Configuration                                                                                  |

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
- `src/main/resources/application-dev.properties` — dev profile config
- `src/main/resources/application-prod.properties` — prod profile config (placeholder values)
- `client/src/environments/environment.ts` — dev environment (apiUrl, wsUrl)
- `client/src/environments/environment.prod.ts` — prod environment
- `src/.../config/WebSocketConfig.kt` — STOMP broker and endpoint config
- `src/.../config/WebSocketAuthInterceptor.kt` — JWT validation on CONNECT
- `src/.../websocket/VaultEvent.kt` — sealed interface + data classes + enums
- `src/.../websocket/WebSocketEventRelay.kt` — event listener → STOMP relay
- `client/src/app/core/services/websocket.service.ts` — STOMP client service
- Integration tests (one per event type)
- Unit tests for relay, interceptor, WebSocketService, store subscriptions

### Modified Files
- `src/main/resources/application.properties` — extract env-specific values to profile files, add shared WebSocket defaults
- `build.gradle.kts` — add `spring-boot-starter-websocket`
- `client/package.json` — add `@stomp/rx-stomp`
- `src/.../config/SecurityConfig.kt` — permit `/ws/**`, read CORS origins from config property
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
