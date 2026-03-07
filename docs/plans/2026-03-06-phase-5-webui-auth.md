# Phase 5: Web UI + Auth Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add JWT authentication, a schema-first GraphQL API, and an Angular Material frontend (Google Reader-style feed reader, bookmarks, YouTube, admin, global search) to MemoryVault.

**Architecture:** Angular SPA communicates with Spring Boot via GraphQL (Apollo Client + schema-first Spring for GraphQL). JWT auth is local now with a clean swap path to AWS Cognito. A `CurrentUser` abstraction extracts the authenticated user from the security context so all services become multi-tenant aware.

**Tech Stack:** Spring Boot 4.x, Spring for GraphQL, spring-boot-starter-oauth2-resource-server, jjwt, Angular 21, Angular Material, Apollo Angular, graphql-codegen, Vitest, Playwright

---

## Context

**Key files to understand before starting:**
- Design doc: `docs/plans/2026-03-06-phase-5-webui-auth-design.md`
- Current security: `src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt` (HTTP Basic, permits actuator)
- User table exists in DB (V2 migration) but has NO Kotlin entity yet
- Seed user: UUID `00000000-0000-0000-0000-000000000001`, email `system@memoryvault.local`
- All services currently use hardcoded `SYSTEM_USER_ID` — must be replaced with authenticated user
- Angular client is a skeleton: empty routes, no components, Karma/Jasmine testing
- Existing services: BookmarkService, FeedService, FeedItemService, YoutubeListService, VideoService, SearchService, StatsService, SyncJobService, LogService

**Package structure for new code:**
```
src/main/kotlin/org/sightech/memoryvault/
├── auth/              # NEW: User entity, JWT service, login controller
│   ├── entity/
│   ├── repository/
│   ├── service/
│   └── controller/
├── graphql/           # NEW: GraphQL resolvers
└── config/            # MODIFY: SecurityConfig

src/main/resources/
├── graphql/           # NEW: .graphqls schema files

client/src/app/
├── auth/              # NEW: login, auth service, guard
├── reader/            # NEW: feed reader (home)
├── bookmarks/         # NEW
├── youtube/           # NEW
├── admin/             # NEW: jobs, logs, stats
├── search/            # NEW: global search results
├── shared/            # NEW: layout, models, graphql
```

**Commit message convention:** Use `git commit -m "message"` with a plain string. Never use `$()`, heredoc, or subshell patterns in commit commands.

---

### Task 1: User Entity + Auth Service (Backend Foundation)

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/auth/entity/User.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/auth/entity/UserRole.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/auth/repository/UserRepository.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/auth/service/UserService.kt`
- Create: `src/main/resources/db/migration/V4__auth_password.sql`
- Test: `src/test/kotlin/org/sightech/memoryvault/auth/service/UserServiceTest.kt`

**Step 1: Create the User entity and role enum**

`src/main/kotlin/org/sightech/memoryvault/auth/entity/UserRole.kt`:
```kotlin
package org.sightech.memoryvault.auth.entity

enum class UserRole { OWNER, ADMIN, VIEWER }
```

`src/main/kotlin/org/sightech/memoryvault/auth/entity/User.kt`:
```kotlin
package org.sightech.memoryvault.auth.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(name = "password_hash")
    var passwordHash: String? = null,

    @Column(name = "display_name", nullable = false)
    var displayName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val role: UserRole = UserRole.OWNER,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    @Version
    val version: Long = 0
)
```

**Step 2: Create the repository**

`src/main/kotlin/org/sightech/memoryvault/auth/repository/UserRepository.kt`:
```kotlin
package org.sightech.memoryvault.auth.repository

import org.sightech.memoryvault.auth.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmailAndDeletedAtIsNull(email: String): User?
}
```

**Step 3: Write the failing test**

`src/test/kotlin/org/sightech/memoryvault/auth/service/UserServiceTest.kt`:
```kotlin
package org.sightech.memoryvault.auth.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.repository.UserRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val service = UserService(userRepository)

    @Test
    fun `findByEmail returns user when found`() {
        val user = User(email = "test@example.com", displayName = "Test")
        every { userRepository.findByEmailAndDeletedAtIsNull("test@example.com") } returns user

        val result = service.findByEmail("test@example.com")
        assertNotNull(result)
        assertEquals("test@example.com", result.email)
    }

    @Test
    fun `findByEmail returns null when not found`() {
        every { userRepository.findByEmailAndDeletedAtIsNull("missing@example.com") } returns null

        val result = service.findByEmail("missing@example.com")
        assertNull(result)
    }

    @Test
    fun `findById returns user`() {
        val id = UUID.randomUUID()
        val user = User(id = id, email = "test@example.com", displayName = "Test")
        every { userRepository.findById(id) } returns java.util.Optional.of(user)

        val result = service.findById(id)
        assertNotNull(result)
        assertEquals(id, result.id)
    }
}
```

**Step 4: Run test to verify it fails**

Run: `./gradlew test --tests "*UserServiceTest" 2>&1 | tail -5`
Expected: FAIL (UserService class doesn't exist)

**Step 5: Write UserService**

`src/main/kotlin/org/sightech/memoryvault/auth/service/UserService.kt`:
```kotlin
package org.sightech.memoryvault.auth.service

import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.repository.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserService(private val userRepository: UserRepository) {

    fun findByEmail(email: String): User? =
        userRepository.findByEmailAndDeletedAtIsNull(email)

    fun findById(id: UUID): User? =
        userRepository.findById(id).orElse(null)
}
```

**Step 6: Run test to verify it passes**

Run: `./gradlew test --tests "*UserServiceTest" 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 7: Create the V4 migration**

`src/main/resources/db/migration/V4__auth_password.sql`:

The `users` table already has `password_hash` and `role` columns from V2. The seed user has a placeholder password hash. This migration updates the seed user with a real BCrypt hash for local dev (password: `memoryvault`):

```sql
-- Update seed user with BCrypt hash for password 'memoryvault'
-- Generated with: BCrypt.hashpw("memoryvault", BCrypt.gensalt())
UPDATE users
SET password_hash = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'
WHERE id = '00000000-0000-0000-0000-000000000001';
```

Note: The BCrypt hash above is a placeholder. Generate a real one at runtime in Step 8 or use an online BCrypt generator. The exact hash value doesn't matter for dev as long as it's a valid BCrypt hash of "memoryvault".

**Step 8: Run the full test suite to verify migration doesn't break anything**

Run: `./gradlew test 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 9: Commit**

```
git add src/main/kotlin/org/sightech/memoryvault/auth/ src/test/kotlin/org/sightech/memoryvault/auth/ src/main/resources/db/migration/V4__auth_password.sql
git commit -m "feat: User entity, repository, service, and V4 migration for auth"
```

---

### Task 2: JWT Token Service

**Files:**
- Modify: `build.gradle.kts` (add jjwt dependencies)
- Create: `src/main/kotlin/org/sightech/memoryvault/auth/service/JwtService.kt`
- Modify: `src/main/resources/application.properties` (add JWT config)
- Test: `src/test/kotlin/org/sightech/memoryvault/auth/service/JwtServiceTest.kt`

**Step 1: Add jjwt dependencies to build.gradle.kts**

Add to the `dependencies` block:
```kotlin
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
```

**Step 2: Add JWT config to application.properties**

```properties
# JWT Configuration
memoryvault.jwt.secret=dev-secret-key-change-in-production-must-be-at-least-256-bits-long!!
memoryvault.jwt.expiration-hours=24
```

**Step 3: Write the failing test**

`src/test/kotlin/org/sightech/memoryvault/auth/service/JwtServiceTest.kt`:
```kotlin
package org.sightech.memoryvault.auth.service

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JwtServiceTest {

    private val secret = "test-secret-key-that-is-at-least-256-bits-long-for-hs256-signing!!"
    private val service = JwtService(secret, 24)

    @Test
    fun `generateToken creates valid JWT`() {
        val userId = UUID.randomUUID()
        val token = service.generateToken(userId, "test@example.com", "OWNER")
        assertNotNull(token)
    }

    @Test
    fun `validateToken extracts correct claims`() {
        val userId = UUID.randomUUID()
        val token = service.generateToken(userId, "test@example.com", "OWNER")

        val claims = service.validateToken(token)
        assertNotNull(claims)
        assertEquals(userId.toString(), claims["userId"])
        assertEquals("test@example.com", claims["email"])
        assertEquals("OWNER", claims["role"])
    }

    @Test
    fun `validateToken returns null for invalid token`() {
        val result = service.validateToken("invalid.token.here")
        assertNull(result)
    }

    @Test
    fun `validateToken returns null for tampered token`() {
        val userId = UUID.randomUUID()
        val token = service.generateToken(userId, "test@example.com", "OWNER")
        val tampered = token.dropLast(5) + "xxxxx"

        val result = service.validateToken(tampered)
        assertNull(result)
    }
}
```

**Step 4: Run test to verify it fails**

Run: `./gradlew test --tests "*JwtServiceTest" 2>&1 | tail -5`
Expected: FAIL

**Step 5: Implement JwtService**

`src/main/kotlin/org/sightech/memoryvault/auth/service/JwtService.kt`:
```kotlin
package org.sightech.memoryvault.auth.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${memoryvault.jwt.secret}") private val secret: String,
    @Value("\${memoryvault.jwt.expiration-hours}") private val expirationHours: Long
) {

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    fun generateToken(userId: UUID, email: String, role: String): String {
        val now = Date()
        val expiry = Date(now.time + expirationHours * 3600 * 1000)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("userId", userId.toString())
            .claim("email", email)
            .claim("role", role)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Map<String, String>? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload

            mapOf(
                "userId" to (claims["userId"] as String),
                "email" to (claims["email"] as String),
                "role" to (claims["role"] as String)
            )
        } catch (e: Exception) {
            null
        }
    }
}
```

**Step 6: Run test to verify it passes**

Run: `./gradlew test --tests "*JwtServiceTest" 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```
git add build.gradle.kts src/main/kotlin/org/sightech/memoryvault/auth/service/JwtService.kt src/test/kotlin/org/sightech/memoryvault/auth/service/JwtServiceTest.kt src/main/resources/application.properties
git commit -m "feat: JWT token service for local auth"
```

---

### Task 3: Login Endpoint + SecurityConfig Update

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/auth/controller/AuthController.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/auth/service/AuthService.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/auth/dto/LoginRequest.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/auth/dto/LoginResponse.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/config/JwtAuthenticationFilter.kt`
- Test: `src/test/kotlin/org/sightech/memoryvault/auth/service/AuthServiceTest.kt`

**Step 1: Create DTOs**

`src/main/kotlin/org/sightech/memoryvault/auth/dto/LoginRequest.kt`:
```kotlin
package org.sightech.memoryvault.auth.dto

data class LoginRequest(val email: String, val password: String)
```

`src/main/kotlin/org/sightech/memoryvault/auth/dto/LoginResponse.kt`:
```kotlin
package org.sightech.memoryvault.auth.dto

data class LoginResponse(val token: String, val email: String, val displayName: String, val role: String)
```

**Step 2: Write the failing AuthService test**

`src/test/kotlin/org/sightech/memoryvault/auth/service/AuthServiceTest.kt`:
```kotlin
package org.sightech.memoryvault.auth.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.entity.UserRole
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AuthServiceTest {

    private val userService = mockk<UserService>()
    private val jwtService = mockk<JwtService>()
    private val passwordEncoder = BCryptPasswordEncoder()
    private val authService = AuthService(userService, jwtService, passwordEncoder)

    @Test
    fun `login returns token for valid credentials`() {
        val hashedPassword = passwordEncoder.encode("password123")
        val user = User(email = "test@example.com", displayName = "Test User", passwordHash = hashedPassword)

        every { userService.findByEmail("test@example.com") } returns user
        every { jwtService.generateToken(user.id, "test@example.com", "OWNER") } returns "jwt-token"

        val response = authService.login("test@example.com", "password123")
        assertNotNull(response)
        assertEquals("jwt-token", response.token)
        assertEquals("test@example.com", response.email)
        assertEquals("Test User", response.displayName)
    }

    @Test
    fun `login throws for invalid email`() {
        every { userService.findByEmail("bad@example.com") } returns null

        assertThrows<IllegalArgumentException> {
            authService.login("bad@example.com", "password")
        }
    }

    @Test
    fun `login throws for wrong password`() {
        val hashedPassword = passwordEncoder.encode("correct")
        val user = User(email = "test@example.com", displayName = "Test", passwordHash = hashedPassword)

        every { userService.findByEmail("test@example.com") } returns user

        assertThrows<IllegalArgumentException> {
            authService.login("test@example.com", "wrong")
        }
    }
}
```

**Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "*AuthServiceTest" 2>&1 | tail -5`
Expected: FAIL

**Step 4: Implement AuthService**

`src/main/kotlin/org/sightech/memoryvault/auth/service/AuthService.kt`:
```kotlin
package org.sightech.memoryvault.auth.service

import org.sightech.memoryvault.auth.dto.LoginResponse
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

@Service
@Profile("!aws")
class AuthService(
    private val userService: UserService,
    private val jwtService: JwtService,
    private val passwordEncoder: BCryptPasswordEncoder
) {

    fun login(email: String, password: String): LoginResponse {
        val user = userService.findByEmail(email)
            ?: throw IllegalArgumentException("Invalid credentials")

        if (user.passwordHash == null || !passwordEncoder.matches(password, user.passwordHash)) {
            throw IllegalArgumentException("Invalid credentials")
        }

        val token = jwtService.generateToken(user.id, user.email, user.role.name)
        return LoginResponse(
            token = token,
            email = user.email,
            displayName = user.displayName,
            role = user.role.name
        )
    }
}
```

**Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "*AuthServiceTest" 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 6: Create the JWT authentication filter**

`src/main/kotlin/org/sightech/memoryvault/config/JwtAuthenticationFilter.kt`:
```kotlin
package org.sightech.memoryvault.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.sightech.memoryvault.auth.service.JwtService
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.substring(7)
            val claims = jwtService.validateToken(token)
            if (claims != null) {
                val authorities = listOf(SimpleGrantedAuthority("ROLE_${claims["role"]}"))
                val auth = UsernamePasswordAuthenticationToken(
                    claims["userId"],  // principal = userId string
                    null,
                    authorities
                )
                SecurityContextHolder.getContext().authentication = auth
            }
        }
        filterChain.doFilter(request, response)
    }
}
```

**Step 7: Create AuthController**

`src/main/kotlin/org/sightech/memoryvault/auth/controller/AuthController.kt`:
```kotlin
package org.sightech.memoryvault.auth.controller

import org.sightech.memoryvault.auth.dto.LoginRequest
import org.sightech.memoryvault.auth.dto.LoginResponse
import org.sightech.memoryvault.auth.service.AuthService
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
@Profile("!aws")
class AuthController(private val authService: AuthService) {

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        return try {
            val response = authService.login(request.email, request.password)
            ResponseEntity.ok(response)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(401).build()
        }
    }
}
```

**Step 8: Update SecurityConfig**

Replace the entire content of `src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt`:

```kotlin
package org.sightech.memoryvault.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/graphiql/**").permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun passwordEncoder() = BCryptPasswordEncoder()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = listOf("http://localhost:4200")
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
```

**Step 9: Add BCryptPasswordEncoder and JWT config to application-test.properties**

Check if `src/main/resources/application-test.properties` exists. Add the JWT config:

```properties
memoryvault.jwt.secret=test-secret-key-that-is-at-least-256-bits-long-for-hs256-signing!!
memoryvault.jwt.expiration-hours=24
```

**Step 10: Run full test suite**

Run: `./gradlew test 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

Note: Existing tests may fail because SecurityConfig now requires JWT and the tests use the `test` profile. If tests fail with 401 errors, add `@AutoConfigureMockMvc(addFilters = false)` to integration tests or configure test security. The common fix is to add `spring.security.enabled=false` or a test security config. Debug as needed.

**Step 11: Commit**

```
git add src/main/kotlin/org/sightech/memoryvault/auth/ src/main/kotlin/org/sightech/memoryvault/config/ src/test/kotlin/org/sightech/memoryvault/auth/ src/main/resources/
git commit -m "feat: JWT auth with login endpoint, security filter, and CORS"
```

---

### Task 4: CurrentUser Helper + Wire Into Services

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/auth/CurrentUser.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/bookmark/service/BookmarkService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/feed/service/FeedService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/feed/service/FeedItemService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/youtube/service/YoutubeListService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/youtube/service/VideoService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/search/SearchService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/stats/StatsService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/mcp/*.kt` (all MCP tool classes)

**Important context:** Currently, services either hardcode `SYSTEM_USER_ID` or take `userId` as a parameter from MCP tools that hardcode it. This task creates a `CurrentUser` helper and updates services. MCP tools continue to use the system user (MCP runs in a non-HTTP context).

**Step 1: Create CurrentUser helper**

`src/main/kotlin/org/sightech/memoryvault/auth/CurrentUser.kt`:
```kotlin
package org.sightech.memoryvault.auth

import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

object CurrentUser {

    val SYSTEM_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    fun userId(): UUID {
        val auth = SecurityContextHolder.getContext().authentication
        return if (auth != null && auth.principal is String) {
            try {
                UUID.fromString(auth.principal as String)
            } catch (e: IllegalArgumentException) {
                SYSTEM_USER_ID
            }
        } else {
            SYSTEM_USER_ID
        }
    }
}
```

**Step 2: Update services to accept userId parameter**

For each service that currently hardcodes the user ID, ensure the public methods accept `userId: UUID` as a parameter. Most services already do this (SearchService, StatsService, SyncJobService). Check each service and add the parameter where missing.

Key services to check and update:
- `BookmarkService` — check if `create`, `findAll`, `updateTags`, `softDelete`, `exportNetscapeHtml` accept userId. If they hardcode it, add the parameter.
- `FeedService` — check `addFeed`, `listFeeds`, `deleteFeed`, `refreshFeed`
- `FeedItemService` — check `getItems`, `markItemRead`, `markItemUnread`, `markFeedRead`
- `YoutubeListService` — check `addList`, `listLists`, `deleteList`, `refreshList`
- `VideoService` — check `getVideos`, `getVideoStatus`
- `TagService` — check if it needs userId

Read each file, identify where `SYSTEM_USER_ID` is used or where userId is absent, and add the parameter. Update the corresponding MCP tools to pass `CurrentUser.SYSTEM_USER_ID` (MCP tools run outside HTTP context so they always use the system user).

**Step 3: Update MCP tool classes**

For each MCP tool class (`BookmarkTools`, `FeedTools`, `YoutubeTools`, `CrossCuttingTools`), replace any hardcoded `SYSTEM_USER_ID` companion object with `CurrentUser.SYSTEM_USER_ID`:

```kotlin
import org.sightech.memoryvault.auth.CurrentUser

// In tool methods:
val userId = CurrentUser.SYSTEM_USER_ID
```

Remove the duplicate `SYSTEM_USER_ID` companion objects from `CrossCuttingTools` and `SpringJobScheduler`.

**Step 4: Update tests**

Run the full test suite. Fix any broken tests by passing the userId parameter where now required. Most test fixtures already use `UUID.fromString("00000000-0000-0000-0000-000000000001")`.

Run: `./gradlew test 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```
git add src/main/kotlin/org/sightech/memoryvault/
git commit -m "feat: CurrentUser helper, wire userId through all services"
```

---

### Task 5: GraphQL Setup + Shared Schema

**Files:**
- Modify: `build.gradle.kts` (add spring-boot-starter-graphql)
- Create: `src/main/resources/graphql/schema.graphqls`
- Modify: `src/main/resources/application.properties` (enable graphiql)
- Create: `src/test/kotlin/org/sightech/memoryvault/graphql/GraphQlSmokeTest.kt`

**Step 1: Add GraphQL dependency**

Add to `build.gradle.kts` dependencies:
```kotlin
implementation("org.springframework.boot:spring-boot-starter-graphql")
```

Add to test dependencies:
```kotlin
testImplementation("org.springframework.graphql:spring-graphql-test")
```

**Step 2: Create the shared schema**

`src/main/resources/graphql/schema.graphqls`:
```graphql
scalar UUID
scalar Instant

type Query {
    # Bookmarks
    bookmarks(query: String, tags: [String]): [Bookmark!]!

    # Feeds
    feeds: [FeedWithUnread!]!
    feedItems(feedId: UUID!, limit: Int, unreadOnly: Boolean): [FeedItem!]!

    # YouTube
    youtubeLists: [YoutubeListWithStats!]!
    videos(listId: UUID, query: String, removedOnly: Boolean): [Video!]!
    videoStatus(videoId: UUID!): Video

    # Admin
    jobs(type: String, limit: Int): [SyncJob!]!
    logs(level: String, service: String, limit: Int): [LogEntry!]!
    stats: SystemStats!

    # Search
    search(query: String!, types: [String]): [SearchResult!]!
}

type Mutation {
    # Auth - local only
    login(email: String!, password: String!): LoginResponse!

    # Bookmarks
    addBookmark(url: String!, title: String, tags: [String]): Bookmark!
    tagBookmark(id: UUID!, tags: [String!]!): Bookmark
    deleteBookmark(id: UUID!): Bookmark
    exportBookmarks: String!

    # Feeds
    addFeed(url: String!): Feed!
    deleteFeed(feedId: UUID!): Feed
    markItemRead(itemId: UUID!): FeedItem
    markItemUnread(itemId: UUID!): FeedItem
    markFeedRead(feedId: UUID!): Int!
    refreshFeed(feedId: UUID): [FeedRefreshResult!]!

    # YouTube
    addYoutubeList(url: String!): YoutubeListAddResult!
    deleteYoutubeList(listId: UUID!): YoutubeList
    refreshYoutubeList(listId: UUID): [SyncResult!]!
}
```

**Step 3: Create domain type schemas**

Create these additional schema files in `src/main/resources/graphql/`:

`bookmarks.graphqls`:
```graphql
type Bookmark {
    id: UUID!
    url: String!
    title: String
    tags: [Tag!]!
    createdAt: Instant!
    updatedAt: Instant!
}

type Tag {
    id: UUID!
    name: String!
    color: String
}
```

`feeds.graphqls`:
```graphql
type Feed {
    id: UUID!
    url: String!
    title: String
    description: String
    siteUrl: String
    lastFetchedAt: Instant
    failureCount: Int!
}

type FeedWithUnread {
    feed: Feed!
    unreadCount: Int!
}

type FeedItem {
    id: UUID!
    feedId: UUID!
    title: String
    url: String
    content: String
    author: String
    imageUrl: String
    publishedAt: Instant
    readAt: Instant
    tags: [Tag!]!
}

type FeedRefreshResult {
    feedId: UUID!
    feedTitle: String
    newItems: Int!
}
```

`youtube.graphqls`:
```graphql
type YoutubeList {
    id: UUID!
    youtubeListId: String!
    url: String!
    name: String
    description: String
    lastSyncedAt: Instant
    failureCount: Int!
}

type YoutubeListWithStats {
    list: YoutubeList!
    totalVideos: Int!
    downloadedVideos: Int!
    removedVideos: Int!
}

type YoutubeListAddResult {
    list: YoutubeList!
    newVideos: Int!
}

type Video {
    id: UUID!
    youtubeVideoId: String!
    youtubeUrl: String!
    title: String
    description: String
    channelName: String
    thumbnailPath: String
    filePath: String
    downloadedAt: Instant
    durationSeconds: Int
    removedFromYoutube: Boolean!
    removedDetectedAt: Instant
    tags: [Tag!]!
}

type SyncResult {
    listId: UUID!
    newVideos: Int!
    removedVideos: Int!
    downloadSuccesses: Int!
    downloadFailures: Int!
}
```

`admin.graphqls`:
```graphql
type SyncJob {
    id: UUID!
    type: String!
    status: String!
    startedAt: Instant!
    completedAt: Instant
    errorMessage: String
    triggeredBy: String!
    metadata: String
}

type LogEntry {
    timestamp: Instant!
    level: String!
    logger: String!
    message: String!
    thread: String!
}

type SystemStats {
    bookmarkCount: Int!
    feedCount: Int!
    feedItemCount: Int!
    unreadFeedItemCount: Int!
    youtubeListCount: Int!
    videoCount: Int!
    downloadedVideoCount: Int!
    removedVideoCount: Int!
    tagCount: Int!
    storageUsedBytes: Float!
    lastFeedSync: Instant
    lastYoutubeSync: Instant
    feedsWithFailures: Int!
    youtubeListsWithFailures: Int!
}

type SearchResult {
    type: String!
    id: UUID!
    title: String
    url: String
    rank: Float!
}

type LoginResponse {
    token: String!
    email: String!
    displayName: String!
    role: String!
}
```

**Step 4: Add graphiql config to application.properties**

```properties
# GraphQL
spring.graphql.graphiql.enabled=true
spring.graphql.schema.printer.enabled=true
```

**Step 5: Create scalar configuration**

`src/main/kotlin/org/sightech/memoryvault/graphql/ScalarConfig.kt`:
```kotlin
package org.sightech.memoryvault.graphql

import graphql.language.StringValue
import graphql.schema.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.graphql.execution.RuntimeWiringConfigurer
import java.time.Instant
import java.util.UUID

@Configuration
class ScalarConfig {

    @Bean
    fun runtimeWiringConfigurer(): RuntimeWiringConfigurer {
        return RuntimeWiringConfigurer { builder ->
            builder
                .scalar(uuidScalar())
                .scalar(instantScalar())
        }
    }

    private fun uuidScalar(): GraphQLScalarType {
        return GraphQLScalarType.newScalar()
            .name("UUID")
            .coercing(object : Coercing<UUID, String> {
                override fun serialize(dataFetcherResult: Any, graphQLContext: graphql.GraphQLContext, locale: java.util.Locale): String {
                    return (dataFetcherResult as UUID).toString()
                }

                override fun parseValue(input: Any, graphQLContext: graphql.GraphQLContext, locale: java.util.Locale): UUID {
                    return UUID.fromString(input as String)
                }

                override fun parseLiteral(input: graphql.language.Value<*>, variables: CoercedVariables, graphQLContext: graphql.GraphQLContext, locale: java.util.Locale): UUID {
                    return UUID.fromString((input as StringValue).value)
                }
            })
            .build()
    }

    private fun instantScalar(): GraphQLScalarType {
        return GraphQLScalarType.newScalar()
            .name("Instant")
            .coercing(object : Coercing<Instant, String> {
                override fun serialize(dataFetcherResult: Any, graphQLContext: graphql.GraphQLContext, locale: java.util.Locale): String {
                    return (dataFetcherResult as Instant).toString()
                }

                override fun parseValue(input: Any, graphQLContext: graphql.GraphQLContext, locale: java.util.Locale): Instant {
                    return Instant.parse(input as String)
                }

                override fun parseLiteral(input: graphql.language.Value<*>, variables: CoercedVariables, graphQLContext: graphql.GraphQLContext, locale: java.util.Locale): Instant {
                    return Instant.parse((input as StringValue).value)
                }
            })
            .build()
    }
}
```

**Step 6: Run full test suite to verify nothing breaks**

Run: `./gradlew test 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```
git add build.gradle.kts src/main/resources/graphql/ src/main/resources/application.properties src/main/kotlin/org/sightech/memoryvault/graphql/
git commit -m "feat: GraphQL setup with schema-first approach and custom scalars"
```

---

### Task 6: GraphQL Resolvers — Bookmarks + Feeds

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/graphql/BookmarkResolver.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/graphql/FeedResolver.kt`
- Test: `src/test/kotlin/org/sightech/memoryvault/graphql/BookmarkResolverTest.kt`
- Test: `src/test/kotlin/org/sightech/memoryvault/graphql/FeedResolverTest.kt`

**Step 1: Write failing BookmarkResolver test**

`src/test/kotlin/org/sightech/memoryvault/graphql/BookmarkResolverTest.kt`:
```kotlin
package org.sightech.memoryvault.graphql

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.bookmark.entity.Bookmark
import org.sightech.memoryvault.bookmark.service.BookmarkService
import java.util.UUID
import kotlin.test.assertEquals

class BookmarkResolverTest {

    private val bookmarkService = mockk<BookmarkService>()
    private val resolver = BookmarkResolver(bookmarkService)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `bookmarks query delegates to service`() {
        every { bookmarkService.findAll(any(), any(), any()) } returns emptyList()

        val result = resolver.bookmarks(null, null, userId)
        assertEquals(0, result.size)
        verify { bookmarkService.findAll(null, null, userId) }
    }

    @Test
    fun `addBookmark mutation delegates to service`() {
        val bookmark = Bookmark(userId = userId, url = "https://example.com")
        every { bookmarkService.create(any(), any(), any(), any()) } returns bookmark

        val result = resolver.addBookmark("https://example.com", null, null, userId)
        assertEquals("https://example.com", result.url)
    }
}
```

Note: The exact method signatures depend on how Task 4 updated BookmarkService. Adjust the test to match the actual service signatures (whether userId is a parameter or not). The resolver should always pass the authenticated userId.

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "*BookmarkResolverTest" 2>&1 | tail -5`
Expected: FAIL

**Step 3: Implement BookmarkResolver**

`src/main/kotlin/org/sightech/memoryvault/graphql/BookmarkResolver.kt`:
```kotlin
package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.bookmark.entity.Bookmark
import org.sightech.memoryvault.bookmark.service.BookmarkService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class BookmarkResolver(private val bookmarkService: BookmarkService) {

    @QueryMapping
    fun bookmarks(
        @Argument query: String?,
        @Argument tags: List<String>?,
        userId: UUID = CurrentUser.userId()
    ): List<Bookmark> {
        return bookmarkService.findAll(query, tags, userId)
    }

    @MutationMapping
    fun addBookmark(
        @Argument url: String,
        @Argument title: String?,
        @Argument tags: List<String>?,
        userId: UUID = CurrentUser.userId()
    ): Bookmark {
        return bookmarkService.create(url, title, tags, userId)
    }

    @MutationMapping
    fun tagBookmark(
        @Argument id: UUID,
        @Argument tags: List<String>
    ): Bookmark? {
        return bookmarkService.updateTags(id, tags)
    }

    @MutationMapping
    fun deleteBookmark(@Argument id: UUID): Bookmark? {
        return bookmarkService.softDelete(id)
    }

    @MutationMapping
    fun exportBookmarks(): String {
        return bookmarkService.exportNetscapeHtml()
    }
}
```

**Step 4: Write and implement FeedResolver similarly**

`src/main/kotlin/org/sightech/memoryvault/graphql/FeedResolver.kt`:
```kotlin
package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.feed.entity.Feed
import org.sightech.memoryvault.feed.entity.FeedItem
import org.sightech.memoryvault.feed.service.FeedItemService
import org.sightech.memoryvault.feed.service.FeedService
import kotlinx.coroutines.runBlocking
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.util.UUID

@Controller
class FeedResolver(
    private val feedService: FeedService,
    private val feedItemService: FeedItemService
) {

    @QueryMapping
    fun feeds(): List<Map<String, Any?>> {
        return feedService.listFeeds().map { (feed, unreadCount) ->
            mapOf("feed" to feed, "unreadCount" to unreadCount)
        }
    }

    @QueryMapping
    fun feedItems(
        @Argument feedId: UUID,
        @Argument limit: Int?,
        @Argument unreadOnly: Boolean?
    ): List<FeedItem> {
        return feedItemService.getItems(feedId, limit, unreadOnly ?: false)
    }

    @MutationMapping
    fun addFeed(@Argument url: String): Feed {
        return runBlocking { feedService.addFeed(url) }
    }

    @MutationMapping
    fun deleteFeed(@Argument feedId: UUID): Feed? {
        return feedService.deleteFeed(feedId)
    }

    @MutationMapping
    fun markItemRead(@Argument itemId: UUID): FeedItem? {
        return feedItemService.markItemRead(itemId)
    }

    @MutationMapping
    fun markItemUnread(@Argument itemId: UUID): FeedItem? {
        return feedItemService.markItemUnread(itemId)
    }

    @MutationMapping
    fun markFeedRead(@Argument feedId: UUID): Int {
        return feedItemService.markFeedRead(feedId)
    }

    @MutationMapping
    fun refreshFeed(@Argument feedId: UUID?): List<Map<String, Any?>> {
        val results = runBlocking { feedService.refreshFeed(feedId) }
        return results.map { (feed, newItems) ->
            mapOf("feedId" to feed.id, "feedTitle" to feed.title, "newItems" to newItems)
        }
    }
}
```

Write corresponding `FeedResolverTest.kt` following the same pattern as BookmarkResolverTest.

**Step 5: Run tests**

Run: `./gradlew test --tests "*ResolverTest" 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```
git add src/main/kotlin/org/sightech/memoryvault/graphql/ src/test/kotlin/org/sightech/memoryvault/graphql/
git commit -m "feat: GraphQL resolvers for bookmarks and feeds"
```

---

### Task 7: GraphQL Resolvers — YouTube + Admin

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/graphql/YoutubeResolver.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/graphql/AdminResolver.kt`
- Test: `src/test/kotlin/org/sightech/memoryvault/graphql/YoutubeResolverTest.kt`
- Test: `src/test/kotlin/org/sightech/memoryvault/graphql/AdminResolverTest.kt`

**Step 1: Implement YoutubeResolver**

Follow the same pattern as Task 6. The resolver wraps:
- `youtubeLists()` — calls `youtubeListService.listLists()`, maps to `YoutubeListWithStats` shape
- `videos(listId, query, removedOnly)` — calls `videoService.getVideos()`
- `videoStatus(videoId)` — calls `videoService.getVideoStatus()`
- `addYoutubeList(url)` — calls `youtubeListService.addList()`
- `deleteYoutubeList(listId)` — calls `youtubeListService.deleteList()`
- `refreshYoutubeList(listId)` — calls `youtubeListService.refreshList()`

**Step 2: Implement AdminResolver**

```kotlin
package org.sightech.memoryvault.graphql

import org.sightech.memoryvault.auth.CurrentUser
import org.sightech.memoryvault.logging.LogService
import org.sightech.memoryvault.scheduling.entity.JobType
import org.sightech.memoryvault.scheduling.service.SyncJobService
import org.sightech.memoryvault.search.ContentType
import org.sightech.memoryvault.search.SearchService
import org.sightech.memoryvault.stats.StatsService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

@Controller
class AdminResolver(
    private val syncJobService: SyncJobService,
    private val logService: LogService,
    private val statsService: StatsService,
    private val searchService: SearchService
) {

    @QueryMapping
    fun jobs(@Argument type: String?, @Argument limit: Int?): List<Any> {
        val userId = CurrentUser.userId()
        val jobType = type?.let { JobType.valueOf(it.trim().uppercase()) }
        return syncJobService.listJobs(userId, jobType, limit ?: 20)
    }

    @QueryMapping
    fun logs(@Argument level: String?, @Argument service: String?, @Argument limit: Int?): List<Any> {
        return logService.getLogs(level, service, limit ?: 50)
    }

    @QueryMapping
    fun stats(): Any {
        return statsService.getStats(CurrentUser.userId())
    }

    @QueryMapping
    fun search(@Argument query: String, @Argument types: List<String>?): List<Any> {
        val typeList = types?.map { ContentType.valueOf(it.trim().uppercase()) }
        return searchService.search(query, typeList, CurrentUser.userId(), 20)
    }
}
```

**Step 3: Write unit tests for both resolvers**

Follow the same mock pattern. Verify that resolvers delegate to services correctly.

**Step 4: Run all tests**

Run: `./gradlew test 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```
git add src/main/kotlin/org/sightech/memoryvault/graphql/ src/test/kotlin/org/sightech/memoryvault/graphql/
git commit -m "feat: GraphQL resolvers for YouTube and admin (jobs, logs, stats, search)"
```

---

### Task 8: GraphQL Integration Test

**Files:**
- Create: `src/test/kotlin/org/sightech/memoryvault/graphql/GraphQlIntegrationTest.kt`

**Step 1: Write integration test**

```kotlin
package org.sightech.memoryvault.graphql

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.test.tester.HttpGraphQlTester
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
@ActiveProfiles("test")
class GraphQlIntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
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

    @Autowired
    lateinit var graphQlTester: HttpGraphQlTester

    @Test
    fun `bookmarks query returns empty list`() {
        // Note: this test needs a valid JWT. Generate one using JwtService
        // or configure test security to bypass auth.
        graphQlTester
            .mutate()
            .header("Authorization", "Bearer ${generateTestToken()}")
            .build()
            .document("{ bookmarks { id url title } }")
            .execute()
            .path("bookmarks")
            .entityList(Any::class.java)
    }

    @Test
    fun `stats query returns system stats`() {
        graphQlTester
            .mutate()
            .header("Authorization", "Bearer ${generateTestToken()}")
            .build()
            .document("{ stats { bookmarkCount feedCount tagCount } }")
            .execute()
            .path("stats.bookmarkCount")
            .entity(Int::class.java)
    }

    private fun generateTestToken(): String {
        // Use JwtService to generate a test token for the seed user
        // Inject JwtService or construct one directly with test secret
        val jwtService = JwtService(
            "test-secret-key-that-is-at-least-256-bits-long-for-hs256-signing!!",
            24
        )
        return jwtService.generateToken(
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "system@memoryvault.local",
            "OWNER"
        )
    }
}
```

**Step 2: Run and debug**

Run: `./gradlew test --tests "*GraphQlIntegrationTest" 2>&1 | tail -20`

This test may require iteration — GraphQL type mappings between JPA entities and schema types often need adjustment. Common issues:
- Field name mismatches between schema and entity (e.g., `unreadCount` vs `unread_count`)
- Nested type resolution (e.g., `FeedWithUnread.feed` needs a `@SchemaMapping`)
- Enum serialization (JobType/JobStatus may serialize differently)

Debug and fix until tests pass.

**Step 3: Commit**

```
git add src/test/kotlin/org/sightech/memoryvault/graphql/GraphQlIntegrationTest.kt
git commit -m "test: GraphQL integration tests with TestContainers"
```

---

### Task 9: Angular Infrastructure — Material, Vitest, Apollo

**Files:**
- Modify: `client/package.json`
- Modify: `client/angular.json`
- Create: `client/vitest.config.ts`
- Create: `client/setup-vitest.ts`
- Modify: `client/src/app/app.config.ts`
- Create: `client/codegen.ts`
- Create: `client/src/app/shared/graphql/graphql.provider.ts`

**Step 1: Install dependencies**

Run from `client/` directory:
```bash
# Angular Material
npm install @angular/material @angular/cdk @angular/animations

# Apollo GraphQL
npm install apollo-angular @apollo/client graphql

# Vitest (replace Karma/Jasmine)
npm install --save-dev vitest @analogjs/vitest-angular jsdom

# GraphQL Codegen
npm install --save-dev @graphql-codegen/cli @graphql-codegen/typescript @graphql-codegen/typescript-operations @graphql-codegen/typescript-apollo-angular

# Playwright
npm install --save-dev @playwright/test
```

**Step 2: Remove Karma/Jasmine**

```bash
npm uninstall karma karma-chrome-launcher karma-coverage karma-jasmine karma-jasmine-html-reporter jasmine-core @types/jasmine
```

**Step 3: Configure Vitest**

`client/vitest.config.ts`:
```typescript
import { defineConfig } from 'vitest/config';
import angular from '@analogjs/vitest-angular';

export default defineConfig({
  plugins: [angular()],
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./setup-vitest.ts'],
    include: ['src/**/*.spec.ts'],
  },
});
```

`client/setup-vitest.ts`:
```typescript
import '@analogjs/vitest-angular/setup-zone';
```

Update `client/package.json` scripts:
```json
{
  "test": "vitest run",
  "test:watch": "vitest"
}
```

Update `client/angular.json` — replace the `test` architect target to use Vitest, or simply use the npm script directly.

**Step 4: Configure GraphQL Codegen**

`client/codegen.ts`:
```typescript
import type { CodegenConfig } from '@graphql-codegen/cli';

const config: CodegenConfig = {
  schema: '../src/main/resources/graphql/*.graphqls',
  documents: 'src/**/*.graphql',
  generates: {
    'src/app/shared/graphql/generated.ts': {
      plugins: [
        'typescript',
        'typescript-operations',
        'typescript-apollo-angular',
      ],
    },
  },
};

export default config;
```

Add to `client/package.json` scripts:
```json
{
  "codegen": "graphql-codegen --config codegen.ts"
}
```

**Step 5: Configure Apollo in app config**

`client/src/app/shared/graphql/graphql.provider.ts`:
```typescript
import { provideApollo } from 'apollo-angular';
import { HttpLink } from 'apollo-angular/http';
import { InMemoryCache } from '@apollo/client/core';
import { inject } from '@angular/core';

export function provideGraphQL() {
  return provideApollo(() => {
    const httpLink = inject(HttpLink);
    return {
      link: httpLink.create({ uri: '/graphql' }),
      cache: new InMemoryCache(),
    };
  });
}
```

Update `client/src/app/app.config.ts`:
```typescript
import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideZonelessChangeDetection, provideBrowserGlobalErrorListeners } from '@angular/core';
import { routes } from './app.routes';
import { provideGraphQL } from './shared/graphql/graphql.provider';
import { authInterceptor } from './auth/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimations(),
    provideGraphQL(),
  ],
};
```

**Step 6: Create the auth interceptor stub**

`client/src/app/auth/auth.interceptor.ts`:
```typescript
import { HttpInterceptorFn } from '@angular/common/http';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('auth_token');
  if (token) {
    const authReq = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    });
    return next(authReq);
  }
  return next(req);
};
```

**Step 7: Verify the Angular app still builds**

Run from `client/`:
```bash
npm run build
```
Expected: Build succeeds

**Step 8: Commit**

```
git add client/
git commit -m "feat: Angular infrastructure - Material, Apollo, Vitest, codegen"
```

---

### Task 10: Angular Auth — Login Page + Guard + Service

**Files:**
- Create: `client/src/app/auth/auth.service.ts`
- Create: `client/src/app/auth/auth.store.ts`
- Create: `client/src/app/auth/auth.guard.ts`
- Create: `client/src/app/auth/login/login.ts`
- Create: `client/src/app/auth/login/login.html`
- Create: `client/src/app/auth/index.ts`
- Modify: `client/src/app/app.routes.ts`

**Step 1: Create AuthService**

`client/src/app/auth/auth.service.ts`:
```typescript
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface LoginResponse {
  token: string;
  email: string;
  displayName: string;
  role: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', { email, password });
  }

  getToken(): string | null {
    return localStorage.getItem('auth_token');
  }

  setToken(token: string): void {
    localStorage.setItem('auth_token', token);
  }

  clearToken(): void {
    localStorage.removeItem('auth_token');
  }

  isAuthenticated(): boolean {
    return this.getToken() !== null;
  }
}
```

**Step 2: Create auth guard**

`client/src/app/auth/auth.guard.ts`:
```typescript
import { inject } from '@angular/core';
import { Router, type CanActivateFn } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/login']);
};
```

**Step 3: Create Login component**

`client/src/app/auth/login/login.ts`:
```typescript
import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './login.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  email = '';
  password = '';
  error = signal<string | null>(null);
  loading = signal(false);

  onSubmit(): void {
    this.loading.set(true);
    this.error.set(null);
    this.authService.login(this.email, this.password).subscribe({
      next: (response) => {
        this.authService.setToken(response.token);
        this.router.navigate(['/']);
      },
      error: () => {
        this.error.set('Invalid email or password');
        this.loading.set(false);
      },
    });
  }
}
```

Add `import { ChangeDetectionStrategy } from '@angular/core';` to the imports.

`client/src/app/auth/login/login.html`:
```html
<div class="login-container">
  <mat-card>
    <mat-card-header>
      <mat-card-title>MemoryVault</mat-card-title>
      <mat-card-subtitle>Sign in to continue</mat-card-subtitle>
    </mat-card-header>
    <mat-card-content>
      <form (ngSubmit)="onSubmit()">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Email</mat-label>
          <input matInput type="email" [(ngModel)]="email" name="email" required />
        </mat-form-field>
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Password</mat-label>
          <input matInput type="password" [(ngModel)]="password" name="password" required />
        </mat-form-field>
        @if (error()) {
          <p class="error-message">{{ error() }}</p>
        }
        <button mat-raised-button color="primary" type="submit" [disabled]="loading()" class="full-width">
          @if (loading()) { Signing in... } @else { Sign In }
        </button>
      </form>
    </mat-card-content>
  </mat-card>
</div>
```

**Step 4: Set up routes**

`client/src/app/app.routes.ts`:
```typescript
import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./auth/login/login').then(m => m.LoginComponent) },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      { path: '', loadComponent: () => import('./reader/reader').then(m => m.ReaderComponent) },
      { path: 'bookmarks', loadComponent: () => import('./bookmarks/bookmarks').then(m => m.BookmarksComponent) },
      { path: 'youtube', loadComponent: () => import('./youtube/youtube').then(m => m.YoutubeComponent) },
      { path: 'admin', loadComponent: () => import('./admin/admin').then(m => m.AdminComponent) },
      { path: 'search', loadComponent: () => import('./search/search').then(m => m.SearchComponent) },
    ],
  },
];
```

Create minimal placeholder components for each route (reader, bookmarks, youtube, admin, search) so the app compiles:

For each feature (`reader`, `bookmarks`, `youtube`, `admin`, `search`), create:
- `client/src/app/<feature>/<feature>.ts` — empty standalone component with OnPush
- `client/src/app/<feature>/<feature>.html` — `<p><feature> works!</p>`

**Step 5: Create barrel export**

`client/src/app/auth/index.ts`:
```typescript
export { AuthService } from './auth.service';
export { authGuard } from './auth.guard';
export { authInterceptor } from './auth.interceptor';
```

**Step 6: Verify build**

Run from `client/`:
```bash
npm run build
```

**Step 7: Commit**

```
git add client/src/
git commit -m "feat: Angular login page, auth service, guard, and route structure"
```

---

### Task 11: Angular App Shell — Top Bar + Sidebar Layout

**Files:**
- Create: `client/src/app/shared/layout/app-layout.ts`
- Create: `client/src/app/shared/layout/app-layout.html`
- Create: `client/src/app/shared/layout/top-bar.ts`
- Create: `client/src/app/shared/layout/top-bar.html`
- Modify: `client/src/app/app.ts`
- Modify: `client/src/app/app.html`
- Modify: `client/src/app/app.routes.ts` (wrap protected routes in layout)

**Context:** The app shell has a top bar with global search, navigation links (Reader, Bookmarks, YouTube, Admin), and a user menu with logout. The reader page has its own sidebar (feed list); other pages are full-width.

**Step 1: Create TopBar component**

Material toolbar with:
- App name/logo (left)
- Global search input (center)
- Navigation links: Reader, Bookmarks, YouTube, Admin (as mat-tab-nav-bar or mat-button links)
- User menu (right): display name + logout button

On search submit, navigate to `/search?q=<query>`.

**Step 2: Create AppLayout component**

Wraps the top bar + `<router-outlet>` for all authenticated routes.

**Step 3: Update routes**

Wrap the protected routes in an AppLayout component:
```typescript
{
  path: '',
  canActivate: [authGuard],
  component: AppLayoutComponent,
  children: [
    { path: '', loadComponent: () => import('./reader/reader').then(m => m.ReaderComponent) },
    // ... other routes
  ],
}
```

**Step 4: Add Angular Material theme**

Add a prebuilt theme to `client/src/styles.css`:
```css
@import '@angular/material/prebuilt-themes/azure-blue.css';

html, body {
  height: 100%;
  margin: 0;
  font-family: Roboto, "Helvetica Neue", sans-serif;
}
```

**Step 5: Verify in browser**

```bash
cd client && npm start
```
Open http://localhost:4200 — should redirect to `/login`. After login, should show the top bar with navigation.

**Step 6: Commit**

```
git add client/src/
git commit -m "feat: Angular app shell with top bar, navigation, and layout"
```

---

### Task 12: Angular Reader Page (Google Reader Style)

**Files:**
- Create: `client/src/app/reader/reader.ts`
- Create: `client/src/app/reader/reader.html`
- Create: `client/src/app/reader/reader.css`
- Create: `client/src/app/reader/feed-sidebar/feed-sidebar.ts`
- Create: `client/src/app/reader/feed-sidebar/feed-sidebar.html`
- Create: `client/src/app/reader/article-list/article-list.ts`
- Create: `client/src/app/reader/article-list/article-list.html`
- Create: `client/src/app/reader/article-detail/article-detail.ts`
- Create: `client/src/app/reader/article-detail/article-detail.html`
- Create: `client/src/app/reader/reader.store.ts`
- Create: `client/src/app/reader/reader.graphql`

**Context:** This is the most complex page. Model after Google Reader:
- Left sidebar: list of feeds with unread counts. Small badges for bookmark count and YouTube download status.
- Main area: list of articles from selected feed (or all feeds). Click article to expand inline.
- Articles are marked as read when expanded.

**Step 1: Create GraphQL operations**

`client/src/app/reader/reader.graphql`:
```graphql
query GetFeeds {
  feeds {
    feed {
      id
      url
      title
      siteUrl
    }
    unreadCount
  }
}

query GetFeedItems($feedId: UUID!, $limit: Int, $unreadOnly: Boolean) {
  feedItems(feedId: $feedId, limit: $limit, unreadOnly: $unreadOnly) {
    id
    feedId
    title
    url
    content
    author
    publishedAt
    readAt
  }
}

mutation MarkItemRead($itemId: UUID!) {
  markItemRead(itemId: $itemId) {
    id
    readAt
  }
}

mutation MarkItemUnread($itemId: UUID!) {
  markItemUnread(itemId: $itemId) {
    id
    readAt
  }
}

mutation MarkFeedRead($feedId: UUID!) {
  markFeedRead(feedId: $feedId)
}

mutation RefreshFeed($feedId: UUID) {
  refreshFeed(feedId: $feedId) {
    feedId
    feedTitle
    newItems
  }
}
```

Run `npm run codegen` to generate TypeScript types.

**Step 2: Create ReaderStore (NgRx Signal Store)**

`client/src/app/reader/reader.store.ts` — manages:
- `feeds` list with unread counts
- `selectedFeedId`
- `items` for the selected feed
- `selectedItemId` (expanded article)
- Actions: selectFeed, selectItem (marks as read), refreshFeeds, markFeedRead

**Step 3: Build the components**

- `FeedSidebarComponent`: `mat-nav-list` with feed names and `mat-badge` for unread counts. "All items" option at top. Small indicator section at bottom for bookmark count and YouTube download progress (just counts, fetched from stats query).
- `ArticleListComponent`: `mat-list` of articles with title, author, date. Unread items are bold. Click expands inline.
- `ArticleDetailComponent`: expanded article view with full content, link to original, mark unread button.

**Step 4: Wire together in ReaderComponent**

`reader.ts`: uses `mat-sidenav-container` with sidebar on the left and article list/detail on the right.

**Step 5: Verify in browser**

Start both backend and frontend. Log in, verify feeds appear in sidebar, clicking a feed shows articles, clicking an article expands it and marks it as read.

**Step 6: Commit**

```
git add client/src/app/reader/
git commit -m "feat: Angular reader page - Google Reader-style feed reader"
```

---

### Task 13: Angular Bookmarks Page

**Files:**
- Create: `client/src/app/bookmarks/bookmarks.ts`
- Create: `client/src/app/bookmarks/bookmarks.html`
- Create: `client/src/app/bookmarks/bookmarks.store.ts`
- Create: `client/src/app/bookmarks/bookmarks.graphql`
- Create: `client/src/app/bookmarks/add-bookmark-dialog/add-bookmark-dialog.ts`
- Create: `client/src/app/bookmarks/add-bookmark-dialog/add-bookmark-dialog.html`

**Step 1: Create GraphQL operations**

```graphql
query GetBookmarks($query: String, $tags: [String]) {
  bookmarks(query: $query, tags: $tags) {
    id
    url
    title
    tags { id name color }
    createdAt
  }
}

mutation AddBookmark($url: String!, $title: String, $tags: [String]) {
  addBookmark(url: $url, title: $title, tags: $tags) {
    id url title tags { id name color }
  }
}

mutation TagBookmark($id: UUID!, $tags: [String!]!) {
  tagBookmark(id: $id, tags: $tags) {
    id tags { id name color }
  }
}

mutation DeleteBookmark($id: UUID!) {
  deleteBookmark(id: $id) { id }
}

mutation ExportBookmarks {
  exportBookmarks
}
```

**Step 2: Build the page**

- Filter bar: search input + tag chip filter
- Bookmark list: `mat-list` or `mat-table` with URL, title, tags, date, actions (edit tags, delete)
- FAB: floating action button to add bookmark (opens `MatDialog`)
- Export button in toolbar
- Tag chips: clickable to filter, inline editing via `mat-chip-grid`

**Step 3: Verify and commit**

```
git add client/src/app/bookmarks/
git commit -m "feat: Angular bookmarks page with add, tag, delete, export"
```

---

### Task 14: Angular YouTube Page

**Files:**
- Create: `client/src/app/youtube/youtube.ts`
- Create: `client/src/app/youtube/youtube.html`
- Create: `client/src/app/youtube/youtube.store.ts`
- Create: `client/src/app/youtube/youtube.graphql`
- Create: `client/src/app/youtube/playlist-card/playlist-card.ts`
- Create: `client/src/app/youtube/playlist-card/playlist-card.html`

**Step 1: Create GraphQL operations**

```graphql
query GetYoutubeLists {
  youtubeLists {
    list { id youtubeListId url name description lastSyncedAt }
    totalVideos
    downloadedVideos
    removedVideos
  }
}

query GetVideos($listId: UUID, $query: String, $removedOnly: Boolean) {
  videos(listId: $listId, query: $query, removedOnly: $removedOnly) {
    id youtubeVideoId youtubeUrl title channelName
    downloadedAt removedFromYoutube durationSeconds
  }
}

mutation AddYoutubeList($url: String!) {
  addYoutubeList(url: $url) {
    list { id name url }
    newVideos
  }
}

mutation RefreshYoutubeList($listId: UUID) {
  refreshYoutubeList(listId: $listId) {
    listId newVideos removedVideos downloadSuccesses downloadFailures
  }
}

mutation DeleteYoutubeList($listId: UUID!) {
  deleteYoutubeList(listId: $listId) { id }
}
```

**Step 2: Build the page**

- Playlist cards: `mat-expansion-panel` per playlist showing name, progress bar (downloaded/total), removed count, refresh button, delete button
- Video list inside each panel: `mat-table` with title, channel, status badges (downloaded/removed/pending), duration
- Add playlist: input + button at top
- Removed filter: toggle to show only removed videos

**Step 3: Verify and commit**

```
git add client/src/app/youtube/
git commit -m "feat: Angular YouTube page with playlists and video archive"
```

---

### Task 15: Angular Admin Page (Jobs, Logs, Stats)

**Files:**
- Create: `client/src/app/admin/admin.ts`
- Create: `client/src/app/admin/admin.html`
- Create: `client/src/app/admin/admin.store.ts`
- Create: `client/src/app/admin/admin.graphql`
- Create: `client/src/app/admin/jobs-table/jobs-table.ts`
- Create: `client/src/app/admin/log-viewer/log-viewer.ts`
- Create: `client/src/app/admin/stats-panel/stats-panel.ts`

**Step 1: Create GraphQL operations**

```graphql
query GetJobs($type: String, $limit: Int) {
  jobs(type: $type, limit: $limit) {
    id type status startedAt completedAt errorMessage triggeredBy metadata
  }
}

query GetLogs($level: String, $service: String, $limit: Int) {
  logs(level: $level, service: $service, limit: $limit) {
    timestamp level logger message thread
  }
}

query GetStats {
  stats {
    bookmarkCount feedCount feedItemCount unreadFeedItemCount
    youtubeListCount videoCount downloadedVideoCount removedVideoCount
    tagCount storageUsedBytes lastFeedSync lastYoutubeSync
    feedsWithFailures youtubeListsWithFailures
  }
}
```

**Step 2: Build three sections**

- **Stats panel**: `mat-card` grid showing counts, storage usage, last sync times, failure warnings
- **Jobs table**: `mat-table` with columns: type, status (with color-coded chips), started, duration, triggered by, error. Filter by type dropdown.
- **Log viewer**: `mat-table` or virtual-scroll list with timestamp, level (color-coded), logger (short name), message. Filters: level dropdown, service input, limit.

Use `mat-tab-group` to organize the three sections.

**Step 3: Verify and commit**

```
git add client/src/app/admin/
git commit -m "feat: Angular admin page with jobs, logs, and stats"
```

---

### Task 16: Angular Global Search

**Files:**
- Create: `client/src/app/search/search.ts`
- Create: `client/src/app/search/search.html`
- Create: `client/src/app/search/search.graphql`
- Modify: `client/src/app/shared/layout/top-bar.ts` (wire search input)

**Step 1: Create GraphQL operation**

```graphql
query Search($query: String!, $types: [String]) {
  search(query: $query, types: $types) {
    type
    id
    title
    url
    rank
  }
}
```

**Step 2: Build the search page**

- Reads `q` query param from URL
- Groups results by type: Bookmarks, Feed Items, Videos (using `mat-expansion-panel` or sections)
- Each result is clickable: bookmarks go to `/bookmarks`, feed items go to reader, videos go to `/youtube`
- Type filter chips at the top (BOOKMARK, FEED_ITEM, VIDEO)

**Step 3: Wire the top bar search**

In `TopBarComponent`, the search input navigates to `/search?q=<query>` on Enter.

**Step 4: Verify and commit**

```
git add client/src/app/search/ client/src/app/shared/
git commit -m "feat: Angular global search page with cross-entity results"
```

---

### Task 17: Frontend Unit Tests

**Files:**
- Create: `client/src/app/auth/auth.service.spec.ts`
- Create: `client/src/app/auth/login/login.spec.ts`
- Create: `client/src/app/reader/reader.spec.ts`
- Create: `client/src/app/bookmarks/bookmarks.spec.ts`

**Step 1: Write auth service test**

```typescript
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  it('should send login request', () => {
    service.login('test@example.com', 'password').subscribe(response => {
      expect(response.token).toBeTruthy();
    });

    const req = httpTesting.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    req.flush({ token: 'jwt', email: 'test@example.com', displayName: 'Test', role: 'OWNER' });
  });

  it('should manage token in localStorage', () => {
    service.setToken('test-token');
    expect(service.getToken()).toBe('test-token');
    expect(service.isAuthenticated()).toBe(true);

    service.clearToken();
    expect(service.getToken()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });
});
```

**Step 2: Write login component test**

Test that the login form renders, submits correctly, navigates on success, and shows error on failure. Mock `AuthService`.

**Step 3: Write at least one test per feature component**

Verify that the component creates and renders basic structure. Mock Apollo queries.

**Step 4: Run tests**

```bash
cd client && npm test
```

**Step 5: Commit**

```
git add client/src/
git commit -m "test: Angular unit tests for auth, reader, bookmarks"
```

---

### Task 18: Playwright E2E Setup

**Files:**
- Create: `client/playwright.config.ts`
- Create: `client/e2e/login.spec.ts`
- Create: `client/e2e/navigation.spec.ts`

**Step 1: Configure Playwright**

`client/playwright.config.ts`:
```typescript
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  use: {
    baseURL: 'http://localhost:4200',
    headless: true,
  },
  webServer: {
    command: 'npm start',
    url: 'http://localhost:4200',
    reuseExistingServer: true,
  },
});
```

Add to `client/package.json`:
```json
{
  "e2e": "playwright test"
}
```

**Step 2: Write login E2E test**

```typescript
import { test, expect } from '@playwright/test';

test('login page shows form', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByLabel('Email')).toBeVisible();
  await expect(page.getByLabel('Password')).toBeVisible();
  await expect(page.getByRole('button', { name: /sign in/i })).toBeVisible();
});

test('redirects to login when not authenticated', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/login/);
});
```

**Step 3: Write navigation E2E test**

Test that after login, clicking navigation links goes to correct pages. This requires the backend to be running — use `webServer` config or skip if backend isn't available.

**Step 4: Run E2E**

```bash
cd client && npx playwright install && npm run e2e
```

**Step 5: Commit**

```
git add client/playwright.config.ts client/e2e/ client/package.json
git commit -m "test: Playwright E2E tests for login and navigation"
```

---

### Task 19: Test Scripts

**Files:**
- Create: `scripts/test-frontend.sh`
- Create: `scripts/test-graphql.sh`
- Modify: `scripts/test-all.sh`

**Step 1: Create test-frontend.sh**

```bash
#!/bin/bash
set -e

echo "=== Frontend Tests ==="

echo ""
echo "--- Unit Tests (Vitest) ---"
cd client && npm test 2>&1 | grep -E "(PASS|FAIL|Tests|✓|✗)"

echo ""
echo "--- Lint ---"
npm run lint 2>&1 | grep -E "(error|warning|problems)" || echo "Lint clean"

echo ""
echo "=== Frontend tests complete ==="
```

**Step 2: Create test-graphql.sh**

```bash
#!/bin/bash
set -e

echo "=== GraphQL Tests ==="

echo ""
echo "--- Unit: BookmarkResolver ---"
./gradlew test --tests "*BookmarkResolverTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Unit: FeedResolver ---"
./gradlew test --tests "*FeedResolverTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Unit: YoutubeResolver ---"
./gradlew test --tests "*YoutubeResolverTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Unit: AdminResolver ---"
./gradlew test --tests "*AdminResolverTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Integration: GraphQlIntegrationTest ---"
./gradlew test --tests "*GraphQlIntegrationTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "=== All GraphQL tests complete ==="
```

**Step 3: Update test-all.sh**

Add these lines:
```bash
echo ""
echo "--- GraphQL ---"
./scripts/test-graphql.sh

echo ""
echo "--- Frontend ---"
./scripts/test-frontend.sh
```

**Step 4: Make scripts executable and test**

```bash
chmod +x scripts/test-frontend.sh scripts/test-graphql.sh
./scripts/test-all.sh
```

**Step 5: Commit**

```
git add scripts/
git commit -m "feat: test scripts for frontend and GraphQL"
```

---

### Task 20: Final Verification + Proxy Config

**Files:**
- Create: `client/proxy.conf.json` (for local dev API proxying)
- Modify: `client/angular.json` (add proxy config to serve target)
- Verify: all tests pass, app runs end-to-end

**Step 1: Create proxy config**

`client/proxy.conf.json`:
```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false
  },
  "/graphql": {
    "target": "http://localhost:8080",
    "secure": false
  },
  "/graphiql": {
    "target": "http://localhost:8080",
    "secure": false
  }
}
```

Update `client/angular.json` — add to the `serve` target options:
```json
"proxyConfig": "proxy.conf.json"
```

**Step 2: Full end-to-end verification**

1. Start PostgreSQL: `docker compose up -d`
2. Start backend: `./gradlew bootRun`
3. Start frontend: `cd client && npm start`
4. Open http://localhost:4200
5. Login with `system@memoryvault.local` / `memoryvault`
6. Verify: reader shows feeds, bookmarks page works, YouTube page works, admin shows stats/jobs/logs, global search works

**Step 3: Run all tests**

```bash
./scripts/test-all.sh
```

**Step 4: Commit**

```
git add client/proxy.conf.json client/angular.json
git commit -m "feat: proxy config for local dev, Phase 5 complete"
```

---

## Summary

| Task | Description                             | Key Files                                              |
|------|-----------------------------------------|--------------------------------------------------------|
| 1    | User entity + auth service              | auth/entity/, auth/service/, V4 migration              |
| 2    | JWT token service                       | auth/service/JwtService.kt, jjwt deps                  |
| 3    | Login endpoint + SecurityConfig         | auth/controller/, config/SecurityConfig, JwtAuthFilter |
| 4    | CurrentUser helper + wire services      | auth/CurrentUser.kt, all services                      |
| 5    | GraphQL setup + schema                  | graphql/*.graphqls, ScalarConfig, deps                 |
| 6    | GraphQL resolvers: bookmarks + feeds    | graphql/BookmarkResolver, FeedResolver                 |
| 7    | GraphQL resolvers: YouTube + admin      | graphql/YoutubeResolver, AdminResolver                 |
| 8    | GraphQL integration test                | GraphQlIntegrationTest                                 |
| 9    | Angular infra: Material, Vitest, Apollo | client deps, config, providers                         |
| 10   | Angular auth: login + guard             | client/src/app/auth/                                   |
| 11   | Angular app shell: top bar + layout     | client/src/app/shared/layout/                          |
| 12   | Angular reader page                     | client/src/app/reader/                                 |
| 13   | Angular bookmarks page                  | client/src/app/bookmarks/                              |
| 14   | Angular YouTube page                    | client/src/app/youtube/                                |
| 15   | Angular admin page                      | client/src/app/admin/                                  |
| 16   | Angular global search                   | client/src/app/search/                                 |
| 17   | Frontend design                         | *.css files                                            |
| 18   | Frontend unit tests                     | *.spec.ts files                                        |
| 19   | Playwright E2E                          | client/e2e/                                            |
| 20   | Test scripts                            | scripts/test-frontend.sh, test-graphql.sh              |
| 21   | Final verification + proxy config       | proxy.conf.json, end-to-end check                      |
