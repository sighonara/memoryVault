# Phase 9 — Infrastructure: Design Spec

## Overview

Phase 9 deploys MemoryVault to AWS, replacing local-only development with a production-ready infrastructure. It covers six sub-projects executed in dependency order.

**Decisions made during brainstorming:**
- AWS account: starting from scratch (no existing resources)
- Deployment model: EC2 + Docker (single t3.small instance, not ECS/Fargate)
- VPC: Simple — public subnets only, no NAT Gateway (~$47/mo baseline)
- Domain: New domain registered through Route 53, HTTPS via ALB + ACM
- Cognito: Custom UI (keep existing Angular login form, call Cognito API directly)
- Lambda + video downloads: Lambda schedules jobs, EC2 does the yt-dlp heavy lifting
- Cost tracking: @Scheduled in Spring Boot (not Lambda) for daily cost refresh

**Estimated monthly AWS cost:** ~$31/mo baseline (EC2 t3.small $15, RDS db.t3.micro $15, Route 53 $0.50, S3/Lambda negligible). ALB ($16/mo) omitted for now — Caddy reverse proxy on EC2 handles HTTPS with free Let's Encrypt certs. ALB can be added later if needed.

## Sub-Project Decomposition

| #  | Sub-project                      | Depends on  | Deliverable                                                                                                          |
|----|----------------------------------|-------------|----------------------------------------------------------------------------------------------------------------------|
| 9A | AWS Foundation + Terraform       | Nothing     | AWS account, Terraform for VPC, EC2, RDS, S3, ALB, Route 53, ACM, IAM, ECR. Dockerized Spring Boot deploys and runs. |
| 9B | CI/CD Pipeline                   | 9A          | GitHub Actions: test, build Docker image, push to ECR, deploy to EC2 via SSM.                                        |
| 9C | AWS Service Implementations      | 9A          | S3StorageService, CloudWatchLogService implementations. Wire up @Profile("aws").                                     |
| 9D | Cognito Auth Swap                | 9A          | Cognito User Pool, Angular login calls Cognito API, Spring Boot validates Cognito JWTs.                              |
| 9E | Lambda Scheduling + Video Worker | 9A, 9C      | EventBridge rules, RSS/YouTube sync Lambdas, video download dispatch (Lambda → EC2).                                 |
| 9F | Cost Tracking                    | 9A          | AwsCostRecord entity, CostService, CostTools MCP tool.                                                               |

---

## Profile Naming Convention

Phase 9 replaces the `@Profile("local")` negation pattern with explicit profile names for readability and future extensibility (e.g., Azure, staging):

| Profile  | When active                                | Purpose                                                    |
|----------|--------------------------------------------|------------------------------------------------------------|
| `local`  | Default (`spring.profiles.default=local`)  | Local development, no AWS credentials needed               |
| `aws`    | `SPRING_PROFILES_ACTIVE=aws` in production | AWS services (S3, CloudWatch, Cognito, SSM, Cost Explorer) |

**Migration from current state:**
- Rename `application-dev.properties` → `application-local.properties`
- Change `spring.profiles.default=dev` → `spring.profiles.default=local` in `application.properties`
- Replace all `@Profile("local")` annotations with `@Profile("local")`
- Existing `@Profile("aws")` annotations stay as-is
- `application-prod.properties` stays as-is (separate concern from the service profile — `prod` controls DB URLs, CORS origins, etc.; `aws` controls which service implementations to use)

This means production runs with `SPRING_PROFILES_ACTIVE=aws,prod` — `aws` for service implementations, `prod` for environment-specific config.

---

## Prerequisites (Manual Steps Before Terraform)

These steps must be done manually before any Terraform can run:

1. **Create an AWS account** — sign up at aws.amazon.com, set up billing with a payment method
2. **Enable Cost Explorer** — must be explicitly enabled in the AWS Billing console (takes ~24 hours to start collecting data, needed for 9F)
3. **Create an IAM admin user** — create an IAM user with `AdministratorAccess` policy, generate access keys for CLI/Terraform use
4. **Install Terraform CLI** — `brew install terraform` (macOS) or download from hashicorp.com
5. **Install AWS CLI** — `brew install awscli` (macOS) or from aws.amazon.com/cli
6. **Configure AWS credentials** — `aws configure` with the IAM access keys from step 3
7. **Run the Terraform bootstrap script** — creates the S3 bucket for Terraform state and DynamoDB table for state locking (these can't be managed by Terraform itself)
8. **Register the domain** — done via Route 53 in the AWS console (requires active account + payment). Domain registration is a separate purchase (~$12/year for .com). After registration, Route 53 automatically creates a hosted zone.

Steps 1-6 are one-time setup. Step 7 is automated by a bootstrap script in `terraform/bootstrap/`. Step 8 can be done at any point before DNS is needed.

---

## 9A: AWS Foundation + Terraform

### Architecture

Single-region deployment. One VPC with two public subnets across two AZs (for future ALB if needed). EC2 in one subnet runs the Spring Boot app as a Docker container with Caddy as a reverse proxy for HTTPS (Let's Encrypt auto-renewing certs). RDS PostgreSQL in a DB subnet group (public subnets but access restricted by security group to EC2 only). S3 bucket for video/content storage.

**Future ALB path:** The architecture supports adding an ALB later (two AZs, target group-compatible health check). Adding an ALB would mean: create ALB + listener + target group in Terraform, update Route 53 A record from EC2 IP to ALB alias, switch from Caddy to ACM for TLS. No application code changes needed.

### AWS Resources

| Resource        | Config                                                                                        |
|-----------------|-----------------------------------------------------------------------------------------------|
| VPC             | 10.0.0.0/16, 2 public subnets in different AZs                                                |
| EC2             | t3.small, Amazon Linux 2023, Docker pre-installed via user_data                               |
| RDS             | db.t3.micro, PostgreSQL 16, single-AZ, 20GB gp3                                               |
| S3              | One bucket, versioning enabled, lifecycle rules (90d → IA, 365d → Glacier Deep Archive)       |
| Caddy           | Reverse proxy on EC2. HTTPS via Let's Encrypt (auto-renewing). Proxies 443 → localhost:8085, redirects 80 → 443. |
| Route 53        | Hosted zone + A record pointing to EC2 Elastic IP. Domain registered through Route 53.        |
| Elastic IP      | Static IP for EC2 so DNS records don't change on instance restart                             |
| ECR             | Repository for the Spring Boot Docker image                                                   |
| Security Groups | EC2: 80/443 from anywhere (Caddy), 22 from your IP. RDS: 5432 from EC2 only.                  |
| IAM             | EC2 instance role with S3, CloudWatch Logs, ECR pull, SSM permissions                         |
| SSM             | For deploy commands — no SSH needed for deploys                                               |

### Terraform Structure

```
terraform/
├── main.tf          # Provider, backend (S3 for state), variables
├── vpc.tf           # VPC, subnets, internet gateway, route tables
├── ec2.tf           # Instance, user_data, key pair
├── rds.tf           # PostgreSQL instance, subnet group
├── s3.tf            # Content bucket + Terraform state bucket
├── dns.tf           # Route 53 hosted zone, records, Elastic IP
├── ecr.tf           # Container registry
├── iam.tf           # Roles, policies, instance profile
├── security.tf      # Security groups
├── outputs.tf       # EC2 IP, RDS endpoint, etc.
└── variables.tf     # Region, instance sizes, domain name, etc.
```

### Terraform State

Stored in S3 with DynamoDB locking. The state bucket and DynamoDB table are created via a small bootstrap script (`terraform/bootstrap/`) since Terraform can't create its own backend.

### Dockerfile

Multi-stage build added to the project root:
- Stage 1: Gradle builds the JAR (uses Gradle Docker image)
- Stage 2: Slim JRE image runs the JAR
- Exposes port 8085
- Health check via /actuator/health

### EC2 user_data

Shell script that runs on first boot:
- Installs Docker
- Installs Caddy (reverse proxy, auto-HTTPS via Let's Encrypt)
- Installs AWS CloudWatch agent (for log shipping)
- Installs SSM agent
- Installs yt-dlp + ffmpeg (for video downloads in 9E)
- Authenticates to ECR, pulls latest image, runs the container
- Container environment variables: DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD, JWT_SECRET, CORS_ALLOWED_ORIGINS, SPRING_PROFILES_ACTIVE=aws
- Configures Caddy: reverse proxy HTTPS (443) → localhost:8085, redirect HTTP (80) → HTTPS

---

## 9B: CI/CD Pipeline

### GitHub Actions Workflows

**`ci.yml` — runs on every push and PR:**
- Checkout code
- Run backend tests (`./gradlew test`)
- Run frontend tests (`cd client && npm test`)
- Build Angular for production (`cd client && npm run build`)
- Build Docker image (verify it builds, don't push)

**`deploy.yml` — runs after CI passes on `master` (via `workflow_run` trigger), or manual trigger via `workflow_dispatch`:**
- Does NOT re-run tests (CI already passed on this commit)
- Build Docker image
- Authenticate to ECR, push image tagged with git SHA + `latest`
- Deploy to EC2 via SSM `SendCommand`: pull new image, stop old container, start new one
- Run smoke test against the live instance (`scripts/smoke-test.sh` with prod URL)

### GitHub Secrets

- `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` (deploy IAM user)
- `AWS_REGION`
- `AWS_ACCOUNT_ID`
- `EC2_INSTANCE_ID`

### No Staging Environment

For a single-user app, the CI test suite is the gate. Deploy goes straight to prod after tests pass. Staging can be added later if needed.

### Rollback

Previous Docker images remain tagged in ECR. Rolling back = SSM command to pull a previous tag. No automated rollback for now.

---

## 9C: AWS Service Implementations

### S3StorageService

- Implements the existing `StorageService` interface
- Uses AWS SDK v2 `S3Client`
- Config properties: `memoryvault.storage.s3-bucket`, `memoryvault.storage.s3-region`
- `store()` → `PutObjectRequest`
- `retrieve()` → `GetObjectRequest`
- `delete()` → `DeleteObjectRequest`
- `exists()` → `HeadObjectRequest`
- `usedBytes()` → `ListObjectsV2` with size summation
- Annotation: `@Component @Profile("aws")`

### CloudWatchLogService

- Implements the existing `LogService` interface
- Uses AWS SDK v2 `CloudWatchLogsClient`
- Config properties: `memoryvault.logging.cloudwatch-log-group`, `memoryvault.logging.cloudwatch-region`
- Uses CloudWatch Logs Insights: `startQuery()` → poll `getQueryResults()` until complete
- Maps results to the existing `LogEntry` data class
- Filter by level/logger translated to Insights query syntax
- Annotation: `@Component @Profile("aws")`

### CloudWatch Log Shipping

- Docker container writes JSON structured logs to stdout (existing logback config)
- CloudWatch agent on EC2 captures Docker container logs → ships to log group
- Terraform creates the log group with 30-day retention (matches local config)

### Gradle Dependencies

- `software.amazon.awssdk:bom` (version management)
- `software.amazon.awssdk:s3`
- `software.amazon.awssdk:cloudwatchlogs`

### Minimal Changes to Existing Code

Local implementations (`LocalStorageService`, `LocalLogService`) and their interfaces remain functionally untouched. The only change is the profile rename: `@Profile("!aws")` → `@Profile("local")` on all existing local-profile beans (done as part of the profile naming convention migration).

---

## 9D: Cognito Auth Swap

### Cognito User Pool (Terraform)

- Email as primary sign-in attribute
- Password policy: 8+ chars, mixed case, numbers, symbols
- No MFA initially (can enable later)
- One App Client: public client (no secret) for single-page application (SPA) use
- Custom attribute: `custom:role` (OWNER/VIEWER)

### Seed User Migration

- Create `system@memoryvault.local` user in Cognito via Terraform or a one-time script
- Generate a strong random password and store it in AWS Secrets Manager (retrieve via `aws secretsmanager get-secret-value` or set a new password via the Cognito console)
- Local dev keeps the simple `memoryvault` password — this only affects the Cognito production user
- Set `custom:role = OWNER`

### Angular Changes

- Add `amazon-cognito-identity-js` npm package
- `AuthService.login()` calls Cognito `InitiateAuth` instead of `POST /api/auth/login`
- On success: Cognito returns ID token + access token + refresh token
- Store tokens same way (localStorage), use ID token as Bearer token
- Token refresh handled by Cognito SDK automatically
- New environment config fields: `cognitoUserPoolId`, `cognitoClientId`, `cognitoRegion`
- Login form UI stays identical

### Spring Boot Changes

- New `CognitoJwtFilter` (`@Profile("aws")`) replaces `JwtAuthenticationFilter`
- Validates Cognito JWT against JWKS endpoint: `https://cognito-idp.{region}.amazonaws.com/{poolId}/.well-known/jwks.json`
- Extracts `email` and `custom:role` from Cognito claims
- Looks up the local `User` entity by email to get the database UUID
- Sets that database UUID in `SecurityContext` (not Cognito's `sub` — our app uses its own UUIDs)
- `CurrentUser.userId()` continues to work unchanged because SecurityContext still holds our database UUID

### WebSocket Auth on AWS

- `WebSocketAuthInterceptor` needs updating for Cognito JWT validation
- Extract into `StompTokenValidator` interface with two implementations:
  - `LocalStompTokenValidator` (`@Profile("local")`) — uses existing JwtService
  - `CognitoStompTokenValidator` (`@Profile("aws")`) — validates against Cognito JWKS
- `WebSocketAuthInterceptor` injects `StompTokenValidator` interface

### What Stays the Same

- `AuthService` + `AuthController` remain with `@Profile("local")` for local dev
- `JwtAuthenticationFilter` remains with `@Profile("local")` for local dev
- `JwtService` remains for local dev
- Local dev workflow completely unaffected

### Build-Time Configuration

Since we're on EC2 (not a managed service with environment injection), Cognito config (User Pool ID, Client ID, region) is baked into `environment.prod.ts` at Angular build time. These are not secrets — they're public client identifiers.

---

## 9E: Lambda Scheduling + Video Worker

### EventBridge Rules (Terraform)

- RSS feed sync: cron schedule (configurable, default every 30 minutes)
- YouTube list sync: cron schedule (configurable, default every 6 hours)
- Each rule triggers its corresponding Lambda function

### RSS Sync Lambda (Python)

- Small Python function that calls the Spring Boot API on EC2
- Endpoint: `POST /api/internal/sync/feeds`
- Reuses existing `FeedService.refreshAllFeeds()` logic — no code duplication
- Lambda just acts as a scheduler that pokes the app

### YouTube Sync Lambda (Python)

- Same pattern — calls `POST /api/internal/sync/youtube`
- Triggers `VideoSyncService` to check for new videos across all lists

### Video Download Dispatch (Lambda → EC2)

- When `VideoSyncService` identifies new videos, it needs to download them
- On AWS profile: `LambdaVideoDownloader` sends an SSM `SendCommand` to the EC2 instance
- The SSM command runs a script on EC2 that:
  1. Downloads the video with yt-dlp (already installed on EC2)
  2. Uploads to S3 via the app's StorageService
  3. Updates the video record in the database
- Reuses existing `YtDlpService` and `StorageService` — no code duplication

### yt-dlp Update Strategy

Rather than a weekly cron (which leaves a failure window when YouTube changes their API), yt-dlp is updated just-in-time:

- **Before each download:** The video download endpoint runs `pip install --upgrade yt-dlp` before invoking yt-dlp. This is a fast no-op when already current (~1 second) and ensures the latest version is always used.
- **Manual update endpoint:** `POST /api/internal/ytdlp/update` forces an update and returns the installed version. Useful when you notice download failures and want to trigger an update immediately (e.g., via MCP tool or curl).
- **MCP tool:** `updateYtDlp` tool (in existing `YoutubeTools.kt` or a new `MaintenanceTools.kt`) that calls the internal endpoint. Available on both `local` and `aws` profiles since yt-dlp runs on EC2 in both cases.

### LambdaVideoDownloader Implementation

- Implements `VideoDownloader` interface
- `@Component @Profile("aws")`
- Uses AWS SDK v2 `SsmClient` to send RunCommand
- SSM command calls an internal API endpoint: `POST /api/internal/videos/download` with the video ID
- The download endpoint runs yt-dlp update before each download attempt (see above)
- The endpoint invokes `YtDlpService` + `StorageService` inside the running app — no code duplication
- Returns `DownloadResult` (async — sets status to PENDING, internal endpoint updates to SUCCESS/FAILED)

### Internal API Security

- New endpoints under `/api/internal/**`
- Secured by shared API key header (`X-Internal-Key`) checked against environment variable `INTERNAL_API_KEY`
- `InternalSyncController` (`@Profile("aws")`) with endpoints:
  - `POST /api/internal/sync/feeds`
  - `POST /api/internal/sync/youtube`
  - `POST /api/internal/videos/download` (used by LambdaVideoDownloader via SSM)
  - `POST /api/internal/costs/refresh` (used by 9F)

### SpringJobScheduler on AWS

- `FeedSyncRegistrar` and `YoutubeSyncRegistrar` still register with cron `"-"` (disabled)
- EventBridge + Lambda replaces the Spring cron scheduling
- `SyncJobService` still tracks job execution (internal endpoints call it)
- `triggerNow()` still works for manual triggers from MCP/UI

### Lambda Structure

```
lambdas/
├── feed-sync/
│   ├── handler.py
│   └── requirements.txt
└── youtube-sync/
    ├── handler.py
    └── requirements.txt
```

Packaged as ZIP, uploaded to S3, deployed via Terraform.

### Gradle Dependency

- `software.amazon.awssdk:ssm`

---

## 9F: Cost Tracking

### AwsCostRecord Entity

New Flyway migration creates `aws_cost_records` table:
- `id` UUID PRIMARY KEY
- `billing_cycle_start` DATE NOT NULL
- `billing_cycle_end` DATE NOT NULL
- `compute_cost_usd` DECIMAL(10,4) NOT NULL DEFAULT 0
- `storage_cost_usd` DECIMAL(10,4) NOT NULL DEFAULT 0
- `transfer_cost_usd` DECIMAL(10,4) NOT NULL DEFAULT 0
- `total_cost_usd` DECIMAL(10,4) NOT NULL DEFAULT 0
- `fetched_at` TIMESTAMPTZ NOT NULL
- `created_at` TIMESTAMPTZ NOT NULL DEFAULT now()

### CostService (`@Profile("aws")`)

- Uses AWS SDK v2 `CostExplorerClient`
- `fetchCurrentCycle()` — queries Cost Explorer for current month, grouped by service
- Maps AWS service names to three buckets:
  - Compute: EC2, Lambda
  - Storage: S3, RDS
  - Transfer: Data Transfer
- Stores result as an `AwsCostRecord`
- `getLatestCost()` — returns most recent record
- `getCostHistory(months)` — returns records for last N months

### Cost Refresh Schedule

- `@Scheduled(cron = "0 0 6 * * *")` in a `CostRefreshTask` (`@Profile("aws")`)
- Runs daily at 6 AM (AWS cost data is delayed ~24 hours)
- Also callable via `POST /api/internal/costs/refresh`

### LocalCostService (`@Profile("local")`)

- Returns empty list / informative message
- Keeps the app bootable locally without AWS credentials

### MCP Tool

- `CostTools.kt` with `getAwsCosts` tool
- Returns current billing cycle costs and optional history
- On local profile: returns a message saying cost tracking is only available in production

### Gradle Dependency

- `software.amazon.awssdk:costexplorer`

---

## Cross-Cutting Concerns

| Concern       | Approach                                                                                                                                                                   |
|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Auth**      | Cognito (`@Profile("aws")`) / local JWT (`@Profile("local")`), explicit profile toggle                                                                                     |
| **Errors**    | AWS SDK exceptions caught and logged, wrapped in service-layer exceptions. All AWS service calls wrapped in try/catch with WARN-level logging on failure.                  |
| **Threading** | No changes — async WebSocket relay stays, Lambda is external, SSM commands are fire-and-forget with status polling                                                         |
| **Logging**   | JSON structured logs → CloudWatch (AWS) / local file (dev). EC2 CloudWatch agent ships Docker stdout. 30-day retention both environments.                                  |
| **Lifecycle** | Docker container managed by SSM commands. Caddy health check proxied to /actuator/health. Auto-restart via Docker --restart=unless-stopped.                                |
| **Limits**    | RDS 20GB gp3, S3 lifecycle tiering (90d→IA, 365d→Glacier), Lambda 15-min timeout, EC2 t3.small (2 vCPU, 2GB RAM)                                                           |
| **Config**    | All secrets via environment variables. Terraform outputs feed deploy scripts. No secrets in code or Docker images.                                                         |
| **Security**  | HTTPS via Caddy+Let's Encrypt. Security groups restrict network access. IAM least-privilege roles. Internal API key for Lambda→EC2 calls. No SSH needed for deploys (SSM). |

---

## Testing Strategy

| Sub-project        | Testing approach                                                                                                                  |
|--------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| 9A (Terraform)     | `terraform plan` review, smoke test after `terraform apply`                                                                       |
| 9B (CI/CD)         | Trigger workflow, verify image in ECR, verify deploy completes, smoke test                                                        |
| 9C (S3/CloudWatch) | Unit tests with mocked AWS SDK clients. Integration test against LocalStack or real AWS in CI.                                    |
| 9D (Cognito)       | Unit tests for CognitoJwtFilter with mocked JWKS. E2E login test against Cognito in staging.                                      |
| 9E (Lambda)        | Unit tests for Lambda handlers. Integration test: EventBridge triggers Lambda, Lambda calls internal API, job tracked in SyncJob. |
| 9F (Cost)          | Unit tests for CostService with mocked CostExplorerClient. Verify daily schedule runs.                                            |

---

## Configuration Summary

### New Environment Variables (Production)

| Variable                                   | Source                                                 | Used by                                  |
|--------------------------------------------|--------------------------------------------------------|------------------------------------------|
| `DATABASE_URL`                             | Terraform output (RDS endpoint)                        | Spring Boot                              |
| `DATABASE_USERNAME`                        | Terraform variable                                     | Spring Boot                              |
| `DATABASE_PASSWORD`                        | Terraform variable (sensitive)                         | Spring Boot                              |
| `JWT_SECRET`                               | Generated, stored in AWS Secrets Manager               | Spring Boot (local auth fallback)        |
| `CORS_ALLOWED_ORIGINS`                     | Domain URL from Terraform                              | Spring Boot                              |
| `SPRING_PROFILES_ACTIVE`                   | `aws,prod` (`aws` for services, `prod` for env config) | Spring Boot                              |
| `MEMORYVAULT_STORAGE_S3_BUCKET`            | Terraform output                                       | S3StorageService                         |
| `MEMORYVAULT_STORAGE_S3_REGION`            | Terraform variable                                     | S3StorageService                         |
| `MEMORYVAULT_LOGGING_CLOUDWATCH_LOG_GROUP` | Terraform output                                       | CloudWatchLogService                     |
| `MEMORYVAULT_LOGGING_CLOUDWATCH_REGION`    | Terraform variable                                     | CloudWatchLogService                     |
| `COGNITO_USER_POOL_ID`                     | Terraform output                                       | CognitoJwtFilter                         |
| `COGNITO_CLIENT_ID`                        | Terraform output                                       | Angular (build-time)                     |
| `COGNITO_REGION`                           | Terraform variable                                     | CognitoJwtFilter + Angular               |
| `INTERNAL_API_KEY`                         | Generated, stored in Secrets Manager                   | InternalSyncController, Lambda functions |

### New Angular Environment Fields (environment.prod.ts)

| Field               | Value                 |
|---------------------|-----------------------|
| `cognitoUserPoolId` | From Terraform output |
| `cognitoClientId`   | From Terraform output |
| `cognitoRegion`     | AWS region            |

### New Gradle Dependencies

| Dependency                              | Sub-project |
|-----------------------------------------|-------------|
| `software.amazon.awssdk:bom`            | All         |
| `software.amazon.awssdk:s3`             | 9C          |
| `software.amazon.awssdk:cloudwatchlogs` | 9C          |
| `software.amazon.awssdk:cognitoidp`     | 9D          |
| `software.amazon.awssdk:ssm`            | 9E          |
| `software.amazon.awssdk:costexplorer`   | 9F          |

### New npm Dependencies

| Package                      | Sub-project |
|------------------------------|-------------|
| `amazon-cognito-identity-js` | 9D          |
