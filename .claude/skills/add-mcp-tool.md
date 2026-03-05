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

Each class is a `@Component` bean (not `@Service` — tool classes are infrastructure adapters, not domain services).

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

In the appropriate `*Tools.kt` class, import `org.springframework.ai.tool.annotation.Tool`:

```kotlin
@Tool(description = "<Clear, user-facing description of what this tool does and when to call it. This is what Claude reads.>")
fun <toolName>(<param>: <Type>): <ReturnType> {
    return service.<method>(<param>)
}
```

Rules:
- Description must explain WHAT the tool does AND WHEN to use it
- Parameters must be Kotlin types (String, Int, Boolean, List<String>, UUID)
- Return simple types or data classes — avoid JPA entities directly (create a DTO or Map)
- Nullable parameters mean "optional" to Claude

### 3. Run the tests

```bash
./gradlew test --tests "*<Domain>ToolsTest"
```

Expected: PASS.

### 4. Commit

```bash
git add src/main/kotlin/org/sightech/memoryvault/mcp/<Domain>Tools.kt
git add src/test/kotlin/org/sightech/memoryvault/mcp/<Domain>ToolsTest.kt
git commit -m "feat: add <toolName> MCP tool"
```
