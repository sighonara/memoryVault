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

## Entity Design Conventions

Follow these rules for every entity field:

### Enums for discrete values
Any field with a fixed set of values (status, type, role, category, trigger source, etc.) **MUST** be a Kotlin enum with `@Enumerated(EnumType.STRING)`. Never use raw `String` for discrete values.

```kotlin
enum class BookmarkStatus { ACTIVE, ARCHIVED, FAILED }

@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
val status: BookmarkStatus = BookmarkStatus.ACTIVE,
```

Define enums in the same entity file if they're entity-specific, or in a shared package if used across entities.

### JSONB columns
For flexible/schemaless data, use `@JdbcTypeCode(SqlTypes.JSON)` alongside `@Column(columnDefinition = "jsonb")`:

```kotlin
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
var metadata: String? = null
```

### Standard field patterns
- **Primary key**: `UUID`, generated in Kotlin (`UUID.randomUUID()`)
- **Foreign keys**: Use `@ManyToOne` / `@OneToMany` JPA relationships, not raw UUID columns
- **Timestamps**: `Instant` (not `LocalDateTime`) — always UTC
- **Soft delete**: `deletedAt: Instant?` — null means active
- **Optimistic locking**: `@Version val version: Long = 0`
- **User ownership**: `userId: UUID` (foreign key to `users` table)
- **Mutable vs immutable**: Use `val` for fields set at creation, `var` for fields that change

### Column annotations
- Always set `nullable` explicitly on `@Column`
- Set `length` on `@Column` for string/enum fields
- Set `columnDefinition` for PostgreSQL-specific types (TEXT, jsonb)

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

    // --- domain fields here (use enums for discrete values!) ---

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

Check the next available version number:
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
