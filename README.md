# MemoryVault

> A self-hosted content archival and aggregation platform — RSS reader, bookmark manager, and YouTube archiver with AI interaction via MCP.

## Overview

MemoryVault replaces services like theoldreader.com with a fully self-hosted alternative. All content lives in your PostgreSQL database and local/S3 storage. Claude (via MCP) can query and manage everything through natural language.

- **RSS reader** — subscribe to feeds, read items, mark read/unread
- **Bookmarks** — save URLs with tags, full-text search, export to Netscape HTML
- **YouTube archival** — track playlists/channels, download videos via yt-dlp, detect removals
- **AI integration** — Claude Desktop connects via MCP to query and manage all content
- **Web UI** — Angular frontend with Google Reader-style layout (feed reader, bookmarks, YouTube, admin)
- **Global search** — PostgreSQL full-text search across bookmarks, feed items, and videos

## Technology Stack

| Layer | Technology |
|---|---|
| Backend language | Kotlin 2.x |
| Backend framework | Spring Boot 4.x |
| API | GraphQL (Spring for GraphQL) + REST (auth only) |
| AI/MCP | Spring AI 2.0-M2 (`spring-ai-starter-mcp-server`, STDIO transport) |
| Database | PostgreSQL 16 (Docker, port **5433**) |
| ORM | Spring Data JPA + Flyway migrations |
| Frontend | Angular 21, zoneless, standalone components |
| UI library | Angular Material |
| State management | NgRx Signal Store |
| GraphQL client | Apollo Angular + graphql-codegen |
| Frontend testing | Vitest (unit), Playwright (E2E) |
| Backend testing | JUnit 5, MockK, TestContainers |
| Content processing | Python 3.11+, yt-dlp |
| Infrastructure | Docker Compose (local), AWS (EC2, RDS, S3, Lambda, EventBridge) |
| IaC | Terraform |

## Project Structure

```
memoryVault/
├── src/                        # Spring Boot backend (Kotlin)
│   └── main/kotlin/org/sightech/memoryvault/
│       ├── auth/               # User entity, JWT service, login endpoint
│       ├── bookmark/           # Bookmark management
│       ├── feed/               # RSS feeds and items
│       ├── youtube/            # YouTube lists and video archival
│       ├── search/             # PostgreSQL full-text search
│       ├── stats/              # System stats
│       ├── sync/               # Job history tracking
│       ├── log/                # Log retrieval service
│       ├── graphql/            # GraphQL resolvers
│       ├── mcp/                # Spring AI @Tool classes
│       └── config/             # SecurityConfig, etc.
├── client/                     # Angular frontend
│   └── src/app/
│       ├── auth/               # Login, auth service, guard, interceptor
│       ├── reader/             # Feed reader (home)
│       ├── bookmarks/          # Bookmark management
│       ├── youtube/            # YouTube archive
│       ├── admin/              # Jobs, logs, stats
│       ├── search/             # Global search results
│       └── shared/             # Layout, GraphQL provider, generated types
├── content-processor/          # Python — yt-dlp, RSS fetching, web scraping
├── terraform/                  # AWS infrastructure (Phase 7)
├── lambdas/                    # AWS Lambda functions (Phase 7)
├── scripts/                    # Test and utility scripts
│   ├── test-all.sh
│   ├── smoke-test.sh
│   └── test-frontend.sh
├── docs/plans/                 # Phase design docs and implementation plans
├── compose.yaml                # Docker Compose (PostgreSQL)
└── build.gradle.kts
```

## Local Development

### Prerequisites

- Docker 24+ and Docker Compose 2.20+
- JDK 21+
- Node.js 20+ and npm 10+
- Python 3.11+ (optional, for content-processor)

### Start

```bash
# Start PostgreSQL (port 5433)
docker compose up -d

# Start backend
./gradlew bootRun

# Start frontend (in another terminal)
cd client && npm start
```

Open http://localhost:4200

### Local Login

| Field | Value |
|---|---|
| Email | `system@memoryvault.local` |
| Password | `memoryvault` |

This seed user is created by the V2 + V4 Flyway migrations and has the `OWNER` role. JWT tokens expire after 24 hours. If the app starts returning errors after leaving it open, log out and back in.

### Other useful commands

```bash
# Run backend tests (TestContainers handles the DB)
./gradlew test

# Run a specific test class
./gradlew test --tests "*BookmarkServiceTest"

# Run all tests (backend + frontend)
./scripts/test-all.sh

# Frontend unit tests
cd client && npm test

# Frontend lint
cd client && npm run lint

# Regenerate GraphQL types from schema + .graphql files
cd client && npm run codegen

# Inspect the database
docker compose exec postgres psql -U memoryvault -c "SELECT * FROM users;"

# Smoke test against a running instance
./scripts/smoke-test.sh
```

### GraphQL Playground

http://localhost:8080/graphiql — available in dev for ad-hoc queries.

## MCP Configuration (Claude Desktop)

The MCP server runs inside Spring Boot over STDIO transport. To connect Claude Desktop:

```json
{
  "mcpServers": {
    "memoryvault": {
      "command": "java",
      "args": ["-jar", "/path/to/memoryvault.jar"],
      "env": {}
    }
  }
}
```

Or, if running via Gradle:

```json
{
  "mcpServers": {
    "memoryvault": {
      "command": "/path/to/gradlew",
      "args": ["bootRun"],
      "cwd": "/path/to/memoryVault"
    }
  }
}
```

Available MCP tools: `addBookmark`, `listBookmarks`, `tagBookmark`, `deleteBookmark`, `exportBookmarks`, `addFeed`, `listFeeds`, `getFeedItems`, `markItemRead`, `markItemUnread`, `markFeedRead`, `refreshFeed`, `addYoutubeList`, `listYoutubeLists`, `listArchivedVideos`, `getVideoStatus`, `refreshYoutubeList`, `deleteYoutubeList`, `search`, `getStats`, `listJobs`, `getLogs`.

## API

All data operations go through GraphQL at `/graphql`. The schema is in `src/main/resources/graphql/`. Authentication uses a REST endpoint:

- `POST /api/auth/login` — returns a JWT token
- All other requests require `Authorization: Bearer <token>` header

## Roadmap

### Phase 0 — Tooling ✅
Spring Boot skeleton, Docker Compose, Claude Code custom skills.

### Phase 1 — Bookmarks ✅
Entity, service, 5 MCP tools, Flyway migrations (V1–V2).

### Phase 2 — RSS Feeds ✅
RSS parsing (RSS-Parser), feed/item entities, 7 MCP tools, scheduled sync via `JobScheduler` interface.

### Phase 3 — YouTube Archival ✅
yt-dlp integration, YoutubeList/Video entities, local/S3 storage stubs, 6 MCP tools.

### Phase 4 — Cross-Cutting ✅
PostgreSQL full-text search (GIN indexes, tsvectors), SyncJob history, structured JSON logging (Logback + Logstash encoder), 4 MCP tools.

### Phase 5 — Web UI + Auth ✅ (current branch)
JWT authentication, GraphQL API (schema-first, Spring for GraphQL), Angular 21 frontend (reader, bookmarks, YouTube, admin, search), Apollo Angular, graphql-codegen.

### Phase 6 — Bookmark Automation
Automatically import bookmarks from browsers and integrate with existing bookmarks.

### Phase 7 — Infrastructure
Terraform, GitHub Actions CI/CD, production AWS deployment (EC2, RDS, S3, Lambda, EventBridge). AWS Cognito auth, CloudWatch log retrieval, AWS cost tracking.

### Phase 8 — Real-Time Updates
WebSocket support for live UI updates — new feed items, sync progress, download status pushed to Angular without polling.

## License

MIT
