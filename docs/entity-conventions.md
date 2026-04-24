# Entity Design Conventions

Follow these rules for every JPA entity in MemoryVault.

## Enums for discrete values

Any field with a fixed set of values (status, type, role, category, trigger source, etc.) **MUST** be a Kotlin enum with `@Enumerated(EnumType.STRING)`. Never use raw `String` for discrete values.

```kotlin
enum class BookmarkStatus { ACTIVE, ARCHIVED, FAILED }

@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
val status: BookmarkStatus = BookmarkStatus.ACTIVE,
```

Define enums in the same entity file if they're entity-specific, or in a shared package if used across entities.

## JSONB columns

For flexible/schemaless data, use `@JdbcTypeCode(SqlTypes.JSON)` alongside `@Column(columnDefinition = "jsonb")`:

```kotlin
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
var metadata: String? = null
```

## Standard field patterns

- **Primary key**: `UUID`, generated in Kotlin (`UUID.randomUUID()`)
- **Foreign keys**: Use `@ManyToOne` / `@OneToMany` JPA relationships, not raw UUID columns
- **Timestamps**: `Instant` (not `LocalDateTime`) — always UTC
- **Soft delete**: `deletedAt: Instant?` — null means active
- **Optimistic locking**: `@Version val version: Long = 0`
- **User ownership**: `userId: UUID` (foreign key to `users` table)
- **Mutable vs immutable**: Use `val` for fields set at creation, `var` for fields that change

## Column annotations

- Always set `nullable` explicitly on `@Column`
- Set `length` on `@Column` for string/enum fields
- Set `columnDefinition` for PostgreSQL-specific types (TEXT, jsonb)
