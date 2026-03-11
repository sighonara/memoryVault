# CLAUDE.md

## Project Overview

**MemoryVault** — a self-hosted content archival and aggregation platform. RSS reader, bookmark manager, and YouTube archiver with AI interaction via MCP, a web UI, and automated AWS sync jobs.

Design docs: `docs/plans/2026-03-05-tooling-first-design.md`

---

## Backend (Spring Boot / Kotlin)

Located in `src/`.

- **Language**: Kotlin 2.x
- **Framework**: Spring Boot 4.x
- **AI/MCP**: Spring AI 2.0.0-M2 (`spring-ai-starter-mcp-server`)
- **Database**: PostgreSQL 16 via Docker Compose, migrations with Flyway
- **ORM**: Spring Data JPA
- **Testing**: JUnit 5, MockK, TestContainers

### Package Structure

```
src/main/kotlin/org/sightech/memoryvault/
├── mcp/           # Spring AI @Tool classes (one per domain)
├── <domain>/
│   ├── entity/
│   ├── repository/
│   ├── service/
│   └── controller/
```

### Commands

- `./gradlew bootRun` — start the app (requires Docker Compose running)
- `./gradlew test` — run tests (TestContainers handles PostgreSQL)
- `docker compose up -d` — start PostgreSQL
- `./scripts/test-all.sh` — run all tests across all services (backend + frontend + E2E)
- `./scripts/test-frontend.sh` — run frontend Vitest unit tests
- `./scripts/test-graphql.sh` — run GraphQL/integration tests
- `./scripts/require-backend.sh` — shared check that backend is running (prompts user if not)
- `./scripts/smoke-test.sh` — smoke test against running instance

### Conventions

- UUIDs for all primary keys
- Soft deletes via `deletedAt` timestamp (never hard delete user content)
- Optimistic locking via `version` field on mutable entities
- MCP `@Tool` methods call service layer — never repositories directly
- Return DTOs or simple types from `@Tool` methods, not JPA entities

---

## Frontend (Angular)

Located in `client/`.

- **Framework**: Angular 21, zoneless, standalone components
- **State**: NgRx Signal Store
- **UI**: Angular Material with custom theme
- **API**: REST with HttpClient + `httpResource()`
- **Testing**: Vitest (unit), Playwright (e2e)

### Commands

- `npm run test` — run Vitest unit tests
- `npm run e2e` — run Playwright E2E tests (requires backend running for navigation tests)
- `npm run build` — production build

### Conventions

- Feature-based folder structure: `feature-name/{component,service,store,model,routes}`
- All components use `OnPush` change detection
- API calls through services, never directly in components
- Barrel exports (`index.ts`) for every feature folder
- No NgModules, no `CommonModule` imports
- No constructor injection (use `inject()`)
- No `*ngIf` / `*ngFor` (use `@if` / `@for`)
- No `subscribe()` in components

---

## Python Content Processor

Located in `content-processor/` (created in Phase 3).

- **Language**: Python 3.11+
- **Purpose**: yt-dlp downloads, web scraping, RSS fetching, content archival
- **Testing**: pytest

---

## AWS Lambda Functions

Located in `lambdas/` (created in Phase 6+).

- **Runtime**: Python 3.11
- **Trigger**: EventBridge scheduled rules
- **Terraform**: `terraform/lambdas/<function-name>.tf`

---

## Infrastructure

- **Local dev**: Docker Compose (`compose.yaml`)
- **Production**: AWS (EC2, RDS, S3, Lambda, EventBridge, CloudWatch)
- **IaC**: Terraform (`terraform/`)
- **CI/CD**: GitHub Actions (Phase 6)

---

## Skills

Custom project skills live in `.claude/skills/`:

- `/scaffold-entity` — new Kotlin JPA entity + repo + service + controller
- `/add-mcp-tool` — new Spring AI `@Tool` method
- `/add-lambda` — new AWS Lambda function with Terraform
- `/add-content-processor` — new Python processor module
- `/write-tests` — write tests with verifiable evidence, positive + negative cases

Installed from skills.sh: `mcp-builder`, `kotlin-springboot`, `python-testing-patterns`, `async-python-patterns`, `pytest-coverage`, `webapp-testing`
