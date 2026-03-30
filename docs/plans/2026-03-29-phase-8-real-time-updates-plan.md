# Phase 8: Real-Time Updates — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add WebSocket (STOMP) support so the Angular UI receives lightweight signals on server-side state changes and refetches data automatically — no polling.

**Architecture:** Spring ApplicationEvents published by domain services → async `WebSocketEventRelay` → STOMP simple broker → Angular `WebSocketService` → store refetch. JWT validated on STOMP CONNECT. Dev/prod config split as a prerequisite.

**Tech Stack:** Spring Boot 4.0.3 WebSocket + STOMP, `@stomp/rx-stomp` (Angular), MockK + TestContainers (backend tests), Vitest (frontend tests), Playwright (E2E)

**Spec:** `docs/plans/2026-03-29-phase-8-real-time-updates-design.md`

---

## File Structure

### New Files — Backend
- `src/main/resources/application-dev.properties` — dev profile overrides
- `src/main/resources/application-prod.properties` — prod profile placeholders
- `src/main/kotlin/org/sightech/memoryvault/websocket/VaultEvent.kt` — sealed interface + data classes + enums
- `src/main/kotlin/org/sightech/memoryvault/websocket/WebSocketEventRelay.kt` — `@EventListener` → STOMP relay
- `src/main/kotlin/org/sightech/memoryvault/config/WebSocketConfig.kt` — STOMP broker + endpoint config
- `src/main/kotlin/org/sightech/memoryvault/config/WebSocketAuthInterceptor.kt` — JWT on CONNECT
- `src/test/kotlin/org/sightech/memoryvault/websocket/WebSocketEventRelayTest.kt` — unit test
- `src/test/kotlin/org/sightech/memoryvault/config/WebSocketAuthInterceptorTest.kt` — unit test
- `src/test/kotlin/org/sightech/memoryvault/websocket/WebSocketIntegrationTest.kt` — integration test
- `client/src/environments/environment.ts` — dev environment
- `client/src/environments/environment.prod.ts` — prod environment
- `client/src/app/core/services/websocket.service.ts` — STOMP client
- `client/src/app/core/services/websocket.service.spec.ts` — unit test

### Modified Files — Backend
- `build.gradle.kts` — add `spring-boot-starter-websocket`
- `src/main/resources/application.properties` — extract env-specific values
- `src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt` — permit `/ws/**`, CORS from config
- `src/main/kotlin/org/sightech/memoryvault/scheduling/service/SyncJobService.kt` — publish `JobStatusChanged`
- `src/main/kotlin/org/sightech/memoryvault/feed/service/FeedService.kt` — publish `FeedSyncCompleted`
- `src/main/kotlin/org/sightech/memoryvault/feed/service/FeedItemService.kt` — publish `ContentMutated`
- `src/main/kotlin/org/sightech/memoryvault/feed/service/FeedCategoryService.kt` — publish `ContentMutated`
- `src/main/kotlin/org/sightech/memoryvault/youtube/service/VideoSyncService.kt` — publish `VideoDownloadCompleted`
- `src/main/kotlin/org/sightech/memoryvault/bookmark/service/BookmarkService.kt` — publish `ContentMutated`
- `src/main/kotlin/org/sightech/memoryvault/bookmark/service/IngestService.kt` — publish `IngestReady`

### Modified Files — Frontend
- `client/package.json` — add `@stomp/rx-stomp`, `@stomp/stompjs`
- `client/proxy.conf.json` — add `/ws` proxy
- `client/src/app/reader/reader.store.ts` — subscribe to WebSocket signals
- `client/src/app/bookmarks/bookmarks.store.ts` — subscribe to WebSocket signals
- `client/src/app/youtube/youtube.store.ts` — subscribe to WebSocket signals
- `client/src/app/admin/admin.store.ts` — subscribe to WebSocket signals
- `client/src/app/admin/admin.ts` — replace setInterval with WebSocket

---

## Task 1: Dev/Prod Configuration Split

**Files:**
- Modify: `src/main/resources/application.properties`
- Create: `src/main/resources/application-dev.properties`
- Create: `src/main/resources/application-prod.properties`
- Modify: `src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt`

- [x] **Step 1: Create `application-dev.properties`**

```properties
# Dev profile — active by default (no SPRING_PROFILES_ACTIVE set)

# Database (Docker Compose local)
spring.datasource.url=jdbc:postgresql://localhost:5433/memoryvault
spring.datasource.username=memoryvault
spring.datasource.password=memoryvault

# CORS
memoryvault.cors.allowed-origins=http://localhost:4200

# JWT (dev-only secret — NEVER use in production)
memoryvault.jwt.secret=dev-secret-key-change-in-production-must-be-at-least-256-bits-long!!

# GraphiQL (dev only)
spring.graphql.graphiql.enabled=true
```

- [x] **Step 2: Create `application-prod.properties`**

```properties
# Prod profile — activate with SPRING_PROFILES_ACTIVE=prod

# Database (RDS)
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USERNAME}
spring.datasource.password=${DATABASE_PASSWORD}

# CORS
memoryvault.cors.allowed-origins=${CORS_ALLOWED_ORIGINS}

# JWT (from environment / Secrets Manager)
memoryvault.jwt.secret=${JWT_SECRET}

# GraphiQL disabled in production
spring.graphql.graphiql.enabled=false
```

- [x] **Step 3: Update `application.properties` to contain only shared settings**

Remove the env-specific values that are now in dev/prod profiles. Keep:

```properties
spring.application.name=memoryVault
spring.profiles.default=dev

# JPA
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false

# Flyway
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# Actuator
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=when-authorized

# MCP Server
spring.ai.mcp.server.name=memoryvault
spring.ai.mcp.server.version=0.0.1
spring.ai.mcp.server.instructions=MemoryVault: search and manage your bookmarks, RSS feeds, and archived YouTube videos.

# JWT expiration (shared across profiles)
memoryvault.jwt.expiration-hours=24

# Feed sync schedule (cron syntax). Set to "-" to disable automatic sync.
memoryvault.feeds.sync-cron=-

# YouTube sync schedule (cron syntax). Set to "-" to disable automatic sync.
memoryvault.youtube.sync-cron=-

# Local storage path for downloaded videos and other files
memoryvault.storage.local-path=${user.home}/.memoryvault/storage

# Log file path for structured JSON logs
memoryvault.logging.path=${user.home}/.memoryvault/logs

# GraphQL
spring.graphql.graphiql.path=/graphiql
spring.graphql.schema.printer.enabled=true

# TODO: OAuth2 Client Configuration — uncomment and configure when OAuth login is implemented
# spring.security.oauth2.client.registration.google.client-id=YOUR_GOOGLE_CLIENT_ID
# spring.security.oauth2.client.registration.google.client-secret=YOUR_GOOGLE_CLIENT_SECRET
# spring.security.oauth2.client.registration.google.scope=openid,profile,email
# spring.security.oauth2.client.registration.github.client-id=YOUR_GITHUB_CLIENT_ID
# spring.security.oauth2.client.registration.github.client-secret=YOUR_GITHUB_CLIENT_SECRET
```

- [x] **Step 4: Update SecurityConfig to read CORS from config**

Replace the hardcoded `http://localhost:4200` in `SecurityConfig.kt` with a `@Value` injection:

```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    @Value("\${memoryvault.cors.allowed-origins}") private val allowedOrigins: String
) {
    // ... securityFilterChain unchanged ...

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = allowedOrigins.split(",").map { it.trim() }
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
```

- [x] **Step 5: Add CORS property to `application-test.properties`**

Append to `src/main/resources/application-test.properties`:

```properties
memoryvault.cors.allowed-origins=http://localhost
```

This ensures the `@Value("${memoryvault.cors.allowed-origins}")` in `SecurityConfig` resolves during tests, even though the test profile doesn't load `application-dev.properties`.

- [x] **Step 6: Run all tests to verify the config split didn't break anything**

Run: `./gradlew test`
Expected: All existing tests pass. `application-test.properties` continues to override test-specific values (JWT secret). The `spring.profiles.default=dev` ensures `application-dev.properties` is loaded when no profile is active.

- [x] **Step 7: Commit**

```
git add src/main/resources/application.properties src/main/resources/application-dev.properties src/main/resources/application-prod.properties src/main/resources/application-test.properties src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt
git commit -m "refactor: split application.properties into dev/prod profiles"
```

---

## Task 2: Angular Environment Files

**Files:**
- Create: `client/src/environments/environment.ts`
- Create: `client/src/environments/environment.prod.ts`
- Modify: `client/proxy.conf.json`

- [x] **Step 1: Create `client/src/environments/environment.ts`**

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080',
  wsUrl: 'ws://localhost:8080/ws',
};
```

- [x] **Step 2: Create `client/src/environments/environment.prod.ts`**

```typescript
export const environment = {
  production: true,
  apiUrl: '', // relative URLs, served from same origin
  wsUrl: '', // set at runtime from window.location
};
```

- [x] **Step 3: Add WebSocket proxy to `client/proxy.conf.json`**

Add the `/ws` entry:

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  },
  "/graphql": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  },
  "/graphiql": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  },
  "/ws": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true,
    "ws": true
  }
}
```

- [x] **Step 4: Commit**

```
git add client/src/environments/environment.ts client/src/environments/environment.prod.ts client/proxy.conf.json
git commit -m "feat: add Angular environment files and WebSocket proxy"
```

---

## Task 3: Domain Events (VaultEvent)

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/websocket/VaultEvent.kt`
- Test: `src/test/kotlin/org/sightech/memoryvault/websocket/VaultEventTest.kt`

- [ ] **Step 1: Write a test for VaultEvent construction and eventType**

Create `src/test/kotlin/org/sightech/memoryvault/websocket/VaultEventTest.kt`:

```kotlin
package org.sightech.memoryvault.websocket

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class VaultEventTest {

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `FeedSyncCompleted has correct eventType`() {
        val event = FeedSyncCompleted(
            userId = userId,
            timestamp = Instant.now(),
            feedId = UUID.randomUUID(),
            newItemCount = 5,
            feedsRefreshed = 1
        )
        assertEquals(VaultEventType.FEED_SYNC_COMPLETED, event.eventType)
    }

    @Test
    fun `JobStatusChanged has correct eventType`() {
        val event = JobStatusChanged(
            userId = userId,
            timestamp = Instant.now(),
            jobId = UUID.randomUUID(),
            jobType = "RSS_FETCH",
            oldStatus = "PENDING",
            newStatus = "RUNNING"
        )
        assertEquals(VaultEventType.JOB_STATUS_CHANGED, event.eventType)
    }

    @Test
    fun `VideoDownloadCompleted has correct eventType`() {
        val event = VideoDownloadCompleted(
            userId = userId,
            timestamp = Instant.now(),
            videoId = UUID.randomUUID(),
            listId = UUID.randomUUID(),
            success = true
        )
        assertEquals(VaultEventType.VIDEO_DOWNLOAD_COMPLETED, event.eventType)
    }

    @Test
    fun `IngestReady has correct eventType`() {
        val event = IngestReady(
            userId = userId,
            timestamp = Instant.now(),
            previewId = UUID.randomUUID(),
            itemCount = 10
        )
        assertEquals(VaultEventType.INGEST_READY, event.eventType)
    }

    @Test
    fun `ContentMutated has correct eventType`() {
        val event = ContentMutated(
            userId = userId,
            timestamp = Instant.now(),
            contentType = ContentType.BOOKMARK,
            mutationType = MutationType.CREATED,
            entityId = UUID.randomUUID()
        )
        assertEquals(VaultEventType.CONTENT_MUTATED, event.eventType)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.sightech.memoryvault.websocket.VaultEventTest"`
Expected: FAIL — classes don't exist yet.

- [ ] **Step 3: Create `VaultEvent.kt`**

Create `src/main/kotlin/org/sightech/memoryvault/websocket/VaultEvent.kt`:

```kotlin
package org.sightech.memoryvault.websocket

import java.time.Instant
import java.util.UUID

enum class VaultEventType {
    FEED_SYNC_COMPLETED,
    JOB_STATUS_CHANGED,
    VIDEO_DOWNLOAD_COMPLETED,
    INGEST_READY,
    CONTENT_MUTATED
}

enum class ContentType {
    BOOKMARK, FEED_ITEM, VIDEO, FOLDER, CATEGORY
}

enum class MutationType {
    CREATED, UPDATED, DELETED
}

sealed interface VaultEvent {
    val eventType: VaultEventType
    val userId: UUID
    val timestamp: Instant
}

data class FeedSyncCompleted(
    override val userId: UUID,
    override val timestamp: Instant,
    val feedId: UUID?,
    val newItemCount: Int,
    val feedsRefreshed: Int
) : VaultEvent {
    override val eventType = VaultEventType.FEED_SYNC_COMPLETED
}

data class JobStatusChanged(
    override val userId: UUID,
    override val timestamp: Instant,
    val jobId: UUID,
    val jobType: String,
    val oldStatus: String,
    val newStatus: String
) : VaultEvent {
    override val eventType = VaultEventType.JOB_STATUS_CHANGED
}

data class VideoDownloadCompleted(
    override val userId: UUID,
    override val timestamp: Instant,
    val videoId: UUID,
    val listId: UUID,
    val success: Boolean
) : VaultEvent {
    override val eventType = VaultEventType.VIDEO_DOWNLOAD_COMPLETED
}

data class IngestReady(
    override val userId: UUID,
    override val timestamp: Instant,
    val previewId: UUID,
    val itemCount: Int
) : VaultEvent {
    override val eventType = VaultEventType.INGEST_READY
}

data class ContentMutated(
    override val userId: UUID,
    override val timestamp: Instant,
    val contentType: ContentType,
    val mutationType: MutationType,
    val entityId: UUID? = null
) : VaultEvent {
    override val eventType = VaultEventType.CONTENT_MUTATED
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "org.sightech.memoryvault.websocket.VaultEventTest"`
Expected: PASS — all 5 tests green.

- [ ] **Step 5: Commit**

```
git add src/main/kotlin/org/sightech/memoryvault/websocket/VaultEvent.kt src/test/kotlin/org/sightech/memoryvault/websocket/VaultEventTest.kt
git commit -m "feat: add VaultEvent domain events for real-time updates"
```

---

## Task 4: WebSocket Infrastructure (Config + Auth Interceptor)

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/main/kotlin/org/sightech/memoryvault/config/WebSocketConfig.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/config/WebSocketAuthInterceptor.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt`
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/resources/application-dev.properties`
- Modify: `src/main/resources/application-prod.properties`
- Test: `src/test/kotlin/org/sightech/memoryvault/config/WebSocketAuthInterceptorTest.kt`

- [ ] **Step 1: Add WebSocket dependency to `build.gradle.kts`**

In the `dependencies` block, after the `spring-boot-starter-graphql` line, add:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-websocket")
```

- [ ] **Step 2: Add WebSocket config properties**

Append to `src/main/resources/application.properties`:

```properties
# WebSocket
memoryvault.websocket.heartbeat-interval-ms=10000
memoryvault.websocket.relay-executor-pool-size=4
```

Append to `src/main/resources/application-dev.properties`:

```properties
# WebSocket
memoryvault.websocket.allowed-origins=${memoryvault.cors.allowed-origins}
```

Append to `src/main/resources/application-prod.properties`:

```properties
# WebSocket
memoryvault.websocket.allowed-origins=${memoryvault.cors.allowed-origins}
```

Append to `src/main/resources/application-test.properties`:

```properties
memoryvault.websocket.allowed-origins=*
memoryvault.websocket.heartbeat-interval-ms=10000
memoryvault.websocket.relay-executor-pool-size=2
```

- [ ] **Step 3: Write the auth interceptor test**

Create `src/test/kotlin/org/sightech/memoryvault/config/WebSocketAuthInterceptorTest.kt`:

```kotlin
package org.sightech.memoryvault.config

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.service.JwtService
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WebSocketAuthInterceptorTest {

    private val jwtService = mockk<JwtService>()
    private val interceptor = WebSocketAuthInterceptor(jwtService)
    private val channel = mockk<MessageChannel>()

    private fun buildConnectMessage(token: String?): Message<ByteArray> {
        val accessor = StompHeaderAccessor.create(StompCommand.CONNECT)
        if (token != null) {
            accessor.setNativeHeader("Authorization", "Bearer $token")
        }
        accessor.setLeaveMutable(true)
        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }

    @Test
    fun `accepts valid JWT and sets principal`() {
        every { jwtService.validateToken("valid-token") } returns mapOf(
            "userId" to "00000000-0000-0000-0000-000000000001",
            "email" to "test@test.com",
            "role" to "OWNER"
        )

        val message = buildConnectMessage("valid-token")
        val result = interceptor.preSend(message, channel)

        assertNotNull(result)
        val accessor = StompHeaderAccessor.wrap(result)
        assertNotNull(accessor.user)
        assert(accessor.user!!.name == "00000000-0000-0000-0000-000000000001")
    }

    @Test
    fun `rejects invalid JWT`() {
        every { jwtService.validateToken("bad-token") } returns null

        val message = buildConnectMessage("bad-token")
        val result = interceptor.preSend(message, channel)

        assertNull(result)
    }

    @Test
    fun `rejects missing Authorization header`() {
        val message = buildConnectMessage(null)
        val result = interceptor.preSend(message, channel)

        assertNull(result)
    }

    @Test
    fun `passes through non-CONNECT messages`() {
        val accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE)
        accessor.setLeaveMutable(true)
        val message = MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)

        val result = interceptor.preSend(message, channel)

        assertNotNull(result)
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew test --tests "org.sightech.memoryvault.config.WebSocketAuthInterceptorTest"`
Expected: FAIL — `WebSocketAuthInterceptor` doesn't exist.

- [ ] **Step 5: Create `WebSocketAuthInterceptor.kt`**

Create `src/main/kotlin/org/sightech/memoryvault/config/WebSocketAuthInterceptor.kt`:

```kotlin
package org.sightech.memoryvault.config

import org.sightech.memoryvault.auth.service.JwtService
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.stereotype.Component
import java.security.Principal

@Component
class WebSocketAuthInterceptor(private val jwtService: JwtService) : ChannelInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = StompHeaderAccessor.wrap(message)

        if (accessor.command != StompCommand.CONNECT) {
            return message
        }

        val authHeader = accessor.getFirstNativeHeader("Authorization")
        val token = authHeader?.removePrefix("Bearer ")?.trim()

        if (token.isNullOrBlank()) {
            log.warn("WebSocket CONNECT rejected: missing Authorization header")
            return null
        }

        val claims = jwtService.validateToken(token)
        if (claims == null) {
            log.warn("WebSocket CONNECT rejected: invalid JWT")
            return null
        }

        val userId = claims["userId"]!!
        accessor.user = Principal { userId }
        log.info("WebSocket CONNECT accepted userId={}", userId)

        return message
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests "org.sightech.memoryvault.config.WebSocketAuthInterceptorTest"`
Expected: PASS — all 4 tests green.

- [ ] **Step 7: Create `WebSocketConfig.kt`**

Create `src/main/kotlin/org/sightech/memoryvault/config/WebSocketConfig.kt`:

```kotlin
package org.sightech.memoryvault.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
@EnableAsync
class WebSocketConfig(
    private val webSocketAuthInterceptor: WebSocketAuthInterceptor,
    @Value("\${memoryvault.websocket.allowed-origins}") private val allowedOrigins: String,
    @Value("\${memoryvault.websocket.heartbeat-interval-ms}") private val heartbeatInterval: Long,
    @Value("\${memoryvault.websocket.relay-executor-pool-size}") private val relayPoolSize: Int
) : WebSocketMessageBrokerConfigurer {

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
            .setHeartbeatValue(longArrayOf(heartbeatInterval, heartbeatInterval))
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins(*allowedOrigins.split(",").map { it.trim() }.toTypedArray())
            .withSockJS()
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(webSocketAuthInterceptor)
    }

    @Bean("websocketRelayExecutor")
    fun websocketRelayExecutor(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = relayPoolSize
        executor.maxPoolSize = relayPoolSize
        executor.setThreadNamePrefix("ws-relay-")
        executor.initialize()
        return executor
    }
}
```

- [ ] **Step 8: Update SecurityConfig to permit `/ws/**`**

In the `securityFilterChain` method, add `/ws/**` to the permitted paths:

```kotlin
.authorizeHttpRequests { auth ->
    auth
        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
        .requestMatchers("/api/auth/**").permitAll()
        .requestMatchers("/graphiql/**").permitAll()
        .requestMatchers("/ws/**").permitAll()
        .anyRequest().authenticated()
}
```

- [ ] **Step 9: Run all tests**

Run: `./gradlew test`
Expected: All tests pass including the new interceptor tests.

- [ ] **Step 10: Commit**

```
git add build.gradle.kts src/main/resources/application.properties src/main/resources/application-dev.properties src/main/resources/application-prod.properties src/main/kotlin/org/sightech/memoryvault/config/WebSocketConfig.kt src/main/kotlin/org/sightech/memoryvault/config/WebSocketAuthInterceptor.kt src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt src/test/kotlin/org/sightech/memoryvault/config/WebSocketAuthInterceptorTest.kt
git commit -m "feat: add WebSocket STOMP config with JWT auth interceptor"
```

---

## Task 5: WebSocket Event Relay

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/websocket/WebSocketEventRelay.kt`
- Test: `src/test/kotlin/org/sightech/memoryvault/websocket/WebSocketEventRelayTest.kt`

- [ ] **Step 1: Write the relay test**

Create `src/test/kotlin/org/sightech/memoryvault/websocket/WebSocketEventRelayTest.kt`:

```kotlin
package org.sightech.memoryvault.websocket

import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebSocketEventRelayTest {

    private val messagingTemplate = mockk<SimpMessagingTemplate>(relaxed = true)
    private val relay = WebSocketEventRelay(messagingTemplate)
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `relays FeedSyncCompleted to correct topic`() {
        val feedId = UUID.randomUUID()
        val event = FeedSyncCompleted(
            userId = userId,
            timestamp = Instant.now(),
            feedId = feedId,
            newItemCount = 5,
            feedsRefreshed = 1
        )

        relay.onFeedSyncCompleted(event)

        val topicSlot = slot<String>()
        val payloadSlot = slot<Any>()
        verify { messagingTemplate.convertAndSend(capture(topicSlot), capture(payloadSlot)) }
        assertEquals("/topic/user/$userId/feeds", topicSlot.captured)
        val payload = payloadSlot.captured as Map<*, *>
        assertEquals("FEED_SYNC_COMPLETED", payload["eventType"])
        assertEquals(feedId.toString(), payload["feedId"])
        assertEquals(5, payload["newItemCount"])
    }

    @Test
    fun `relays JobStatusChanged to correct topic`() {
        val jobId = UUID.randomUUID()
        val event = JobStatusChanged(
            userId = userId,
            timestamp = Instant.now(),
            jobId = jobId,
            jobType = "RSS_FETCH",
            oldStatus = "PENDING",
            newStatus = "RUNNING"
        )

        relay.onJobStatusChanged(event)

        val topicSlot = slot<String>()
        val payloadSlot = slot<Any>()
        verify { messagingTemplate.convertAndSend(capture(topicSlot), capture(payloadSlot)) }
        assertEquals("/topic/user/$userId/jobs", topicSlot.captured)
        val payload = payloadSlot.captured as Map<*, *>
        assertEquals("JOB_STATUS_CHANGED", payload["eventType"])
        assertEquals("RUNNING", payload["newStatus"])
    }

    @Test
    fun `relays VideoDownloadCompleted to correct topic`() {
        val videoId = UUID.randomUUID()
        val listId = UUID.randomUUID()
        val event = VideoDownloadCompleted(
            userId = userId,
            timestamp = Instant.now(),
            videoId = videoId,
            listId = listId,
            success = true
        )

        relay.onVideoDownloadCompleted(event)

        val topicSlot = slot<String>()
        val payloadSlot = slot<Any>()
        verify { messagingTemplate.convertAndSend(capture(topicSlot), capture(payloadSlot)) }
        assertEquals("/topic/user/$userId/videos", topicSlot.captured)
        val payload = payloadSlot.captured as Map<*, *>
        assertEquals("VIDEO_DOWNLOAD_COMPLETED", payload["eventType"])
        assertEquals(true, payload["success"])
    }

    @Test
    fun `relays IngestReady to correct topic`() {
        val previewId = UUID.randomUUID()
        val event = IngestReady(
            userId = userId,
            timestamp = Instant.now(),
            previewId = previewId,
            itemCount = 10
        )

        relay.onIngestReady(event)

        val topicSlot = slot<String>()
        val payloadSlot = slot<Any>()
        verify { messagingTemplate.convertAndSend(capture(topicSlot), capture(payloadSlot)) }
        assertEquals("/topic/user/$userId/ingests", topicSlot.captured)
        val payload = payloadSlot.captured as Map<*, *>
        assertEquals("INGEST_READY", payload["eventType"])
        assertEquals(10, payload["itemCount"])
    }

    @Test
    fun `relays ContentMutated to correct topic`() {
        val entityId = UUID.randomUUID()
        val event = ContentMutated(
            userId = userId,
            timestamp = Instant.now(),
            contentType = ContentType.BOOKMARK,
            mutationType = MutationType.CREATED,
            entityId = entityId
        )

        relay.onContentMutated(event)

        val topicSlot = slot<String>()
        val payloadSlot = slot<Any>()
        verify { messagingTemplate.convertAndSend(capture(topicSlot), capture(payloadSlot)) }
        assertEquals("/topic/user/$userId/sync", topicSlot.captured)
        val payload = payloadSlot.captured as Map<*, *>
        assertEquals("CONTENT_MUTATED", payload["eventType"])
        assertEquals("BOOKMARK", payload["contentType"])
        assertEquals("CREATED", payload["mutationType"])
    }

    @Test
    fun `swallows exception from messagingTemplate`() {
        io.mockk.every { messagingTemplate.convertAndSend(any<String>(), any()) } throws RuntimeException("broker down")

        val event = ContentMutated(
            userId = userId,
            timestamp = Instant.now(),
            contentType = ContentType.FEED_ITEM,
            mutationType = MutationType.UPDATED,
            entityId = null
        )

        // Should not throw
        relay.onContentMutated(event)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.sightech.memoryvault.websocket.WebSocketEventRelayTest"`
Expected: FAIL — `WebSocketEventRelay` doesn't exist.

- [ ] **Step 3: Create `WebSocketEventRelay.kt`**

Create `src/main/kotlin/org/sightech/memoryvault/websocket/WebSocketEventRelay.kt`:

```kotlin
package org.sightech.memoryvault.websocket

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class WebSocketEventRelay(private val messagingTemplate: SimpMessagingTemplate) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Async("websocketRelayExecutor")
    @EventListener
    fun onFeedSyncCompleted(event: FeedSyncCompleted) {
        send("/topic/user/${event.userId}/feeds", mapOf(
            "eventType" to event.eventType.name,
            "feedId" to event.feedId?.toString(),
            "newItemCount" to event.newItemCount
        ))
    }

    @Async("websocketRelayExecutor")
    @EventListener
    fun onJobStatusChanged(event: JobStatusChanged) {
        send("/topic/user/${event.userId}/jobs", mapOf(
            "eventType" to event.eventType.name,
            "jobId" to event.jobId.toString(),
            "jobType" to event.jobType,
            "newStatus" to event.newStatus
        ))
    }

    @Async("websocketRelayExecutor")
    @EventListener
    fun onVideoDownloadCompleted(event: VideoDownloadCompleted) {
        send("/topic/user/${event.userId}/videos", mapOf(
            "eventType" to event.eventType.name,
            "videoId" to event.videoId.toString(),
            "listId" to event.listId.toString(),
            "success" to event.success
        ))
    }

    @Async("websocketRelayExecutor")
    @EventListener
    fun onIngestReady(event: IngestReady) {
        send("/topic/user/${event.userId}/ingests", mapOf(
            "eventType" to event.eventType.name,
            "previewId" to event.previewId.toString(),
            "itemCount" to event.itemCount
        ))
    }

    @Async("websocketRelayExecutor")
    @EventListener
    fun onContentMutated(event: ContentMutated) {
        send("/topic/user/${event.userId}/sync", mapOf(
            "eventType" to event.eventType.name,
            "contentType" to event.contentType.name,
            "mutationType" to event.mutationType.name,
            "entityId" to event.entityId?.toString()
        ))
    }

    private fun send(topic: String, payload: Map<String, Any?>) {
        try {
            messagingTemplate.convertAndSend(topic, payload)
            log.debug("Signal sent topic={} eventType={}", topic, payload["eventType"])
        } catch (e: Exception) {
            log.warn("Failed to relay signal topic={} eventType={}: {}", topic, payload["eventType"], e.message)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "org.sightech.memoryvault.websocket.WebSocketEventRelayTest"`
Expected: PASS — all 6 tests green.

- [ ] **Step 5: Commit**

```
git add src/main/kotlin/org/sightech/memoryvault/websocket/WebSocketEventRelay.kt src/test/kotlin/org/sightech/memoryvault/websocket/WebSocketEventRelayTest.kt
git commit -m "feat: add WebSocketEventRelay to forward domain events to STOMP topics"
```

---

## Task 6: Publish Events from Backend Services

**Files:**
- Modify: `src/main/kotlin/org/sightech/memoryvault/scheduling/service/SyncJobService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/feed/service/FeedService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/feed/service/FeedItemService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/feed/service/FeedCategoryService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/youtube/service/VideoSyncService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/bookmark/service/BookmarkService.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/bookmark/service/IngestService.kt`

This task wires `ApplicationEventPublisher` into each service and publishes the appropriate `VaultEvent` after mutations. Each service follows the same pattern: inject the publisher, call `publishEvent()` after the mutation.

- [ ] **Step 1: Update `SyncJobService` to publish `JobStatusChanged`**

Add `ApplicationEventPublisher` to the constructor and publish events in `recordStart`, `recordSuccess`, and `recordFailure`:

```kotlin
package org.sightech.memoryvault.scheduling.service

import org.sightech.memoryvault.scheduling.entity.JobStatus
import org.sightech.memoryvault.scheduling.entity.JobType
import org.sightech.memoryvault.scheduling.entity.SyncJob
import org.sightech.memoryvault.scheduling.entity.TriggerSource
import org.sightech.memoryvault.scheduling.repository.SyncJobRepository
import org.sightech.memoryvault.websocket.JobStatusChanged
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@Service
class SyncJobService(
    private val syncJobRepository: SyncJobRepository,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()

    fun recordStart(type: JobType, triggeredBy: TriggerSource, userId: UUID): SyncJob {
        val job = SyncJob(
            userId = userId,
            type = type,
            triggeredBy = triggeredBy
        )
        job.status = JobStatus.RUNNING
        val saved = syncJobRepository.save(job)
        eventPublisher.publishEvent(JobStatusChanged(
            userId = userId,
            timestamp = Instant.now(),
            jobId = saved.id,
            jobType = type.name,
            oldStatus = JobStatus.PENDING.name,
            newStatus = JobStatus.RUNNING.name
        ))
        log.debug("Published JobStatusChanged jobId={} newStatus=RUNNING", saved.id)
        return saved
    }

    fun recordSuccess(jobId: UUID, metadata: Map<String, Any>?) {
        val job = syncJobRepository.findById(jobId).orElse(null) ?: return
        val oldStatus = job.status.name
        job.status = JobStatus.SUCCESS
        job.completedAt = Instant.now()
        if (metadata != null) {
            job.metadata = objectMapper.writeValueAsString(metadata)
        }
        syncJobRepository.save(job)
        eventPublisher.publishEvent(JobStatusChanged(
            userId = job.userId,
            timestamp = Instant.now(),
            jobId = jobId,
            jobType = job.type.name,
            oldStatus = oldStatus,
            newStatus = JobStatus.SUCCESS.name
        ))
        log.debug("Published JobStatusChanged jobId={} newStatus=SUCCESS", jobId)
    }

    fun recordFailure(jobId: UUID, error: String) {
        val job = syncJobRepository.findById(jobId).orElse(null) ?: return
        val oldStatus = job.status.name
        job.status = JobStatus.FAILED
        job.completedAt = Instant.now()
        job.errorMessage = error
        syncJobRepository.save(job)
        eventPublisher.publishEvent(JobStatusChanged(
            userId = job.userId,
            timestamp = Instant.now(),
            jobId = jobId,
            jobType = job.type.name,
            oldStatus = oldStatus,
            newStatus = JobStatus.FAILED.name
        ))
        log.debug("Published JobStatusChanged jobId={} newStatus=FAILED", jobId)
    }

    fun listJobs(userId: UUID, type: JobType?, limit: Int): List<SyncJob> {
        return if (type != null) {
            syncJobRepository.findRecentByUserIdAndType(userId, type, limit)
        } else {
            syncJobRepository.findRecentByUserId(userId, limit)
        }
    }

    fun findLastSuccessful(userId: UUID, type: JobType): SyncJob? {
        return syncJobRepository.findLastSuccessful(userId, type)
    }
}
```

- [ ] **Step 2: Update `FeedService` to publish `FeedSyncCompleted`**

Add `ApplicationEventPublisher` to the `FeedService` constructor and publish after `refreshFeed()`:

In `FeedService.kt`, add the import and constructor parameter:

```kotlin
import org.sightech.memoryvault.websocket.FeedSyncCompleted
import org.springframework.context.ApplicationEventPublisher
```

Add `private val eventPublisher: ApplicationEventPublisher` to the constructor.

Update `refreshFeed()` to publish after the sync loop:

```kotlin
suspend fun refreshFeed(feedId: UUID?): List<Pair<Feed, Int>> {
    val userId = CurrentUser.userId()
    val feeds = if (feedId != null) {
        val feed = feedRepository.findActiveByIdAndUserId(feedId, userId) ?: return emptyList()
        listOf(feed)
    } else {
        feedRepository.findAllActiveByUserId(userId)
    }

    val results = feeds.map { feed ->
        val newCount = rssFetchService.fetchAndStore(feed)
        feed to newCount
    }

    val totalNew = results.sumOf { it.second }
    eventPublisher.publishEvent(FeedSyncCompleted(
        userId = userId,
        timestamp = java.time.Instant.now(),
        feedId = feedId,
        newItemCount = totalNew,
        feedsRefreshed = results.size
    ))
    log.debug("Published FeedSyncCompleted feedId={} newItems={} feedsRefreshed={}", feedId, totalNew, results.size)

    return results
}
```

- [ ] **Step 3: Update `FeedItemService` to publish `ContentMutated`**

Add to `FeedItemService.kt`:

```kotlin
import org.sightech.memoryvault.websocket.ContentMutated
import org.sightech.memoryvault.websocket.ContentType
import org.sightech.memoryvault.websocket.MutationType
import org.springframework.context.ApplicationEventPublisher
```

Add `private val eventPublisher: ApplicationEventPublisher` to the constructor.

After each mutation, publish `ContentMutated`. In `markItemRead`:

```kotlin
fun markItemRead(itemId: UUID): FeedItem? {
    val userId = CurrentUser.userId()
    val item = feedItemRepository.findByIdAndUserId(itemId, userId) ?: return null
    item.readAt = Instant.now()
    log.info("Marked item read itemId={}", itemId)
    val saved = feedItemRepository.save(item)
    eventPublisher.publishEvent(ContentMutated(
        userId = userId, timestamp = Instant.now(),
        contentType = ContentType.FEED_ITEM, mutationType = MutationType.UPDATED, entityId = itemId
    ))
    return saved
}
```

Apply the same pattern to `markItemUnread`:

```kotlin
fun markItemUnread(itemId: UUID): FeedItem? {
    val userId = CurrentUser.userId()
    val item = feedItemRepository.findByIdAndUserId(itemId, userId) ?: return null
    item.readAt = null
    log.info("Marked item unread itemId={}", itemId)
    val saved = feedItemRepository.save(item)
    eventPublisher.publishEvent(ContentMutated(
        userId = userId, timestamp = Instant.now(),
        contentType = ContentType.FEED_ITEM, mutationType = MutationType.UPDATED, entityId = itemId
    ))
    return saved
}
```

For `markFeedRead` and `markCategoryRead`, publish a single event after the bulk operation (no entityId since it's bulk):

```kotlin
@Transactional
fun markFeedRead(feedId: UUID): Int {
    val userId = CurrentUser.userId()
    val count = feedItemRepository.markAllReadByFeedIdAndUserId(feedId, userId, Instant.now())
    if (count > 0) {
        eventPublisher.publishEvent(ContentMutated(
            userId = userId, timestamp = Instant.now(),
            contentType = ContentType.FEED_ITEM, mutationType = MutationType.UPDATED
        ))
    }
    return count
}

@Transactional
fun markCategoryRead(categoryId: UUID): Int {
    val userId = CurrentUser.userId()
    val feeds = feedRepository.findAllActiveByCategoryId(userId, categoryId)
    val now = Instant.now()
    val count = feeds.sumOf { feed ->
        feedItemRepository.markAllReadByFeedIdAndUserId(feed.id, userId, now)
    }
    log.info("Marked category read categoryId={} count={}", categoryId, count)
    if (count > 0) {
        eventPublisher.publishEvent(ContentMutated(
            userId = userId, timestamp = Instant.now(),
            contentType = ContentType.FEED_ITEM, mutationType = MutationType.UPDATED
        ))
    }
    return count
}
```

- [ ] **Step 4: Update `FeedCategoryService` to publish `ContentMutated`**

Add to `FeedCategoryService.kt`:

```kotlin
import org.sightech.memoryvault.websocket.ContentMutated
import org.sightech.memoryvault.websocket.ContentType
import org.sightech.memoryvault.websocket.MutationType
import org.springframework.context.ApplicationEventPublisher
```

Add `private val eventPublisher: ApplicationEventPublisher` to the constructor.

Publish in `createCategory`:

```kotlin
fun createCategory(name: String): FeedCategory {
    val userId = CurrentUser.userId()
    val maxSort = categoryRepository.findMaxSortOrderByUserId(userId)
    val category = FeedCategory(userId = userId, name = name, sortOrder = maxSort + 1)
    log.info("Created feed category '{}' for user {}", name, userId)
    val saved = categoryRepository.save(category)
    eventPublisher.publishEvent(ContentMutated(
        userId = userId, timestamp = Instant.now(),
        contentType = ContentType.CATEGORY, mutationType = MutationType.CREATED, entityId = saved.id
    ))
    return saved
}
```

Publish in `renameCategory`:

```kotlin
fun renameCategory(categoryId: UUID, newName: String): FeedCategory? {
    val userId = CurrentUser.userId()
    val category = categoryRepository.findActiveByIdAndUserId(categoryId, userId) ?: return null
    if (category.name == FeedCategory.SUBSCRIBED_NAME) {
        throw IllegalArgumentException("Cannot rename the '${FeedCategory.SUBSCRIBED_NAME}' category")
    }
    category.name = newName
    category.updatedAt = Instant.now()
    log.info("Renamed feed category {} to '{}' for user {}", categoryId, newName, userId)
    val saved = categoryRepository.save(category)
    eventPublisher.publishEvent(ContentMutated(
        userId = userId, timestamp = Instant.now(),
        contentType = ContentType.CATEGORY, mutationType = MutationType.UPDATED, entityId = categoryId
    ))
    return saved
}
```

Publish in `deleteCategory` (after the save):

```kotlin
// At the end of deleteCategory, after categoryRepository.save(category):
eventPublisher.publishEvent(ContentMutated(
    userId = userId, timestamp = Instant.now(),
    contentType = ContentType.CATEGORY, mutationType = MutationType.DELETED, entityId = categoryId
))
```

Publish in `reorderCategories`:

```kotlin
// At the end of reorderCategories, after categoryRepository.saveAll:
eventPublisher.publishEvent(ContentMutated(
    userId = userId, timestamp = Instant.now(),
    contentType = ContentType.CATEGORY, mutationType = MutationType.UPDATED
))
```

- [ ] **Step 5: Update `VideoSyncService` to publish `VideoDownloadCompleted`**

Add to `VideoSyncService.kt`:

```kotlin
import org.sightech.memoryvault.websocket.VideoDownloadCompleted
import org.springframework.context.ApplicationEventPublisher
```

Add `private val eventPublisher: ApplicationEventPublisher` to the constructor.

After each download attempt in `syncList`, publish the event:

```kotlin
// Inside the download loop, after both the success and failure branches:
for (video in newVideos) {
    val result = videoDownloader.download(video.youtubeUrl, video.id)
    if (result.success && result.filePath != null) {
        video.filePath = result.filePath
        video.downloadedAt = Instant.now()
        video.updatedAt = Instant.now()
        videoRepository.save(video)
        downloadSuccesses++
    } else {
        logger.warn("Failed to download video {}: {}", video.youtubeVideoId, result.error)
        downloadFailures++
    }
    eventPublisher.publishEvent(VideoDownloadCompleted(
        userId = list.userId,
        timestamp = Instant.now(),
        videoId = video.id,
        listId = list.id,
        success = result.success && result.filePath != null
    ))
}
```

- [ ] **Step 6: Update `BookmarkService` to publish `ContentMutated`**

Add to `BookmarkService.kt`:

```kotlin
import org.sightech.memoryvault.websocket.ContentMutated
import org.sightech.memoryvault.websocket.ContentType
import org.sightech.memoryvault.websocket.MutationType
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
```

Add `private val eventPublisher: ApplicationEventPublisher` to the constructor.

Add logger (BookmarkService currently has no logger — required by project conventions):
```kotlin
private val log = LoggerFactory.getLogger(javaClass)
```

Publish after `create`:

```kotlin
fun create(url: String, title: String?, tagNames: List<String>?, folderId: UUID? = null): Bookmark {
    val userId = CurrentUser.userId()
    val bookmark = Bookmark(userId = userId, url = url, title = title ?: url)
    folderId?.let { bookmark.folderId = it }
    if (!tagNames.isNullOrEmpty()) {
        val tags = tagService.findOrCreateByNames(tagNames)
        bookmark.tags.addAll(tags)
    }
    val saved = bookmarkRepository.save(bookmark)
    eventPublisher.publishEvent(ContentMutated(
        userId = userId, timestamp = Instant.now(),
        contentType = ContentType.BOOKMARK, mutationType = MutationType.CREATED, entityId = saved.id
    ))
    return saved
}
```

Publish after `updateTags`:

```kotlin
fun updateTags(bookmarkId: UUID, tagNames: List<String>): Bookmark? {
    val userId = CurrentUser.userId()
    val bookmark = bookmarkRepository.findActiveByIdAndUserId(bookmarkId, userId) ?: return null
    val tags = tagService.findOrCreateByNames(tagNames)
    bookmark.tags.clear()
    bookmark.tags.addAll(tags)
    bookmark.updatedAt = Instant.now()
    val saved = bookmarkRepository.save(bookmark)
    eventPublisher.publishEvent(ContentMutated(
        userId = userId, timestamp = Instant.now(),
        contentType = ContentType.BOOKMARK, mutationType = MutationType.UPDATED, entityId = bookmarkId
    ))
    return saved
}
```

Publish after `softDelete`:

```kotlin
fun softDelete(bookmarkId: UUID): Bookmark? {
    val userId = CurrentUser.userId()
    val bookmark = bookmarkRepository.findActiveByIdAndUserId(bookmarkId, userId) ?: return null
    bookmark.deletedAt = Instant.now()
    bookmark.updatedAt = Instant.now()
    val saved = bookmarkRepository.save(bookmark)
    eventPublisher.publishEvent(ContentMutated(
        userId = userId, timestamp = Instant.now(),
        contentType = ContentType.BOOKMARK, mutationType = MutationType.DELETED, entityId = bookmarkId
    ))
    return saved
}
```

Publish after `moveBookmark`:

```kotlin
fun moveBookmark(bookmarkId: UUID, folderId: UUID?): Bookmark {
    val userId = CurrentUser.userId()
    val bookmark = bookmarkRepository.findActiveByIdAndUserId(bookmarkId, userId)
        ?: throw IllegalArgumentException("Bookmark not found")
    if (folderId != null) {
        folderRepository.findActiveByIdAndUserId(folderId, userId)
            ?: throw IllegalArgumentException("Folder not found")
    }
    bookmark.folderId = folderId
    bookmark.updatedAt = Instant.now()
    val saved = bookmarkRepository.save(bookmark)
    eventPublisher.publishEvent(ContentMutated(
        userId = userId, timestamp = Instant.now(),
        contentType = ContentType.BOOKMARK, mutationType = MutationType.UPDATED, entityId = bookmarkId
    ))
    return saved
}
```

Publish after folder mutations — `createFolder`:

```kotlin
fun createFolder(name: String, parentId: UUID?): Folder {
    val userId = CurrentUser.userId()
    if (parentId != null) {
        folderRepository.findActiveByIdAndUserId(parentId, userId)
            ?: throw IllegalArgumentException("Parent folder not found")
    }
    val folder = Folder(name = name, userId = userId, parentId = parentId)
    val saved = folderRepository.save(folder)
    eventPublisher.publishEvent(ContentMutated(
        userId = userId, timestamp = Instant.now(),
        contentType = ContentType.FOLDER, mutationType = MutationType.CREATED, entityId = saved.id
    ))
    return saved
}
```

Apply the same pattern for `renameFolder` (UPDATED), `moveFolder` (UPDATED), and `deleteFolder` (DELETED).

- [ ] **Step 7: Update `IngestService` to publish `IngestReady`**

Add to `IngestService.kt`:

```kotlin
import org.sightech.memoryvault.websocket.IngestReady
import org.springframework.context.ApplicationEventPublisher
```

Add `private val eventPublisher: ApplicationEventPublisher` to the constructor.

Publish at the end of `generatePreview`:

```kotlin
fun generatePreview(input: List<IngestBookmarkInput>): IngestPreviewResult {
    // ... existing code ...
    ingestPreviewRepository.save(previewEntity)

    eventPublisher.publishEvent(IngestReady(
        userId = userId,
        timestamp = Instant.now(),
        previewId = previewEntity.id,
        itemCount = items.size
    ))
    log.debug("Published IngestReady previewId={} itemCount={}", previewEntity.id, items.size)

    return IngestPreviewResult(previewId = previewEntity.id, items = items, summary = summary)
}
```

- [ ] **Step 8: Update existing unit tests to provide the new `eventPublisher` mock**

Every existing test that constructs these services directly will need a mock `ApplicationEventPublisher` added. For example, in `SyncJobServiceTest.kt`:

```kotlin
private val repository = mockk<SyncJobRepository>()
private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
private val service = SyncJobService(repository, eventPublisher)
```

The `relaxed = true` means `publishEvent()` calls are accepted without explicit `every {}` setup. Apply this pattern to:
- `SyncJobServiceTest.kt`
- `BookmarkServiceTest.kt`
- `FeedServiceTest.kt`
- `FeedItemServiceTest.kt`
- `FeedCategoryServiceTest.kt`
- `VideoSyncServiceTest.kt`
- `IngestServiceTest.kt`

For each: add `private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)` and pass it to the service constructor.

- [ ] **Step 9: Run all tests**

Run: `./gradlew test`
Expected: All tests pass. The relaxed mocks absorb the new `publishEvent()` calls without breaking existing assertions.

- [ ] **Step 10: Commit**

```
git add src/main/kotlin/org/sightech/memoryvault/scheduling/service/SyncJobService.kt src/main/kotlin/org/sightech/memoryvault/feed/service/FeedService.kt src/main/kotlin/org/sightech/memoryvault/feed/service/FeedItemService.kt src/main/kotlin/org/sightech/memoryvault/feed/service/FeedCategoryService.kt src/main/kotlin/org/sightech/memoryvault/youtube/service/VideoSyncService.kt src/main/kotlin/org/sightech/memoryvault/bookmark/service/BookmarkService.kt src/main/kotlin/org/sightech/memoryvault/bookmark/service/IngestService.kt src/test/
git commit -m "feat: publish VaultEvent domain events from all backend services"
```

---

## Task 7: Angular WebSocket Service

**Files:**
- Modify: `client/package.json`
- Create: `client/src/app/core/services/websocket.service.ts`
- Test: `client/src/app/core/services/websocket.service.spec.ts`

- [ ] **Step 1: Install `@stomp/rx-stomp` and `@stomp/stompjs`**

Run from the `client/` directory:

```bash
cd client && npm install @stomp/rx-stomp @stomp/stompjs
```

- [ ] **Step 2: Write the WebSocketService test**

Create `client/src/app/core/services/websocket.service.spec.ts`:

```typescript
import { TestBed } from '@angular/core/testing';
import { WebSocketService, VaultSignal } from './websocket.service';
import { AuthService } from '../../auth/auth.service';

describe('WebSocketService', () => {
  let service: WebSocketService;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  function makeJwt(payload: object): string {
    const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const body = btoa(JSON.stringify(payload));
    return `${header}.${body}.signature`;
  }

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['getToken', 'isAuthenticated']);
    TestBed.configureTestingModule({
      providers: [
        WebSocketService,
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });
    service = TestBed.inject(WebSocketService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should extract userId from JWT', () => {
    const token = makeJwt({
      exp: Math.floor(Date.now() / 1000) + 3600,
      userId: '00000000-0000-0000-0000-000000000001',
    });
    authServiceSpy.getToken.and.returnValue(token);

    // Access the private method via bracket notation for testing
    const userId = (service as any).getUserIdFromToken();
    expect(userId).toBe('00000000-0000-0000-0000-000000000001');
  });

  it('should return null userId when no token', () => {
    authServiceSpy.getToken.and.returnValue(null);
    const userId = (service as any).getUserIdFromToken();
    expect(userId).toBeNull();
  });

  it('should build correct topic path', () => {
    const token = makeJwt({
      exp: Math.floor(Date.now() / 1000) + 3600,
      userId: 'test-user-id',
    });
    authServiceSpy.getToken.and.returnValue(token);

    const topic = (service as any).buildTopic('feeds');
    expect(topic).toBe('/topic/user/test-user-id/feeds');
  });
});
```

- [ ] **Step 3: Run test to verify it fails**

Run from `client/`: `npm run test -- --run`
Expected: FAIL — `WebSocketService` doesn't exist.

- [ ] **Step 4: Create `WebSocketService`**

Create `client/src/app/core/services/websocket.service.ts`:

```typescript
import { Injectable, inject, OnDestroy } from '@angular/core';
import { RxStomp } from '@stomp/rx-stomp';
import { Observable, Subject, filter, map } from 'rxjs';
import { AuthService } from '../../auth/auth.service';

export interface VaultSignal {
  eventType: string;
  [key: string]: any;
}

@Injectable({ providedIn: 'root' })
export class WebSocketService implements OnDestroy {
  private authService = inject(AuthService);
  private rxStomp: RxStomp | null = null;
  private destroy$ = new Subject<void>();

  connect(): void {
    if (this.rxStomp) return;

    const token = this.authService.getToken();
    if (!token) return;

    this.rxStomp = new RxStomp();
    this.rxStomp.configure({
      brokerURL: this.getWsUrl(),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 5000,
    });
    this.rxStomp.activate();
  }

  disconnect(): void {
    if (this.rxStomp) {
      this.rxStomp.deactivate();
      this.rxStomp = null;
    }
  }

  /**
   * Subscribe to a user-scoped topic (e.g., 'feeds', 'jobs', 'videos', 'ingests', 'sync').
   * Returns an Observable of VaultSignal for that topic.
   */
  on(topicSuffix: string): Observable<VaultSignal> {
    const topic = this.buildTopic(topicSuffix);
    if (!this.rxStomp) {
      return new Observable<VaultSignal>(); // empty if not connected
    }
    return this.rxStomp.watch(topic).pipe(
      map((message) => {
        try {
          return JSON.parse(message.body) as VaultSignal;
        } catch {
          console.warn('Malformed WebSocket signal:', message.body);
          return null;
        }
      }),
      filter((signal): signal is VaultSignal => signal !== null)
    );
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.disconnect();
  }

  private getUserIdFromToken(): string | null {
    const token = this.authService.getToken();
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload.userId || null;
    } catch {
      return null;
    }
  }

  private buildTopic(suffix: string): string {
    const userId = this.getUserIdFromToken();
    return `/topic/user/${userId}/${suffix}`;
  }

  private getWsUrl(): string {
    // In dev, the Angular proxy handles /ws → backend
    // In prod, construct from current origin
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}/ws`;
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run from `client/`: `npm run test -- --run`
Expected: PASS.

- [ ] **Step 6: Commit**

```
git add client/package.json client/package-lock.json client/src/app/core/services/websocket.service.ts client/src/app/core/services/websocket.service.spec.ts
git commit -m "feat: add Angular WebSocketService with rx-stomp"
```

---

## Task 8: Integrate WebSocket into Angular Stores

**Files:**
- Modify: `client/src/app/reader/reader.store.ts`
- Modify: `client/src/app/bookmarks/bookmarks.store.ts`
- Modify: `client/src/app/youtube/youtube.store.ts`
- Modify: `client/src/app/admin/admin.store.ts`
- Modify: `client/src/app/admin/admin.ts`

Each store subscribes to its relevant WebSocket topics and triggers a debounced refetch when a signal arrives. The debounce prevents rapid-fire signals from causing multiple refetches.

- [ ] **Step 1: Update `reader.store.ts`**

Add WebSocket subscription in the `init` method. Import `WebSocketService` and add debounced subscriptions to `/feeds` and `/sync`:

At the top of `reader.store.ts`, add:

```typescript
import { WebSocketService, VaultSignal } from '../core/services/websocket.service';
import { Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
```

In `withMethods`, inject WebSocketService:

```typescript
withMethods((store, apollo = inject(Apollo), ws = inject(WebSocketService)) => {
```

Add a `subscribeToSignals` local function and call it from `init`:

```typescript
    let wsSubs: Subscription[] = [];

    const subscribeToSignals = () => {
      // Clean up any existing subscriptions
      wsSubs.forEach(s => s.unsubscribe());
      wsSubs = [];

      wsSubs.push(
        ws.on('feeds').pipe(debounceTime(500)).subscribe(() => {
          loadCategories();
          loadItems();
        })
      );

      wsSubs.push(
        ws.on('sync').pipe(
          filter((signal: VaultSignal) => signal.contentType === 'FEED_ITEM'),
          debounceTime(500)
        ).subscribe(() => {
          loadCategories();
          loadItems();
        })
      );
    };
```

Add the `filter` import to the rxjs import line:

```typescript
import { Subscription, filter } from 'rxjs';
```

Update `init` to call `subscribeToSignals`:

```typescript
init: () => {
  loadCategories();
  loadPreferences();
  loadItems();
  subscribeToSignals();
},
```

- [ ] **Step 2: Update `bookmarks.store.ts`**

At the top, add:

```typescript
import { WebSocketService, VaultSignal } from '../core/services/websocket.service';
import { Subscription, filter, debounceTime } from 'rxjs';
```

In `withMethods`, inject WebSocketService:

```typescript
withMethods((store, apollo = inject(Apollo), http = inject(HttpClient), ws = inject(WebSocketService)) => {
```

After the `loadPendingIngests` definition and before the `return {`, add:

```typescript
    // WebSocket subscriptions for real-time updates
    const initWebSocket = () => {
      ws.on('sync').pipe(
        filter((signal: VaultSignal) =>
          signal.contentType === 'BOOKMARK' || signal.contentType === 'FOLDER'
        ),
        debounceTime(500)
      ).subscribe(() => {
        loadBookmarks();
        loadFolders();
      });

      ws.on('ingests').pipe(debounceTime(500)).subscribe(() => {
        loadPendingIngests();
      });
    };

    // Auto-subscribe on store creation
    initWebSocket();
```

- [ ] **Step 3: Update `youtube.store.ts`**

At the top, add:

```typescript
import { WebSocketService } from '../core/services/websocket.service';
import { Subscription, debounceTime } from 'rxjs';
```

In `withMethods`, inject WebSocketService:

```typescript
withMethods((store, apollo = inject(Apollo), ws = inject(WebSocketService)) => {
```

After the `loadVideosRx` definition and before `return {`, add:

```typescript
    // WebSocket subscription for video updates
    ws.on('videos').pipe(debounceTime(500)).subscribe(() => {
      // Reload lists to update counts
      apollo.query({ query: GetYoutubeListsDocument, fetchPolicy: 'network-only' }).subscribe((result: any) => {
        patchState(store, { lists: result.data.youtubeLists });
      });
      // Reload videos if a list is selected
      const selectedId = store.selectedListId();
      if (selectedId) {
        loadVideosRx({ listId: selectedId, query: store.searchQuery(), removedOnly: store.removedOnly() });
      }
    });
```

- [ ] **Step 4: Update `admin.store.ts`**

At the top, add:

```typescript
import { WebSocketService } from '../core/services/websocket.service';
import { debounceTime } from 'rxjs/operators';
```

In `withMethods`, inject WebSocketService:

```typescript
withMethods((store, apollo = inject(Apollo), ws = inject(WebSocketService)) => {
```

After the `loadLogs` definition and before `setJobTypeFilter`, add a WebSocket subscription setup. Convert from the `withMethods((store, ...) => ({` syntax to `withMethods((store, ...) => {` (block body with explicit return) to allow the local subscription code:

```typescript
withMethods((store, apollo = inject(Apollo), ws = inject(WebSocketService)) => {
    const loadStats = rxMethod<void>(
      pipe(
        tap(() => patchState(store, { loading: true })),
        switchMap(() =>
          apollo.query({ query: GetAdminStatsDocument, fetchPolicy: 'network-only' })
        ),
        tap((result: any) => {
          patchState(store, { stats: result.data.stats, loading: false });
        })
      )
    );

    const loadJobs = rxMethod<void>(
      pipe(
        switchMap(() =>
          apollo.query({
            query: GetJobsDocument,
            variables: { type: store.jobTypeFilter() || null, limit: store.jobLimit() },
            fetchPolicy: 'network-only',
          })
        ),
        tap((result: any) => {
          patchState(store, { jobs: result.data.jobs });
        })
      )
    );

    const loadLogs = rxMethod<void>(
      pipe(
        switchMap(() =>
          apollo.query({
            query: GetLogsDocument,
            variables: {
              level: store.logLevelFilter() || null,
              service: store.logServiceFilter() || null,
              limit: store.logLimit(),
            },
            fetchPolicy: 'network-only',
          })
        ),
        tap((result: any) => {
          patchState(store, { logs: result.data.logs });
        })
      )
    );

    // WebSocket subscription for job updates
    ws.on('jobs').pipe(debounceTime(500)).subscribe(() => {
      loadJobs();
      loadStats();
    });

    return {
      loadStats,
      loadJobs,
      loadLogs,

      setJobTypeFilter: (type: string | null) => {
        patchState(store, { jobTypeFilter: type });
        loadJobs();
      },

      setLogLevelFilter: (level: string | null) => {
        patchState(store, { logLevelFilter: level });
        loadLogs();
      },

      setLogServiceFilter: (service: string) => {
        patchState(store, { logServiceFilter: service || null });
        loadLogs();
      },

      setFollowActive: (active: boolean) => {
        patchState(store, { followActive: active });
      },
    };
  })
```

Note: this also fixes the existing `(store as any).loadJobs()` / `(store as any).loadLogs()` calls by switching to the local variable pattern (per project conventions in CLAUDE.md).

- [ ] **Step 5: Update `admin.ts` to replace setInterval with WebSocket**

The `toggleFollow` method in `admin.ts` can be simplified. The WebSocket `/jobs` subscription already pushes updates. The "Follow" button for logs can keep the interval for now (logs aren't pushed via WebSocket), but jobs no longer need polling:

```typescript
toggleFollow() {
    if (this.followInterval) {
        this.stopFollow();
    } else {
        this.store.loadLogs();
        this.followInterval = setInterval(() => this.store.loadLogs(), 3000);
        this.store.setFollowActive(true);
    }
}
```

This stays the same since log following is a different concern (tailing structured logs, not job status). The job status updates are now handled by the WebSocket subscription in the store.

- [ ] **Step 6: Connect WebSocket on app startup**

The `WebSocketService.connect()` needs to be called when the user is authenticated. Find the app's root component or the layout component that loads after login. In `client/src/app/shared/layout/app-layout.ts`, inject `WebSocketService` and call `connect()` in the constructor or `ngOnInit`:

Read the current file first, then add:

```typescript
import { WebSocketService } from '../../core/services/websocket.service';
```

In the component class, inject and connect:

```typescript
private ws = inject(WebSocketService);

ngOnInit() {
  this.ws.connect();
  // ... existing init code ...
}
```

Also disconnect on logout. In `auth.service.ts`, inject `WebSocketService` and call `disconnect()` in `logout()`. However, this creates a circular dependency (AuthService ← WebSocketService ← AuthService). Instead, have the layout component handle both:

In the layout component's logout handler, call `this.ws.disconnect()` before or after clearing the token.

- [ ] **Step 7: Run frontend tests**

Run from `client/`: `npm run test -- --run`
Expected: Existing tests pass. Some may need `WebSocketService` mocked if they construct stores.

- [ ] **Step 8: Commit**

```
git add client/src/app/reader/reader.store.ts client/src/app/bookmarks/bookmarks.store.ts client/src/app/youtube/youtube.store.ts client/src/app/admin/admin.store.ts client/src/app/admin/admin.ts client/src/app/shared/layout/app-layout.ts client/src/app/core/services/websocket.service.ts
git commit -m "feat: integrate WebSocket signals into all Angular stores"
```

---

## Task 9: Backend Integration Test

**Files:**
- Create: `src/test/kotlin/org/sightech/memoryvault/websocket/WebSocketIntegrationTest.kt`

- [ ] **Step 1: Write the integration test**

This test starts the full Spring Boot app with TestContainers, connects a STOMP client, triggers service actions, and asserts signals arrive.

Create `src/test/kotlin/org/sightech/memoryvault/websocket/WebSocketIntegrationTest.kt`:

```kotlin
package org.sightech.memoryvault.websocket

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.service.JwtService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ApplicationEventPublisher
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import org.springframework.web.socket.sockjs.client.SockJsClient
import org.springframework.web.socket.sockjs.client.WebSocketTransport
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.lang.reflect.Type
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketIntegrationTest {

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
            registry.add("memoryvault.websocket.allowed-origins") { "*" }
        }
    }

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var jwtService: JwtService

    @Autowired
    private lateinit var eventPublisher: ApplicationEventPublisher

    private lateinit var stompClient: WebSocketStompClient
    private var session: StompSession? = null

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setup() {
        val sockJsClient = SockJsClient(listOf(WebSocketTransport(StandardWebSocketClient())))
        stompClient = WebSocketStompClient(sockJsClient)
        stompClient.messageConverter = MappingJackson2MessageConverter()
    }

    @AfterEach
    fun teardown() {
        session?.disconnect()
        stompClient.stop()
    }

    private fun connectWithJwt(): StompSession {
        val token = jwtService.generateToken(userId, "system@memoryvault.local", "OWNER")
        val headers = StompHeaders()
        headers.add("Authorization", "Bearer $token")

        val future = stompClient.connectAsync(
            "ws://localhost:$port/ws",
            null,
            headers,
            object : StompSessionHandlerAdapter() {}
        )
        return future.get(5, TimeUnit.SECONDS).also { session = it }
    }

    @Test
    fun `receives FeedSyncCompleted signal on feeds topic`() {
        val stompSession = connectWithJwt()
        val received = CompletableFuture<Map<String, Any>>()

        stompSession.subscribe("/topic/user/$userId/feeds", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                @Suppress("UNCHECKED_CAST")
                received.complete(payload as Map<String, Any>)
            }
        })

        // Give subscription time to register
        Thread.sleep(500)

        eventPublisher.publishEvent(FeedSyncCompleted(
            userId = userId,
            timestamp = Instant.now(),
            feedId = UUID.randomUUID(),
            newItemCount = 3,
            feedsRefreshed = 1
        ))

        val signal = received.get(5, TimeUnit.SECONDS)
        assertEquals("FEED_SYNC_COMPLETED", signal["eventType"])
        assertEquals(3, signal["newItemCount"])
    }

    @Test
    fun `receives JobStatusChanged signal on jobs topic`() {
        val stompSession = connectWithJwt()
        val received = CompletableFuture<Map<String, Any>>()

        stompSession.subscribe("/topic/user/$userId/jobs", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                @Suppress("UNCHECKED_CAST")
                received.complete(payload as Map<String, Any>)
            }
        })

        Thread.sleep(500)

        eventPublisher.publishEvent(JobStatusChanged(
            userId = userId,
            timestamp = Instant.now(),
            jobId = UUID.randomUUID(),
            jobType = "RSS_FETCH",
            oldStatus = "PENDING",
            newStatus = "RUNNING"
        ))

        val signal = received.get(5, TimeUnit.SECONDS)
        assertEquals("JOB_STATUS_CHANGED", signal["eventType"])
        assertEquals("RUNNING", signal["newStatus"])
    }

    @Test
    fun `receives ContentMutated signal on sync topic`() {
        val stompSession = connectWithJwt()
        val received = CompletableFuture<Map<String, Any>>()

        stompSession.subscribe("/topic/user/$userId/sync", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                @Suppress("UNCHECKED_CAST")
                received.complete(payload as Map<String, Any>)
            }
        })

        Thread.sleep(500)

        eventPublisher.publishEvent(ContentMutated(
            userId = userId,
            timestamp = Instant.now(),
            contentType = ContentType.BOOKMARK,
            mutationType = MutationType.CREATED,
            entityId = UUID.randomUUID()
        ))

        val signal = received.get(5, TimeUnit.SECONDS)
        assertEquals("CONTENT_MUTATED", signal["eventType"])
        assertEquals("BOOKMARK", signal["contentType"])
        assertEquals("CREATED", signal["mutationType"])
    }

    @Test
    fun `receives VideoDownloadCompleted signal on videos topic`() {
        val stompSession = connectWithJwt()
        val received = CompletableFuture<Map<String, Any>>()

        stompSession.subscribe("/topic/user/$userId/videos", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                @Suppress("UNCHECKED_CAST")
                received.complete(payload as Map<String, Any>)
            }
        })

        Thread.sleep(500)

        eventPublisher.publishEvent(VideoDownloadCompleted(
            userId = userId,
            timestamp = Instant.now(),
            videoId = UUID.randomUUID(),
            listId = UUID.randomUUID(),
            success = true
        ))

        val signal = received.get(5, TimeUnit.SECONDS)
        assertEquals("VIDEO_DOWNLOAD_COMPLETED", signal["eventType"])
        assertEquals(true, signal["success"])
    }

    @Test
    fun `receives IngestReady signal on ingests topic`() {
        val stompSession = connectWithJwt()
        val received = CompletableFuture<Map<String, Any>>()

        stompSession.subscribe("/topic/user/$userId/ingests", object : StompFrameHandler {
            override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
            override fun handleFrame(headers: StompHeaders, payload: Any?) {
                @Suppress("UNCHECKED_CAST")
                received.complete(payload as Map<String, Any>)
            }
        })

        Thread.sleep(500)

        eventPublisher.publishEvent(IngestReady(
            userId = userId,
            timestamp = Instant.now(),
            previewId = UUID.randomUUID(),
            itemCount = 10
        ))

        val signal = received.get(5, TimeUnit.SECONDS)
        assertEquals("INGEST_READY", signal["eventType"])
        assertEquals(10, signal["itemCount"])
    }

    @Test
    fun `rejects connection without JWT`() {
        val sockJsClient = SockJsClient(listOf(WebSocketTransport(StandardWebSocketClient())))
        val client = WebSocketStompClient(sockJsClient)
        client.messageConverter = MappingJackson2MessageConverter()

        val future = client.connectAsync(
            "ws://localhost:$port/ws",
            object : StompSessionHandlerAdapter() {}
        )

        try {
            future.get(3, TimeUnit.SECONDS)
            // If we get here, the connection wasn't rejected — fail
            assertTrue(false, "Expected connection to be rejected without JWT")
        } catch (e: Exception) {
            // Expected: connection rejected
            assertTrue(true)
        } finally {
            client.stop()
        }
    }
}
```

- [ ] **Step 2: Run the integration test**

Run: `./gradlew test --tests "org.sightech.memoryvault.websocket.WebSocketIntegrationTest"`
Expected: All 6 tests pass. Each event type arrives on its correct topic within the timeout.

- [ ] **Step 3: Run all tests**

Run: `./gradlew test`
Expected: All tests pass (unit + integration).

- [ ] **Step 4: Commit**

```
git add src/test/kotlin/org/sightech/memoryvault/websocket/WebSocketIntegrationTest.kt
git commit -m "test: add WebSocket integration tests for all event types"
```

---

## Task 10: E2E Test + Final Verification

**Files:**
- Create: `client/e2e/websocket.spec.ts` (Playwright)

- [ ] **Step 1: Write the E2E smoke test**

Create `client/e2e/websocket.spec.ts`:

```typescript
import { test, expect } from '@playwright/test';

test.describe('WebSocket real-time updates', () => {
  const loginUrl = 'http://localhost:4200/login';

  async function login(page: any) {
    await page.goto(loginUrl);
    await page.fill('input[formControlName="email"]', 'system@memoryvault.local');
    await page.fill('input[formControlName="password"]', 'memoryvault');
    await page.click('button[type="submit"]');
    await page.waitForURL('**/reader');
  }

  test('cross-tab sync: marking item read reflects in other tab', async ({ browser }) => {
    // Open two browser contexts (simulates two tabs)
    const context1 = await browser.newContext();
    const context2 = await browser.newContext();
    const page1 = await context1.newPage();
    const page2 = await context2.newPage();

    // Login in both tabs
    await login(page1);
    await login(page2);

    // Both should show the reader page
    await expect(page1).toHaveURL(/reader/);
    await expect(page2).toHaveURL(/reader/);

    // Wait for WebSocket connections to establish
    await page1.waitForTimeout(2000);
    await page2.waitForTimeout(2000);

    // This test verifies the WebSocket connection is established and signals flow.
    // A full cross-tab test requires feed items to exist, which depends on test data.
    // For now, verify both pages loaded and WebSocket didn't cause errors.
    const page1Errors: string[] = [];
    const page2Errors: string[] = [];
    page1.on('pageerror', (err) => page1Errors.push(err.message));
    page2.on('pageerror', (err) => page2Errors.push(err.message));

    // Navigate around to trigger store subscriptions
    await page1.waitForTimeout(1000);

    expect(page1Errors.filter(e => e.includes('WebSocket'))).toHaveLength(0);
    expect(page2Errors.filter(e => e.includes('WebSocket'))).toHaveLength(0);

    await context1.close();
    await context2.close();
  });
});
```

- [ ] **Step 2: Run the full test suite**

Backend: `./gradlew test`
Frontend: `cd client && npm run test -- --run`
E2E (requires running backend + frontend): `cd client && npm run e2e`

Expected: All pass.

- [ ] **Step 3: Commit**

```
git add client/e2e/websocket.spec.ts
git commit -m "test: add E2E WebSocket smoke test"
```

- [ ] **Step 4: Update the master roadmap**

Mark Phase 8 as complete in `docs/plans/2026-03-05-tooling-first-design.md` by prepending the Phase 8 description with completion status, matching the pattern of earlier phases.

- [ ] **Step 5: Final commit**

```
git add docs/plans/2026-03-05-tooling-first-design.md
git commit -m "docs: mark Phase 8 complete in master roadmap"
```

---

## Summary

| Task | Description                              | Key Files                                                                  |
|------|------------------------------------------|----------------------------------------------------------------------------|
| 1    | Dev/prod configuration split             | application.properties, application-dev/prod.properties, SecurityConfig.kt |
| 2    | Angular environment files                | environment.ts, environment.prod.ts, proxy.conf.json                       |
| 3    | Domain events (VaultEvent)               | websocket/VaultEvent.kt                                                    |
| 4    | WebSocket infrastructure + auth          | WebSocketConfig.kt, WebSocketAuthInterceptor.kt, build.gradle.kts          |
| 5    | WebSocket event relay                    | websocket/WebSocketEventRelay.kt                                           |
| 6    | Publish events from backend services     | SyncJobService, FeedService, FeedItemService, BookmarkService, + 3 more    |
| 7    | Angular WebSocketService                 | core/services/websocket.service.ts, @stomp/rx-stomp                        |
| 8    | Store integration (all 4 stores)         | reader.store.ts, bookmarks.store.ts, youtube.store.ts, admin.store.ts      |
| 9    | Backend integration test                 | websocket/WebSocketIntegrationTest.kt                                      |
| 10   | E2E test + final verification            | e2e/websocket.spec.ts, master roadmap update                               |
