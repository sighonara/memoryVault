# Phase 9D — Cognito Auth Swap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace local JWT auth with AWS Cognito for production while keeping local auth untouched for development.

**Architecture:** Angular uses `amazon-cognito-identity-js` to authenticate directly with Cognito (no backend involved in login). Spring Boot validates Cognito JWTs via JWKS on the `aws` profile, while local profile keeps existing `JwtService`-based auth. `CurrentUser.userId()` continues returning the database UUID (not Cognito's `sub`).

**Tech Stack:** AWS Cognito, amazon-cognito-identity-js, Spring Security, nimbus-jose-jwt (JWKS validation), Terraform

**Design Spec:** `docs/plans/2026-04-03-phase-9-infrastructure-design.md` § 9D

---

### User creation model (Phase 9D scope)

Phase 9D is **invite-only**: only the seed admin script creates users, and ongoing user creation happens via a separate admin path (MCP tool / Cognito console) added later if needed. The Terraform `aws_cognito_user_pool` must set `admin_create_user_config.allow_admin_create_user_only = true` to enforce this — self-signup is explicitly out of scope here.

Public self-signup (with a payment wall to deter abuse) is tracked as **Phase 11 — Public Self-Signup (OldReader Parity)** in the master roadmap. Do not enable self-signup or build signup UI in this phase.

---

### Pre-existing cleanup required when this phase starts

During Phase 9 deploy hardening (April 2026), `@Profile("local | test")` was **removed** from `AuthController` and `AuthService` so that `/api/auth/login` would be reachable on the AWS-hosted deploy (needed by the expanded smoke test and for manual login against the local-JWT auth that was still in place). Search for `TODO(phase-9d)` in those files.

When implementing this phase:
- Restore `@Profile("local | test")` on `AuthController`, `AuthService`, `JwtAuthenticationFilter` (Step 5) per the sections below.
- Delete the `TODO(phase-9d)` markers once the gate is back in place.
- The smoke test hardcodes `system@memoryvault.local` / `memoryvault`. Before Cognito goes live in prod, update `scripts/smoke-test.sh` to authenticate via Cognito (or split the smoke test into local-auth and cognito-auth variants selected by the target URL).

---

### Task 1: Cognito User Pool Terraform

> **Implementation note (2026-04-15):** `admin_create_user_config { allow_admin_create_user_only = true }` was added to `aws_cognito_user_pool` to enforce the invite-only model from the "User creation model" section above. It's not in the HCL snippet below but is required.

**Files:**
- Create: `terraform/cognito.tf`

- [x] **Step 1: Create cognito.tf**

```hcl
resource "aws_cognito_user_pool" "main" {
  name = "${var.project_name}-users"

  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_uppercase = true
    require_numbers   = true
    require_symbols   = true
  }

  schema {
    name                = "role"
    attribute_data_type = "String"
    mutable             = true

    string_attribute_constraints {
      min_length = 1
      max_length = 20
    }
  }

  tags = {
    Name = "${var.project_name}-cognito"
  }
}

resource "aws_cognito_user_pool_client" "spa" {
  name         = "${var.project_name}-spa"
  user_pool_id = aws_cognito_user_pool.main.id

  generate_secret = false

  explicit_auth_flows = [
    "ALLOW_USER_PASSWORD_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH"
  ]

  token_validity_units {
    access_token  = "hours"
    id_token      = "hours"
    refresh_token = "days"
  }

  access_token_validity  = 1
  id_token_validity      = 1
  refresh_token_validity = 30
}
```

- [ ] **Step 2: Add Cognito outputs**

Append to `terraform/outputs.tf`:

```hcl
output "cognito_user_pool_id" {
  description = "Cognito User Pool ID"
  value       = aws_cognito_user_pool.main.id
}

output "cognito_client_id" {
  description = "Cognito App Client ID (public, no secret)"
  value       = aws_cognito_user_pool_client.spa.id
}
```

- [ ] **Step 3: Validate**

```bash
cd terraform && terraform fmt -check && cd ..
```

- [ ] **Step 4: Commit**

```bash
git add terraform/cognito.tf terraform/outputs.tf && git commit -m "feat: add Terraform Cognito User Pool and SPA client"
```

---

### Task 2: Seed User Script

Create a one-time script to create the initial admin user in Cognito.

**Files:**
- Create: `scripts/cognito-seed-user.sh`

- [ ] **Step 1: Create the script**

```bash
#!/usr/bin/env bash
set -euo pipefail

# Create the seed admin user in Cognito.
# Run ONCE after terraform apply creates the User Pool.
# Prerequisites: AWS CLI configured, terraform outputs available.

REGION="${AWS_REGION:-us-east-1}"
USER_POOL_ID="${1:?Usage: $0 <user-pool-id> [email]}"
EMAIL="${2:-system@memoryvault.local}"
PASSWORD=$(openssl rand -base64 24)

echo "=== Creating Cognito seed user ==="
echo "Pool: $USER_POOL_ID"
echo "Email: $EMAIL"

# Create user with temporary password
aws cognito-idp admin-create-user \
  --user-pool-id "$USER_POOL_ID" \
  --username "$EMAIL" \
  --user-attributes Name=email,Value="$EMAIL" Name=email_verified,Value=true Name=custom:role,Value=OWNER \
  --region "$REGION"

# Set permanent password (skip FORCE_CHANGE_PASSWORD state)
aws cognito-idp admin-set-user-password \
  --user-pool-id "$USER_POOL_ID" \
  --username "$EMAIL" \
  --password "$PASSWORD" \
  --permanent \
  --region "$REGION"

echo ""
echo "=== Seed user created ==="
echo "Email: $EMAIL"
echo "Password: $PASSWORD"
echo ""
echo "SAVE THIS PASSWORD — it will not be shown again."
echo "You can change it later via: aws cognito-idp admin-set-user-password"
```

- [ ] **Step 2: Make executable**

```bash
chmod +x scripts/cognito-seed-user.sh
```

- [ ] **Step 3: Commit**

```bash
git add scripts/cognito-seed-user.sh && git commit -m "feat: add Cognito seed user script"
```

---

### Task 3: CognitoJwtFilter (Spring Boot)

Add a Cognito JWT validation filter that activates under the `aws` profile.

> **Implementation notes (2026-04-15):**
> - **Marker interface instead of raw `OncePerRequestFilter` injection.** The plan originally had `SecurityConfig` inject `OncePerRequestFilter` directly (Step 6). That is fragile — it only works as long as *no other* `OncePerRequestFilter` bean exists in the context (e.g. metrics, CORS, request logging). We introduced `AppAuthenticationFilter` (an `abstract class` extending `OncePerRequestFilter`) as a marker. `JwtAuthenticationFilter` and `CognitoJwtFilter` both extend it, and `SecurityConfig` injects the marker type. This is isolated to our two auth filters and can't collide with unrelated filter beans.
> - **`UserRepository.findByEmail(...)` does not exist.** The plan's filter code calls `userRepository.findByEmail(claims.email)`, but the actual repository method is `findByEmailAndDeletedAtIsNull(...)`. The correct method is used in the implementation — it also excludes soft-deleted users from authenticating, which is the desired behavior. A test case covers the missing/soft-deleted-user path.
> - **`SecurityContextHolder.getContext().authentication` is nullable in Kotlin.** The plan's test asserts `assertEquals(userId.toString(), auth.principal)` without `!!`; Kotlin requires a non-null assertion to access `.principal`.

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/config/AppAuthenticationFilter.kt` (marker abstract class)
- Create: `src/main/kotlin/org/sightech/memoryvault/config/CognitoJwtFilter.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/config/CognitoTokenValidator.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/config/CognitoJwtFilterTest.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/config/JwtAuthenticationFilter.kt` (extend `AppAuthenticationFilter`, add `@Profile("local | test")`)
- Modify: `src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt` (inject `AppAuthenticationFilter` instead of `JwtAuthenticationFilter`)
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add nimbus-jose-jwt dependency**

In `build.gradle.kts`, add after the AWS SDK dependencies:

```kotlin
// Cognito JWT validation (JWKS)
implementation("com.nimbusds:nimbus-jose-jwt:10.3")
```

- [ ] **Step 2: Write the failing tests**

Create `src/test/kotlin/org/sightech/memoryvault/config/CognitoJwtFilterTest.kt`:

```kotlin
package org.sightech.memoryvault.config

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.repository.UserRepository
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CognitoJwtFilterTest {

    private val userRepository = mockk<UserRepository>()
    private val tokenValidator = mockk<CognitoTokenValidator>()
    private val filterChain = mockk<FilterChain>(relaxed = true)
    private lateinit var filter: CognitoJwtFilter

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        filter = CognitoJwtFilter(tokenValidator, userRepository)
    }

    @Test
    fun `sets security context when token is valid`() {
        val userId = UUID.randomUUID()
        val user = mockk<User> {
            every { id } returns userId
            every { role } returns org.sightech.memoryvault.auth.entity.UserRole.OWNER
        }

        every { tokenValidator.validate("valid-token") } returns CognitoClaims("test@example.com", "OWNER")
        every { userRepository.findByEmail("test@example.com") } returns user

        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer valid-token")

        filter.doFilter(request, MockHttpServletResponse(), filterChain)

        val auth = SecurityContextHolder.getContext().authentication
        assertEquals(userId.toString(), auth.principal)
    }

    @Test
    fun `skips auth for requests without Authorization header`() {
        val request = MockHttpServletRequest()
        filter.doFilter(request, MockHttpServletResponse(), filterChain)
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `skips auth when token validation fails`() {
        every { tokenValidator.validate("bad-token") } returns null

        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer bad-token")

        filter.doFilter(request, MockHttpServletResponse(), filterChain)
        assertNull(SecurityContextHolder.getContext().authentication)
    }
}
```

- [ ] **Step 3: Create CognitoClaims and CognitoTokenValidator**

Create `src/main/kotlin/org/sightech/memoryvault/config/CognitoTokenValidator.kt`:

```kotlin
package org.sightech.memoryvault.config

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.DefaultJOSEObjectClaimsVerifier
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.net.URL

data class CognitoClaims(val email: String, val role: String)

@Component
@Profile("aws")
class CognitoTokenValidator(
    @Value("\${memoryvault.cognito.region:us-east-1}")
    private val region: String,
    @Value("\${memoryvault.cognito.user-pool-id}")
    private val userPoolId: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val jwksUrl = "https://cognito-idp.$region.amazonaws.com/$userPoolId/.well-known/jwks.json"

    private val jwtProcessor by lazy {
        val jwkSource = JWKSourceBuilder.create<SecurityContext>(URL(jwksUrl))
            .cache(true)
            .build()
        val keySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, jwkSource)
        DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = keySelector
            jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
                JWTClaimsSet.Builder().issuer("https://cognito-idp.$region.amazonaws.com/$userPoolId").build(),
                setOf("sub", "email", "token_use")
            )
        }
    }

    fun validate(token: String): CognitoClaims? {
        return try {
            val claims = jwtProcessor.process(token, null)
            val email = claims.getStringClaim("email") ?: return null
            val role = claims.getStringClaim("custom:role") ?: "VIEWER"
            CognitoClaims(email, role)
        } catch (e: Exception) {
            log.debug("Cognito token validation failed: {}", e.message)
            null
        }
    }
}
```

- [ ] **Step 4: Create CognitoJwtFilter**

Create `src/main/kotlin/org/sightech/memoryvault/config/CognitoJwtFilter.kt`:

```kotlin
package org.sightech.memoryvault.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.sightech.memoryvault.auth.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Profile("aws")
class CognitoJwtFilter(
    private val tokenValidator: CognitoTokenValidator,
    private val userRepository: UserRepository
) : AppAuthenticationFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            val token = header.substring(7)
            val claims = tokenValidator.validate(token)
            if (claims != null) {
                val user = userRepository.findByEmailAndDeletedAtIsNull(claims.email)
                if (user != null) {
                    val authorities = listOf(SimpleGrantedAuthority("ROLE_${claims.role}"))
                    val auth = UsernamePasswordAuthenticationToken(user.id.toString(), null, authorities)
                    SecurityContextHolder.getContext().authentication = auth
                } else {
                    log.warn("Cognito user {} not found in database", claims.email)
                }
            }
        }
        filterChain.doFilter(request, response)
    }
}
```

- [ ] **Step 5: Gate JwtAuthenticationFilter to local/test and extend the marker**

In `src/main/kotlin/org/sightech/memoryvault/config/JwtAuthenticationFilter.kt`:
1. Add `@Profile("local | test")` above the class declaration (alongside the existing `@Component`).
2. Change `: OncePerRequestFilter()` to `: AppAuthenticationFilter()` so `SecurityConfig` can inject the marker type uniformly across profiles.

Also remove the now-unused `OncePerRequestFilter` import.

- [ ] **Step 6: Update SecurityConfig to inject the marker type**

Create `AppAuthenticationFilter` as an abstract marker class extending `OncePerRequestFilter`. Have both `JwtAuthenticationFilter` and `CognitoJwtFilter` extend it. Then in `SecurityConfig.kt`, inject the marker rather than the concrete class:

```kotlin
// Old
private val jwtAuthenticationFilter: JwtAuthenticationFilter
// New
private val jwtAuthenticationFilter: AppAuthenticationFilter
```

Rationale: injecting `OncePerRequestFilter` directly would break if any unrelated filter bean existed in the context. A purpose-built marker interface keeps the injection unambiguous.

- [ ] **Step 7: Run tests**

```bash
./gradlew test --tests "*CognitoJwtFilterTest"
```

Expected: PASS

```bash
./gradlew test
```

Expected: All tests pass.

- [ ] **Step 8: Commit**

```bash
git add build.gradle.kts src/main/kotlin/org/sightech/memoryvault/config/AppAuthenticationFilter.kt src/main/kotlin/org/sightech/memoryvault/config/CognitoTokenValidator.kt src/main/kotlin/org/sightech/memoryvault/config/CognitoJwtFilter.kt src/main/kotlin/org/sightech/memoryvault/config/JwtAuthenticationFilter.kt src/main/kotlin/org/sightech/memoryvault/config/SecurityConfig.kt src/test/kotlin/org/sightech/memoryvault/config/CognitoJwtFilterTest.kt && git commit -m "feat: add CognitoJwtFilter for AWS profile JWT validation"
```

---

### Task 4: StompTokenValidator Interface for WebSocket Auth

Extract WebSocket JWT validation into an interface with local and Cognito implementations.

**Files:**
- Create: `src/main/kotlin/org/sightech/memoryvault/config/StompTokenValidator.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/config/LocalStompTokenValidator.kt`
- Create: `src/main/kotlin/org/sightech/memoryvault/config/CognitoStompTokenValidator.kt`
- Modify: `src/main/kotlin/org/sightech/memoryvault/config/WebSocketAuthInterceptor.kt`
- Create: `src/test/kotlin/org/sightech/memoryvault/config/CognitoStompTokenValidatorTest.kt`

- [ ] **Step 1: Create StompTokenValidator interface**

```kotlin
package org.sightech.memoryvault.config

import java.security.Principal

interface StompTokenValidator {
    fun validate(token: String): Principal?
}
```

- [ ] **Step 2: Create LocalStompTokenValidator**

```kotlin
package org.sightech.memoryvault.config

import org.sightech.memoryvault.auth.service.JwtService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.security.Principal

@Component
@Profile("local | test")
class LocalStompTokenValidator(
    private val jwtService: JwtService
) : StompTokenValidator {
    override fun validate(token: String): Principal? {
        return try {
            val userId = jwtService.validateToken(token)
            Principal { userId }
        } catch (e: Exception) {
            null
        }
    }
}
```

- [ ] **Step 3: Create CognitoStompTokenValidator**

```kotlin
package org.sightech.memoryvault.config

import org.sightech.memoryvault.auth.repository.UserRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.security.Principal

@Component
@Profile("aws")
class CognitoStompTokenValidator(
    private val cognitoTokenValidator: CognitoTokenValidator,
    private val userRepository: UserRepository
) : StompTokenValidator {
    override fun validate(token: String): Principal? {
        val claims = cognitoTokenValidator.validate(token) ?: return null
        val user = userRepository.findByEmail(claims.email) ?: return null
        return Principal { user.id.toString() }
    }
}
```

- [ ] **Step 4: Update WebSocketAuthInterceptor to use StompTokenValidator**

Replace the `JwtService` dependency with `StompTokenValidator`. Change the token validation logic to use `validator.validate(token)` and set the result as the user `Principal`.

- [ ] **Step 5: Write tests for CognitoStompTokenValidator**

```kotlin
package org.sightech.memoryvault.config

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.sightech.memoryvault.auth.entity.User
import org.sightech.memoryvault.auth.repository.UserRepository
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CognitoStompTokenValidatorTest {

    private val cognitoTokenValidator = mockk<CognitoTokenValidator>()
    private val userRepository = mockk<UserRepository>()
    private val validator = CognitoStompTokenValidator(cognitoTokenValidator, userRepository)

    @Test
    fun `returns principal with user ID when token valid`() {
        val userId = UUID.randomUUID()
        every { cognitoTokenValidator.validate("token") } returns CognitoClaims("test@example.com", "OWNER")
        every { userRepository.findByEmail("test@example.com") } returns mockk<User> {
            every { id } returns userId
        }
        assertEquals(userId.toString(), validator.validate("token")?.name)
    }

    @Test
    fun `returns null when token invalid`() {
        every { cognitoTokenValidator.validate("bad") } returns null
        assertNull(validator.validate("bad"))
    }

    @Test
    fun `returns null when user not in database`() {
        every { cognitoTokenValidator.validate("token") } returns CognitoClaims("unknown@example.com", "VIEWER")
        every { userRepository.findByEmail("unknown@example.com") } returns null
        assertNull(validator.validate("token"))
    }
}
```

- [ ] **Step 6: Run all tests**

```bash
./gradlew test
```

Expected: All tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/org/sightech/memoryvault/config/StompTokenValidator.kt src/main/kotlin/org/sightech/memoryvault/config/LocalStompTokenValidator.kt src/main/kotlin/org/sightech/memoryvault/config/CognitoStompTokenValidator.kt src/main/kotlin/org/sightech/memoryvault/config/WebSocketAuthInterceptor.kt src/test/kotlin/org/sightech/memoryvault/config/CognitoStompTokenValidatorTest.kt && git commit -m "feat: extract StompTokenValidator interface, add Cognito WebSocket auth"
```

---

### Task 5: Angular Cognito Login

Replace the Angular auth flow with Cognito's `amazon-cognito-identity-js`.

**Files:**
- Modify: `client/src/app/auth/auth.service.ts`
- Modify: `client/src/environments/environment.ts`
- Modify: `client/src/environments/environment.prod.ts`
- Modify: `client/package.json`

- [ ] **Step 1: Install amazon-cognito-identity-js**

```bash
cd client && npm install amazon-cognito-identity-js
```

- [ ] **Step 2: Add Cognito config to environment files**

In `environment.ts` (local dev — not used, kept for type consistency):

```typescript
cognito: {
  userPoolId: '',
  clientId: '',
  region: '',
}
```

In `environment.prod.ts`:

```typescript
cognito: {
  userPoolId: 'COGNITO_USER_POOL_ID',  // replaced at build time or via env
  clientId: 'COGNITO_CLIENT_ID',
  region: 'us-east-1',
}
```

- [ ] **Step 3: Create CognitoAuthService**

Create `client/src/app/auth/cognito-auth.service.ts`:

A new service that implements the same public interface as `AuthService` but uses `amazon-cognito-identity-js` internally:

- `login(email, password)` → `AuthenticationDetails` + `CognitoUser.authenticateUser()`
- On success: stores the ID token in localStorage (same key `auth_token`)
- `getToken()`, `clearToken()`, `isAuthenticated()`, `isTokenExpired()` — same logic
- `logout()` → `cognitoUser.signOut()` + clear localStorage

- [ ] **Step 4: Conditional service based on environment**

In `auth.service.ts`, check if `environment.cognito.userPoolId` is set. If it is, delegate to Cognito auth flow. If not (local dev), use the existing REST login.

This keeps a single `AuthService` that switches behavior based on environment config, avoiding the need for Angular DI profile switching.

- [ ] **Step 5: Run frontend tests**

```bash
cd client && npm test
```

Expected: All tests pass (local tests use empty cognito config, so existing REST path is used).

- [ ] **Step 6: Commit**

```bash
cd client && git add -A && git commit -m "feat: add Cognito auth support to Angular AuthService"
```

---

### Task 6: Cognito Config Properties

Add Cognito-specific properties to the Spring Boot prod config.

**Files:**
- Modify: `src/main/resources/application-prod.properties`

- [ ] **Step 1: Add Cognito properties**

Append to `application-prod.properties`:

```properties
# AWS Cognito
memoryvault.cognito.region=${MEMORYVAULT_COGNITO_REGION:us-east-1}
memoryvault.cognito.user-pool-id=${MEMORYVAULT_COGNITO_USER__POOL__ID}
```

- [ ] **Step 2: Update user_data env file**

Add to the env file block in `terraform/templates/user_data.sh`:

```
MEMORYVAULT_COGNITO_REGION=${region}
MEMORYVAULT_COGNITO_USER__POOL__ID=${cognito_user_pool_id}
```

- [ ] **Step 3: Update ec2.tf templatefile variables**

Add to the `templatefile()` call in `terraform/ec2.tf`:

```hcl
cognito_user_pool_id = aws_cognito_user_pool.main.id
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/application-prod.properties terraform/templates/user_data.sh terraform/ec2.tf && git commit -m "feat: add Cognito config properties and Terraform wiring"
```

---

## Summary Table

| Task  | Description                                  | Key Files                                                                                                                      |
|-------|----------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| 1     | Cognito User Pool Terraform                  | `terraform/cognito.tf`, `terraform/outputs.tf`                                                                                 |
| 2     | Seed user script                             | `scripts/cognito-seed-user.sh`                                                                                                 |
| 3     | CognitoJwtFilter + CognitoTokenValidator     | `config/CognitoJwtFilter.kt`, `config/CognitoTokenValidator.kt`, `SecurityConfig.kt`                                           |
| 4     | StompTokenValidator interface for WebSocket  | `config/StompTokenValidator.kt`, `LocalStompTokenValidator.kt`, `CognitoStompTokenValidator.kt`, `WebSocketAuthInterceptor.kt` |
| 5     | Angular Cognito login                        | `auth.service.ts`, `cognito-auth.service.ts`, environment files                                                                |
| 6     | Cognito config properties + Terraform wiring | `application-prod.properties`, `user_data.sh`, `ec2.tf`                                                                        |
