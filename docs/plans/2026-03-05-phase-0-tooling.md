# Phase 0: Tooling Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Stand up the development foundation — Docker Compose with PostgreSQL, a working Spring AI MCP server skeleton with one ping tool, and four custom Claude Code skills that will accelerate all future phases.

**Architecture:** Spring Boot app with `spring-ai-starter-mcp-server` running STDIO transport (for Claude Desktop integration). PostgreSQL via Docker Compose with Spring Boot's Docker Compose auto-integration. Database migrations managed by Flyway. Four project-specific Claude Code skills live in `.claude/skills/` and are version-controlled with the project.

**Tech Stack:** Kotlin 2.x, Spring Boot 4.x, Spring AI 2.0.0-M2, PostgreSQL 16, Flyway, TestContainers, JUnit 5, MockK, Docker Compose

---

## Task 1: Add PostgreSQL to Docker Compose

**Files:**
- Modify: `compose.yaml`

**Step 1: Replace the empty compose.yaml**

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: memoryvault
      POSTGRES_USER: memoryvault
      POSTGRES_PASSWORD: memoryvault
    ports:
      - "5433:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U memoryvault"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

**Step 2: Verify Docker Compose starts**

```bash
docker compose up -d
docker compose ps
```

Expected: postgres container shows `healthy` status.

**Step 3: Commit**

```bash
git add compose.yaml
git commit -m "feat: add PostgreSQL service to Docker Compose"
```

---

## Task 2: Add Database and Web Dependencies

**Files:**
- Modify: `build.gradle.kts`

The current `build.gradle.kts` has Spring Security and the MCP server but is missing Spring Web, JPA, PostgreSQL driver, Flyway, and TestContainers. Add them.

**Step 1: Update the dependencies block**

Replace the existing `dependencies` block with:

```kotlin
dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Database
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // AI / MCP
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server")

    // Dev
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    developmentOnly("org.springframework.ai:spring-ai-spring-boot-docker-compose")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("io.mockk:mockk:1.13.10")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

**Step 2: Verify the build resolves dependencies**

```bash
./gradlew dependencies --configuration runtimeClasspath
```

Expected: dependency tree prints without errors.

**Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "feat: add web, JPA, Flyway, PostgreSQL, TestContainers dependencies"
```

---

## Task 3: Configure Application Properties

**Files:**
- Modify: `src/main/resources/application.properties`
- Create: `src/main/resources/application-test.properties`

**Step 1: Update application.properties**

```properties
spring.application.name=memoryVault

# Database — matches compose.yaml credentials (port 5433)
spring.datasource.url=jdbc:postgresql://localhost:5433/memoryvault
spring.datasource.username=memoryvault
spring.datasource.password=memoryvault
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false

# Flyway
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# MCP Server
spring.ai.mcp.server.name=memoryvault
spring.ai.mcp.server.version=0.0.1
spring.ai.mcp.server.instructions=MemoryVault: search and manage your bookmarks, RSS feeds, and archived YouTube videos.
```

**Step 2: Create application-test.properties**

TestContainers will override the datasource URL at runtime via `@DynamicPropertySource`. This file just disables Flyway's baseline-on-migrate for tests:

```properties
spring.flyway.clean-on-validation-error=false
```

**Step 3: Commit**

```bash
git add src/main/resources/application.properties src/main/resources/application-test.properties
git commit -m "feat: configure datasource, JPA, Flyway, and MCP server properties"
```

---

## Task 4: Add Flyway Baseline Migration

**Files:**
- Create: `src/main/resources/db/migration/V1__baseline.sql`

Flyway runs migrations in version order. The baseline migration creates the schema foundation — no domain tables yet, just the structure we'll build on.

**Step 1: Create the migration directory and file**

```bash
mkdir -p src/main/resources/db/migration
```

```sql
-- V1__baseline.sql
-- Baseline schema: extensions and shared infrastructure only.
-- Domain tables are added in subsequent migrations per phase.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";  -- for gen_random_uuid()

-- Flyway tracks its own history in flyway_schema_history (created automatically).
-- This migration intentionally contains no domain tables.
-- Phase 1 (bookmarks) will add V2__bookmarks.sql, etc.
```

**Step 2: Verify Flyway runs cleanly**

With Docker Compose running (`docker compose up -d`):

```bash
./gradlew bootRun
```

Expected: App starts, logs show `Successfully applied 1 migration to schema "public"`. No errors.

**Step 3: Stop the app (Ctrl+C) and commit**

```bash
git add src/main/resources/db/migration/V1__baseline.sql
git commit -m "feat: add Flyway baseline migration with pgcrypto extension"
```

---

## Task 5: Create the MCP Server Skeleton with a Ping Tool

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/mcp/SystemTools.kt`
- Modify: `src/test/kotlin/org/sightech/memoryvault/MemoryVaultApplicationTests.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/mcp/SystemToolsTest.kt`

This creates one `@Tool` annotated method — a ping — to prove the MCP server is wired correctly before adding real tools in later phases.

**Step 1: Write the failing unit test first**

Create `src/test/kotlin/org/sightech/memoryvault/mcp/SystemToolsTest.kt`:

```kotlin
package org.sightech.memoryvault.mcp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SystemToolsTest {

    private val tools = SystemTools()

    @Test
    fun `ping returns pong with timestamp`() {
        val result = tools.ping()
        assertTrue(result.startsWith("pong"), "Expected response to start with 'pong', got: $result")
    }

    @Test
    fun `getInfo returns application name and version`() {
        val result = tools.getInfo()
        assertTrue(result.contains("memoryVault"), "Expected result to contain app name")
        assertTrue(result.contains("0.0.1"), "Expected result to contain version")
    }
}
```

**Step 2: Run the test to verify it fails**

```bash
./gradlew test --tests "org.sightech.memoryvault.mcp.SystemToolsTest"
```

Expected: FAIL — `SystemTools` class does not exist yet.

**Step 3: Create the SystemTools class**

Create `src/main/kotlin/org/sightech/memoryvault/mcp/SystemTools.kt`:

```kotlin
package org.sightech.memoryvault.mcp

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class SystemTools {

    @Tool(description = "Ping the MemoryVault server to verify it is running. Returns 'pong' with a timestamp.")
    fun ping(): String = "pong at ${Instant.now()}"

    @Tool(description = "Get MemoryVault application name and version.")
    fun getInfo(): String = "memoryVault v0.0.1"
}
```

**Step 4: Run the tests to verify they pass**

```bash
./gradlew test --tests "org.sightech.memoryvault.mcp.SystemToolsTest"
```

Expected: PASS both tests.

**Step 5: Update the context loads test to use TestContainers**

Replace `src/test/kotlin/org/sightech/memoryvault/MemoryVaultApplicationTests.kt`:

```kotlin
package org.sightech.memoryvault

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest
class MemoryVaultApplicationTests {

    companion object {
        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
            withDatabaseName("memoryvault_test")
            withUsername("memoryvault")
            withPassword("memoryvault")
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Test
    fun `application context loads`() {
        // If this passes, Spring context + DB + MCP server all wired correctly
    }
}
```

**Step 6: Run the full test suite**

```bash
./gradlew test
```

Expected: All tests pass. TestContainers will pull and start a PostgreSQL container automatically.

**Step 7: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/mcp/SystemTools.kt
git add src/test/kotlin/org/sightech/memoryvault/mcp/SystemToolsTest.kt
git add src/test/kotlin/org/sightech/memoryvault/MemoryVaultApplicationTests.kt
git commit -m "feat: add MCP server skeleton with ping/info tools and TestContainers integration test"
```

---

## Task 6: Create the `/scaffold-entity` Skill

**Files:**
- Create: `.claude/skills/scaffold-entity.md`

**Step 1: Create the skills directory**

```bash
mkdir -p .claude/skills
```

**Step 2: Create the skill file**

Create `.claude/skills/scaffold-entity.md`:

````markdown
---
name: scaffold-entity
description: Scaffold a new JPA entity with repository, service, and REST controller stub for MemoryVault. Use when adding a new domain entity to the project.
---

# Scaffold Entity

You are scaffolding a new domain entity for the MemoryVault project.

## Package Structure

All domain code lives under `src/main/kotlin/org/sightech/memoryvault/<domain>/` where `<domain>` is a lowercase noun (e.g., `bookmark`, `feed`, `video`).

```
src/main/kotlin/org/sightech/memoryvault/
└── <domain>/
    ├── entity/        <Name>.kt
    ├── repository/    <Name>Repository.kt
    ├── service/       <Name>Service.kt
    └── controller/    <Name>Controller.kt
```

Test mirror: `src/test/kotlin/org/sightech/memoryvault/<domain>/`

## Steps

### 1. Create the Entity

File: `src/main/kotlin/org/sightech/memoryvault/<domain>/entity/<Name>.kt`

```kotlin
package org.sightech.memoryvault.<domain>.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "<name_snake_case>s")
class <Name>(
    @Id
    val id: UUID = UUID.randomUUID(),

    // --- domain fields here ---

    val createdAt: Instant = Instant.now(),

    var updatedAt: Instant = Instant.now(),

    var deletedAt: Instant? = null,

    @Version
    val version: Long = 0
)
```

### 2. Create the Repository

File: `src/main/kotlin/org/sightech/memoryvault/<domain>/repository/<Name>Repository.kt`

```kotlin
package org.sightech.memoryvault.<domain>.repository

import org.sightech.memoryvault.<domain>.entity.<Name>
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface <Name>Repository : JpaRepository<<Name>, UUID> {

    @Query("SELECT e FROM <Name> e WHERE e.deletedAt IS NULL")
    fun findAllActive(): List<<Name>>
}
```

### 3. Create the Service (write failing tests first)

Test file: `src/test/kotlin/org/sightech/memoryvault/<domain>/service/<Name>ServiceTest.kt`

```kotlin
package org.sightech.memoryvault.<domain>.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.<domain>.repository.<Name>Repository
import kotlin.test.assertNotNull

class <Name>ServiceTest {

    private val repository = mockk<<Name>Repository>()
    private val service = <Name>Service(repository)

    @Test
    fun `findAll returns only non-deleted entities`() {
        every { repository.findAllActive() } returns emptyList()
        val result = service.findAll()
        verify { repository.findAllActive() }
        assertNotNull(result)
    }
}
```

Run test to verify it fails: `./gradlew test --tests "*<Name>ServiceTest"`

Then create `src/main/kotlin/org/sightech/memoryvault/<domain>/service/<Name>Service.kt`:

```kotlin
package org.sightech.memoryvault.<domain>.service

import org.sightech.memoryvault.<domain>.entity.<Name>
import org.sightech.memoryvault.<domain>.repository.<Name>Repository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class <Name>Service(private val repository: <Name>Repository) {

    fun findAll(): List<<Name>> = repository.findAllActive()

    fun findById(id: UUID): <Name>? = repository.findById(id).orElse(null)
}
```

Run tests to verify they pass: `./gradlew test --tests "*<Name>ServiceTest"`

### 4. Create the Controller Stub

File: `src/main/kotlin/org/sightech/memoryvault/<domain>/controller/<Name>Controller.kt`

```kotlin
package org.sightech.memoryvault.<domain>.controller

import org.sightech.memoryvault.<domain>.service.<Name>Service
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/<name_kebab_case>s")
class <Name>Controller(private val service: <Name>Service) {

    @GetMapping
    fun findAll() = service.findAll()

    @GetMapping("/{id}")
    fun findById(@PathVariable id: UUID) = service.findById(id)
}
```

### 5. Create a Flyway Migration

File: `src/main/resources/db/migration/V<N>__add_<name_snake_case>.sql`

Use the next available version number. Check existing migrations with:
```bash
ls src/main/resources/db/migration/
```

```sql
CREATE TABLE <name_snake_case>s (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    -- domain columns here --
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0
);
```

### 6. Commit

```bash
git add src/
git commit -m "feat: scaffold <Name> entity, repository, service, and controller"
```
````

**Step 3: Verify the skill file is readable**

```bash
cat .claude/skills/scaffold-entity.md | head -5
```

Expected: prints the frontmatter header.

**Step 4: Commit**

```bash
git add .claude/skills/scaffold-entity.md
git commit -m "feat: add /scaffold-entity Claude Code skill"
```

---

## Task 7: Create the `/add-mcp-tool` Skill

**Files:**
- Create: `.claude/skills/add-mcp-tool.md`

**Step 1: Create the skill file**

Create `.claude/skills/add-mcp-tool.md`:

````markdown
---
name: add-mcp-tool
description: Add a new Spring AI MCP tool to MemoryVault. Use when implementing one of the MCP tools defined in the design doc. Tools must be annotated with @Tool and wired to the service layer.
---

# Add MCP Tool

You are adding a new `@Tool` annotated method to the MemoryVault MCP server.

## Where MCP Tools Live

All MCP tool classes live in `src/main/kotlin/org/sightech/memoryvault/mcp/`.

One class per domain:
- `BookmarkTools.kt` — bookmark tools
- `FeedTools.kt` — RSS feed tools
- `YoutubeTools.kt` — YouTube archival tools
- `SystemTools.kt` — ping, stats, logs, cost tools

## Steps

### 1. Write the failing test first

Test file: `src/test/kotlin/org/sightech/memoryvault/mcp/<Domain>ToolsTest.kt`

```kotlin
@Test
fun `<tool name> returns expected result`() {
    // Arrange
    every { service.<method>(...) } returns <expected>

    // Act
    val result = tools.<toolMethod>(...)

    // Assert
    assertEquals(<expected>, result)
    verify { service.<method>(...) }
}
```

Run: `./gradlew test --tests "*<Domain>ToolsTest"`
Expected: FAIL — method does not exist yet.

### 2. Add the @Tool method

In the appropriate `*Tools.kt` class:

```kotlin
@Tool(description = "<Clear, user-facing description of what this tool does. This is what Claude reads.>")
fun <toolName>(<param>: <Type>): <ReturnType> {
    return service.<method>(<param>)
}
```

Rules:
- Description must be clear enough for Claude to know when to call this tool
- Parameters must be Kotlin types (String, Int, Boolean, List<String>, UUID)
- Return simple types or data classes — avoid JPA entities directly (use DTOs or maps)
- Nullable parameters mean "optional" to Claude

### 3. Run the tests

```bash
./gradlew test --tests "*<Domain>ToolsTest"
```

Expected: PASS.

### 4. Manually verify in Claude Desktop (optional)

If Claude Desktop is configured with MemoryVault's MCP server, restart it and ask Claude to call the new tool by name. Verify the response looks correct.

### 5. Commit

```bash
git add src/main/kotlin/org/sightech/memoryvault/mcp/<Domain>Tools.kt
git add src/test/kotlin/org/sightech/memoryvault/mcp/<Domain>ToolsTest.kt
git commit -m "feat: add <toolName> MCP tool"
```
````

**Step 2: Commit**

```bash
git add .claude/skills/add-mcp-tool.md
git commit -m "feat: add /add-mcp-tool Claude Code skill"
```

---

## Task 8: Create the `/add-lambda` Skill

**Files:**
- Create: `.claude/skills/add-lambda.md`

**Step 1: Create the skill file**

Create `.claude/skills/add-lambda.md`:

````markdown
---
name: add-lambda
description: Scaffold a new AWS Lambda function for MemoryVault scheduled sync jobs (RSS fetch, YouTube sync, link health check, etc.). Creates the function code, Terraform resource, and local test harness.
---

# Add Lambda Function

You are adding a new AWS Lambda function to MemoryVault. Lambda functions handle async, scheduled work — they are triggered by EventBridge on a schedule, do their job, and write results back to PostgreSQL or S3.

## Where Lambda Functions Live

```
lambdas/
└── <function-name>/
    ├── src/
    │   └── main.py       # handler entry point
    ├── tests/
    │   └── test_main.py  # pytest tests
    ├── requirements.txt
    └── README.md         # trigger schedule, inputs, outputs
```

Terraform resources live in `terraform/lambdas/<function-name>.tf`.

## Steps

### 1. Write the failing test first

Create `lambdas/<function-name>/tests/test_main.py`:

```python
import pytest
from unittest.mock import patch, MagicMock
from src.main import handler

def test_handler_returns_success_on_valid_event():
    event = {}  # EventBridge sends an empty-ish event
    context = MagicMock()
    result = handler(event, context)
    assert result["statusCode"] == 200

def test_handler_logs_errors_and_returns_failure():
    with patch("src.main.<dependency>", side_effect=Exception("boom")):
        result = handler({}, MagicMock())
    assert result["statusCode"] == 500
    assert "error" in result["body"]
```

Run: `cd lambdas/<function-name> && python -m pytest tests/ -v`
Expected: FAIL — `src.main` does not exist.

### 2. Create the handler

Create `lambdas/<function-name>/src/main.py`:

```python
import json
import logging
import os

logger = logging.getLogger()
logger.setLevel(logging.INFO)

def handler(event, context):
    """
    <Describe what this Lambda does, what triggers it, what it writes.>
    EventBridge schedule: <cron or rate expression>
    """
    try:
        logger.info("Starting <function-name>", extra={"event": event})
        # --- implementation here ---
        return {"statusCode": 200, "body": json.dumps({"status": "ok"})}
    except Exception as e:
        logger.error("Failed: %s", str(e), exc_info=True)
        return {"statusCode": 500, "body": json.dumps({"error": str(e)})}
```

Run: `python -m pytest tests/ -v`
Expected: PASS.

### 3. Add requirements.txt

```
# lambdas/<function-name>/requirements.txt
psycopg2-binary==2.9.9
boto3==1.34.0
```

### 4. Add Terraform resource

Create `terraform/lambdas/<function-name>.tf`:

```hcl
resource "aws_lambda_function" "<function_name>" {
  function_name = "${var.environment}-memoryvault-<function-name>"
  role          = aws_iam_role.lambda_exec.arn
  handler       = "src.main.handler"
  runtime       = "python3.11"
  timeout       = 300  # adjust per function needs

  filename         = data.archive_file.<function_name>_zip.output_path
  source_code_hash = data.archive_file.<function_name>_zip.output_base64sha256

  environment {
    variables = {
      DB_HOST     = var.db_host
      DB_NAME     = var.db_name
      DB_USER     = var.db_user
      DB_PASSWORD = var.db_password
    }
  }
}

data "archive_file" "<function_name>_zip" {
  type        = "zip"
  source_dir  = "${path.root}/../lambdas/<function-name>"
  output_path = "${path.root}/zips/<function-name>.zip"
}

resource "aws_cloudwatch_event_rule" "<function_name>_schedule" {
  name                = "${var.environment}-<function-name>-schedule"
  schedule_expression = "rate(1 hour)"  # adjust as needed
}

resource "aws_cloudwatch_event_target" "<function_name>_target" {
  rule      = aws_cloudwatch_event_rule.<function_name>_schedule.name
  target_id = "<function_name>"
  arn       = aws_lambda_function.<function_name>.arn
}

resource "aws_lambda_permission" "allow_eventbridge_<function_name>" {
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.<function_name>.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.<function_name>_schedule.arn
}
```

### 5. Commit

```bash
git add lambdas/<function-name>/ terraform/lambdas/<function-name>.tf
git commit -m "feat: add <function-name> Lambda function with Terraform and tests"
```
````

**Step 2: Commit**

```bash
git add .claude/skills/add-lambda.md
git commit -m "feat: add /add-lambda Claude Code skill"
```

---

## Task 9: Create the `/add-content-processor` Skill

**Files:**
- Create: `.claude/skills/add-content-processor.md`

**Step 1: Create the skill file**

Create `.claude/skills/add-content-processor.md`:

````markdown
---
name: add-content-processor
description: Scaffold a new Python content processor module for MemoryVault. Use when adding a new processing capability to the content-processor service (yt-dlp downloader, RSS parser, web scraper, page archiver, etc.).
---

# Add Content Processor

You are adding a new Python processing module to the MemoryVault content-processor service.

## Where Processors Live

```
content-processor/
├── src/
│   └── <processor_name>/
│       ├── __init__.py
│       └── processor.py      # main logic
├── tests/
│   └── <processor_name>/
│       ├── __init__.py
│       └── test_processor.py
└── requirements.txt
```

## Steps

### 1. Write the failing test first

Create `content-processor/tests/<processor_name>/test_processor.py`:

```python
import pytest
from unittest.mock import patch, MagicMock
from src.<processor_name>.processor import <ProcessorName>

class Test<ProcessorName>:

    def test_process_returns_result_on_valid_input(self):
        processor = <ProcessorName>()
        result = processor.process("<valid input>")
        assert result is not None
        assert result["status"] == "success"

    def test_process_raises_on_invalid_input(self):
        processor = <ProcessorName>()
        with pytest.raises(ValueError):
            processor.process("")

    def test_process_handles_network_error(self):
        processor = <ProcessorName>()
        with patch("<dependency>", side_effect=ConnectionError("network down")):
            result = processor.process("<input>")
        assert result["status"] == "error"
        assert "network" in result["error"].lower()
```

Run: `cd content-processor && python -m pytest tests/<processor_name>/ -v`
Expected: FAIL — module does not exist.

### 2. Create the processor module

Create `content-processor/src/<processor_name>/__init__.py` (empty).

Create `content-processor/src/<processor_name>/processor.py`:

```python
import logging
from typing import Any

logger = logging.getLogger(__name__)

class <ProcessorName>:
    """
    <What this processor does, what it takes as input, what it returns.>
    """

    def process(self, input: str) -> dict[str, Any]:
        """
        Process <input type> and return a result dict with 'status' key.
        Status is 'success' or 'error'.
        """
        if not input:
            raise ValueError("Input cannot be empty")

        try:
            logger.info("Processing: %s", input)
            # --- implementation here ---
            return {"status": "success", "result": None}
        except Exception as e:
            logger.error("Processing failed: %s", str(e), exc_info=True)
            return {"status": "error", "error": str(e)}
```

Run: `python -m pytest tests/<processor_name>/ -v`
Expected: PASS.

### 3. Add dependencies

If new packages are needed, add to `content-processor/requirements.txt`:

```
# existing deps...
<new-package>==<version>  # used by <processor_name>
```

### 4. Async variant (if needed)

If the processor is I/O-bound (network calls, S3 uploads), make it async. Refer to the `async-python-patterns` skill for guidance.

### 5. Commit

```bash
git add content-processor/src/<processor_name>/ content-processor/tests/<processor_name>/
git commit -m "feat: add <ProcessorName> content processor with tests"
```
````

**Step 2: Commit**

```bash
git add .claude/skills/add-content-processor.md
git commit -m "feat: add /add-content-processor Claude Code skill"
```

---

## Task 10: Create the `scripts/` Directory with Dev Scripts

**Files:**
- Create: `scripts/test-all.sh`
- Create: `scripts/smoke-test.sh`
- Create: `scripts/logs.sh`
- Create: `scripts/reset-db.sh`

**Step 1: Create scripts directory**

```bash
mkdir -p scripts
```

**Step 2: Create test-all.sh**

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "=== MemoryVault: Running all tests ==="

echo ""
echo "--- Kotlin/Spring tests ---"
./gradlew test

echo ""
echo "--- Content processor tests (if exists) ---"
if [ -d "content-processor" ]; then
  cd content-processor && python -m pytest --tb=short && cd ..
else
  echo "content-processor not yet created, skipping."
fi

echo ""
echo "--- Lambda tests (if exist) ---"
if [ -d "lambdas" ]; then
  for dir in lambdas/*/; do
    echo "Testing $dir..."
    cd "$dir" && python -m pytest --tb=short && cd ../..
  done
else
  echo "lambdas/ not yet created, skipping."
fi

echo ""
echo "=== All tests passed ==="
```

**Step 3: Create smoke-test.sh**

```bash
#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0

check() {
  local name="$1"
  local url="$2"
  local expected_status="${3:-200}"

  status=$(curl -s -o /dev/null -w "%{http_code}" "$url")
  if [ "$status" -eq "$expected_status" ]; then
    echo "  PASS  $name ($status)"
    ((PASS++))
  else
    echo "  FAIL  $name (expected $expected_status, got $status)"
    ((FAIL++))
  fi
}

echo "=== MemoryVault smoke test against $BASE_URL ==="
echo ""

check "Health endpoint" "$BASE_URL/actuator/health"

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ] || exit 1
```

**Step 4: Create logs.sh**

```bash
#!/usr/bin/env bash
# Usage: ./scripts/logs.sh [level] [lines]
# Example: ./scripts/logs.sh ERROR 50

LEVEL="${1:-INFO}"
LINES="${2:-100}"
LOG_FILE="logs/memoryvault.log"

if [ -f "$LOG_FILE" ]; then
  grep "\"level\":\"$LEVEL\"" "$LOG_FILE" | tail -n "$LINES" | python3 -m json.tool 2>/dev/null || tail -n "$LINES" "$LOG_FILE"
else
  echo "No log file found at $LOG_FILE. Is the app running?"
  echo "To view live logs: ./gradlew bootRun"
fi
```

**Step 5: Create reset-db.sh**

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "WARNING: This will drop and recreate the memoryvault database."
read -p "Are you sure? (yes/N): " confirm

if [ "$confirm" != "yes" ]; then
  echo "Aborted."
  exit 0
fi

echo "Resetting database..."
docker compose exec postgres psql -U memoryvault -c "DROP DATABASE IF EXISTS memoryvault;"
docker compose exec postgres psql -U memoryvault -c "CREATE DATABASE memoryvault;"
echo "Database reset. Flyway will re-run migrations on next app start."
```

**Step 6: Make scripts executable**

```bash
chmod +x scripts/*.sh
```

**Step 7: Run test-all.sh to verify it works**

```bash
./scripts/test-all.sh
```

Expected: Kotlin tests pass, other sections print "skipping" messages.

**Step 8: Commit**

```bash
git add scripts/
git commit -m "feat: add dev scripts for testing, smoke testing, log viewing, and DB reset"
```

---

## Task 11: Add Spring Boot Actuator and Verify Smoke Test

**Files:**
- Modify: `build.gradle.kts`
- Modify: `src/main/resources/application.properties`

The smoke test calls `/actuator/health`. Add the Actuator dependency and expose the health endpoint.

**Step 1: Add actuator dependency**

In `build.gradle.kts`, add to the `dependencies` block:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

**Step 2: Configure actuator in application.properties**

Add:

```properties
# Actuator
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=when-authorized
```

**Step 3: Start the app and run the smoke test**

```bash
./gradlew bootRun &
sleep 10
./scripts/smoke-test.sh
```

Expected: `PASS  Health endpoint (200)`.

**Step 4: Stop the background app**

```bash
kill %1
```

**Step 5: Run the full test suite one final time**

```bash
./scripts/test-all.sh
```

Expected: All tests pass.

**Step 6: Commit**

```bash
git add build.gradle.kts src/main/resources/application.properties
git commit -m "feat: add Spring Boot Actuator with health endpoint for smoke tests"
```

---

## Phase 0 Complete

At the end of Phase 0 you have:

- PostgreSQL running via Docker Compose
- Spring Boot app connecting to PostgreSQL with Flyway managing migrations
- Spring AI MCP server with a working `ping` and `getInfo` tool
- TestContainers integration test proving the full context loads
- 4 custom Claude Code skills: `/scaffold-entity`, `/add-mcp-tool`, `/add-lambda`, `/add-content-processor`
- `scripts/` with `test-all.sh`, `smoke-test.sh`, `logs.sh`, `reset-db.sh`
- Spring Boot Actuator health endpoint

**Next:** Phase 1 — Bookmarks. Use `/scaffold-entity` to generate the Bookmark entity, then `/add-mcp-tool` for each bookmark tool.
