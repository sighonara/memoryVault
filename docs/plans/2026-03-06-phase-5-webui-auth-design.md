# Phase 5: Web UI + Auth --- Design Document

**Date**: 2026-03-06
**Status**: Approved

## Goal

Add a web frontend (Angular + Angular Material) and authentication (JWT) to MemoryVault. The Angular UI communicates with the backend via a schema-first GraphQL API. Authentication is JWT-based, issuing tokens locally now with a clean swap path to AWS Cognito in Phase 6.

## Scope

### In scope
- JWT authentication (local issuer, AWS-ready abstraction)
- User entity update (passwordHash, role enum)
- GraphQL API layer (schema-first, resolvers for all domains)
- Angular UI: reader, bookmarks, YouTube, admin, global search
- Apollo Client + graphql-codegen on the Angular side
- Swap Karma/Jasmine to Vitest for frontend testing
- Testing across all layers

### Not in scope (deferred)
- AWS Cognito integration (Phase 6)
- Social login
- Real-time updates / WebSockets (Phase 7)
- Mobile-responsive design beyond Angular Material defaults

---

## Authentication

### Local auth flow
- `POST /api/auth/login` accepts email + password
- Validates against BCrypt hash in `users` table
- Returns a signed JWT (HS256, configurable secret, 24h expiry)
- JWT claims: `userId`, `email`, `role`
- `spring-boot-starter-oauth2-resource-server` validates JWTs on every request

### AWS-ready abstraction
- JWT validation configured via `spring.security.oauth2.resourceserver.jwt.issuer-uri`
- Locally: points to the app itself
- On AWS: change to Cognito user pool URL --- no code changes
- Local login endpoint uses `@Profile("!aws")` --- disappears when Cognito takes over
- Angular always sends `Authorization: Bearer <token>` regardless of issuer

### CurrentUser helper
- Extracts `userId` from the security context
- All service layer calls use this instead of the hardcoded seed user UUID
- Works identically with local JWTs or Cognito JWTs

### User entity update
- Add `passwordHash: String?` (nullable --- Cognito users won't have one)
- Add `role` enum: `OWNER`, `ADMIN`, `VIEWER`
- V4 migration adds the column and sets the seed user's password

---

## GraphQL API

### Approach: schema-first

Spring for GraphQL (`spring-boot-starter-graphql`) with `.graphqls` schema files.

### Schema files

Located in `src/main/resources/graphql/`:

| File | Content |
|---|---|
| `schema.graphqls` | Query/Mutation root types, shared scalars, pagination types |
| `bookmarks.graphqls` | Bookmark type, queries (list, search), mutations (add, tag, delete, export) |
| `feeds.graphqls` | Feed, FeedItem types, queries (list feeds, get items), mutations (add, mark read/unread, refresh) |
| `youtube.graphqls` | YoutubeList, Video types, queries (list, browse, status), mutations (add, refresh, delete) |
| `admin.graphqls` | SyncJob, LogEntry, SystemStats types, queries (listJobs, getLogs, getStats, search) |

### Resolvers

Located in `src/main/kotlin/org/sightech/memoryvault/graphql/`:

- One resolver class per domain: `BookmarkResolver`, `FeedResolver`, `YoutubeResolver`, `AdminResolver`
- Resolvers call existing service layer --- no new business logic
- `@AuthenticationPrincipal` extracts userId from JWT on every resolver method
- Cursor-based pagination for list queries

### Endpoints

- `/graphql` --- API endpoint
- `/graphiql` --- playground, enabled in dev profile only

---

## Angular UI

### Pages

| Route | Page | Description |
|---|---|---|
| `/login` | Login | Email + password form, public |
| `/` | Reader | Google Reader-style feed reader (home/default, auth required) |
| `/bookmarks` | Bookmarks | List, add, tag, delete, export (auth required) |
| `/youtube` | YouTube | Playlists, video archive browser (auth required) |
| `/admin` | Admin | Jobs history, log viewer, system stats (auth required) |
| `/search` | Search Results | Cross-entity FTS results grouped by type (auth required) |

### Reader page (home)

Modeled after Google Reader / TheOldReader:

- **Left sidebar**: feed list with unread counts, organized by feed. Small status indicators for bookmark count and YouTube download progress.
- **Main content area**: article list, click to expand inline. Articles marked as read on expand.
- **Top bar**: global search input, navigation links, user menu.

### Bookmarks page

- Filterable/searchable list with tag chips
- Add bookmark form (URL, title, tags)
- Tag management inline
- Export button (Netscape HTML)

### YouTube page

- Playlist list with progress stats (downloaded/total, removed count)
- Expandable video list per playlist
- Status badges (downloaded, removed, pending)

### Admin page

- Three sections: job history table, log viewer with level/service filters, system stats summary
- Job history: sortable table with type, status, timing, metadata
- Log viewer: filterable by level (INFO/WARN/ERROR) and service name
- Stats: content counts, storage, sync health

### Global search

- Search input in the top bar triggers cross-entity FTS
- Results displayed on `/search` page, grouped by type (bookmarks, feed items, videos)
- Clicking a result navigates to the relevant page/item

### Architecture

- **State management**: NgRx Signal Store per feature (auth, feeds, bookmarks, youtube, admin)
- **GraphQL client**: Apollo Client for Angular + graphql-codegen for type generation
- **UI framework**: Angular Material with custom theme
- **Component conventions**: standalone, OnPush, `inject()`, `@if`/`@for`, no `subscribe()`
- **Feature folders**: `feature-name/{component,service,store,model,routes}` with barrel exports

---

## Dependencies

### Backend (new)
- `spring-boot-starter-graphql` --- GraphQL server
- `spring-boot-starter-oauth2-resource-server` --- JWT validation
- `io.jsonwebtoken:jjwt-api` / `jjwt-impl` / `jjwt-jackson` --- local JWT signing

### Frontend (new)
- `@angular/material` --- UI components
- `apollo-angular` / `@apollo/client` / `graphql` --- GraphQL client
- `@graphql-codegen/cli` + plugins --- TypeScript type generation from schema
- Vitest (replacing Karma/Jasmine)
- Playwright (E2E)

---

## Testing

### Backend
- **Unit**: resolver classes with MockK, mock service layer
- **Integration**: `HttpGraphQlTester` with TestContainers PostgreSQL
- **Auth**: JWT validation tests, 401 without token

### Frontend
- **Unit**: Vitest with TestBed, mock Apollo services
- **E2E**: Playwright --- login flow, read article, add bookmark, navigation

### Scripts
- `scripts/test-frontend.sh` --- Vitest + lint
- `scripts/test-graphql.sh` --- GraphQL resolver and integration tests
- Update `scripts/test-all.sh` to include both

---

## Migration: V4__auth.sql

1. Add `password_hash` column to `users` (nullable)
2. Add `role` column to `users` (VARCHAR(20), default 'OWNER')
3. Set seed user's password hash (BCrypt of a configurable default)
