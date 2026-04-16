# CLAUDE.md

## Project Overview

**MemoryVault** — a self-hosted content archival and aggregation platform. RSS reader, bookmark manager, and YouTube archiver with AI interaction via MCP, a web UI, and automated AWS sync jobs.

Design docs: `docs/plans/2026-03-05-tooling-first-design.md`

**Master roadmap:** `docs/plans/2026-03-05-tooling-first-design.md` — contains all phase summaries and future work items. Update this document whenever a phase is renamed, completed, or when future work items are identified during design. Do not bury future work only in phase-specific specs.

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
├── graphql/       # Spring for GraphQL resolvers
├── <domain>/
│   ├── entity/    # JPA entities (e.g. Bookmark, Folder, IngestPreviewEntity)
│   ├── repository/
│   ├── service/   # Business logic (e.g. BookmarkService, IngestService)
│   └── controller/ # REST controllers (e.g. IngestController)
```

### Commands

- `./scripts/dev.sh` — start backend + frontend together (Ctrl+C stops both)
- `./gradlew bootRun` — start the backend only (requires Docker Compose running)
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
- **Logging**: Every controller and service class must have `private val log = LoggerFactory.getLogger(javaClass)`. Log at INFO level for all mutations (create, update, delete) with relevant context (entity ID, count, etc.). Log at DEBUG for read operations. Log at WARN/ERROR for failures.

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
- **NgRx Signal Store**: In `withMethods`, the `store` parameter only has state/computed signals — NOT other methods. To call one method from another, define methods as local `const` variables in the closure, then reference them directly. Never use `(store as any).methodName()`.
- **Search**: Use `(input)` event with 300ms debounce via `setTimeout`/`clearTimeout` for search inputs (consistent across all features)
- **Bookmarks feature** has sub-components: `bookmark-tree/`, `bookmark-list/`, `ingest-panel/`, `conflict-review/` — each with barrel export (`index.ts`)

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

### Infra-vs-code change rules

Changes to `terraform/**` are **not** delivered by `git push`. The deploy workflow only rebuilds the app image and redeploys the container onto whatever EC2 instance already exists — it never re-runs Terraform. To actually change AWS state you must run `terraform apply` locally.

When recommending a fix that touches `terraform/**`, the sequence is:
1. `terraform plan` — confirm expected drift (especially EC2 replacement, which forces EIP reassociation and brief downtime).
2. `terraform apply` — apply to AWS.
3. Wait for the new instance's status checks to pass.
4. Then commit/push the code change.

Label every recovery step by what it touches: *code* (`git push`), *AWS state* (`terraform apply`), or *automation* (workflow run). Do not assume one implies another.

### Where configuration lives

Before fixing a broken value, ask: is it in the right *kind* of place? Categories:

- **GitHub Actions secrets** — long-lived credentials and account-level constants (AWS keys, account ID, region). Never put anything terraform outputs or recreates here (e.g. instance IDs, pool IDs) — those must be discovered at runtime.
- **Runtime discovery in the workflow** (`aws ec2 describe-instances`, `terraform output`) — for any identifier that changes when infrastructure is replaced.
- **`.env` (gitignored) + `.env.sample` (committed)** — per-developer config and rotated secrets for local scripts (smoke test credentials, etc).
- **`CLAUDE.md` / `docs/plans/`** — project rules, architectural decisions, phase plans.

When a value breaks, default to checking the *category* first, not just the value.

---

## Skills

Custom project skills live in `.claude/skills/`:

- `/scaffold-entity` — new Kotlin JPA entity + repo + service + controller
- `/add-mcp-tool` — new Spring AI `@Tool` method
- `/add-lambda` — new AWS Lambda function with Terraform
- `/add-content-processor` — new Python processor module
- `/write-tests` — write tests with verifiable evidence, positive + negative cases

Installed from skills.sh: `mcp-builder`, `kotlin-springboot`, `python-testing-patterns`, `async-python-patterns`, `pytest-coverage`, `webapp-testing`
