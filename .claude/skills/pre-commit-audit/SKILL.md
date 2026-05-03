---
name: pre-commit-audit
description: Forced walk-through checklist of cross-cutting concerns (security, threading, data integrity, observability, config, deploy). MUST be invoked (1) after writing or revising any file under docs/plans/**, (2) before proposing any commit of non-trivial code (anything beyond a typo fix or a single-line tweak), and (3) before declaring a plan ready for execution. Use when about to commit, about to push a PR, or about to say "ready for review."
---

# Pre-Commit Audit

You are about to commit code or finalize a plan. Before doing so, walk the seven categories below. This is a **forced enumeration**, not a narrative. For each category: state one of **OK** / **ISSUE** / **N/A** / **DEFER (to <target>)**, each in one line, with a one-sentence reason. Do not skip categories. Do not consolidate categories. Do not replace the checklist with a prose summary.

If any category surfaces an ISSUE, stop and decide with the user whether to fix now, amend the plan, or defer explicitly to a named later phase. Never silently defer.

## When this skill fires

- After writing or revising any `docs/plans/**` doc. The audit runs against the plan's current state, not what you started with.
- Before writing any commit message for a change that touches more than one file, any production code path, any security boundary, any external config, or any data model.
- Before saying "ready to commit," "ready for review," "ready for deploy," or equivalent.
- Before calling a plan "approved" or "complete."

If you are about to do one of those things and this skill hasn't fired, that's the bug. Fire it.

## The checklist

Run through these in order. Every single one, every time.

### 1. Security

- **Authentication**: is every new endpoint / interface behind the right auth mechanism? What happens with no credentials, wrong credentials, expired credentials?
- **Authorization**: who can reach this? Is the tenant / user scope enforced at the data layer or only the API?
- **Input validation / injection**: untrusted input reaches SQL / shell / template / path? Parameterized, escaped, or sandboxed?
- **Secrets in transit**: does any credential, token, or shared secret traverse plaintext HTTP, logs, error messages, stack traces, or client-visible output?
- **Secrets at rest**: is anything sensitive written to `application-*.properties`, committed config, terraform state without `sensitive = true`, or a log file?
- **Timing attacks on comparisons**: any `==` / `!=` comparing a secret? Use constant-time.
- **Rate limiting / abuse**: can an attacker replay, enumerate, or hammer this cheaply? Is there a bound?
- **CSRF / CORS / CSP**: if a new HTTP surface, are these handled consistently with the rest of the app?

### 2. Threading & concurrency

- **`@Async` return values**: are callers reading a return that won't exist once the method is fire-and-forget? (The #1 `@Async` regression.)
- **Transaction boundaries**: does the async / background thread need its own `@Transactional`? The caller's tx commits before the async thread runs.
- **Uncaught exception handler**: `@Async void` methods swallow exceptions unless configured. Is one wired?
- **Executor saturation**: bounded pool + bounded queue + explicit rejection policy (prefer `CallerRunsPolicy` for back-pressure; `AbortPolicy` surfaces as 500s).
- **Self-invocation**: Spring `@Async` / `@Transactional` only work through the proxy. Is anything calling its own annotated method from within the same class?
- **Subprocess timeouts**: any `ProcessBuilder` / shell call has a bounded `waitFor(timeout, unit)` and `destroyForcibly` on timeout?
- **Graceful shutdown**: in-flight work on JVM shutdown — do we wait, cancel, or leak?
- **Shared mutable state**: maps, lists, caches across threads — are they concurrent-safe?

### 3. Data integrity

- **Idempotency**: can this be replayed (retry, dupe webhook, cron overlap) without corrupting state?
- **Optimistic locking**: entities with `version` getting updated without loading fresh?
- **Audit trail**: does this action bypass the existing `sync_jobs` / event-log pattern? (Common when shortcutting a service.)
- **Soft deletes**: queries respect `deletedAt IS NULL`?
- **Migrations**: Flyway migration required? Is it backward-compatible during rolling deploy?
- **Foreign keys / cascade**: new FK relationships understood for delete cascade semantics?

### 4. Observability

- **Logging**: every mutation logs at INFO with entity id / count / context per project convention. Failures log at WARN or ERROR with the exception.
- **No secrets logged**: API keys, tokens, passwords, PII — not in log lines, not in exception messages that get logged.
- **Correlation**: can you trace a request end-to-end? (If `@Async` or cross-service hops are introduced, MDC propagation is usually needed.)
- **Metrics / alarms**: new failure modes that warrant a CloudWatch alarm or an admin-dashboard surface?

### 5. Configuration

- **Where does the value live?** (CLAUDE.md rules: GitHub Actions secrets / runtime discovery / `.env` / `CLAUDE.md`.) Is the new value in the right category?
- **Defaults**: variable has a sensible default where one is reasonable, or is intentionally required (so it fails fast, not silently).
- **Profile gating**: does this belong in `application-dev`, `application-prod`, both, or a new profile?
- **Secret rotation**: can this secret be rotated without downtime? If not, document as a redeploy event.

### 6. Deployment & infrastructure

- **Code vs infra vs automation**: `git push` does not run `terraform apply`. If any `terraform/**` changed, the apply step is explicit and ordered correctly (apply → wait → push).
- **user_data / replace semantics**: any `user_data.sh` change requires EC2 replacement. Is `user_data_replace_on_change = true` on the instance? Brief downtime understood and communicated?
- **Env var plumbing**: new Spring property requires: property in `application-*.properties`, env var in user_data / deploy config, Spring Boot relaxed-binding name mapping verified against precedent.
- **Pre-flight checks**: anything the deploy presumes exists on the target (yt-dlp installed, IAM perms, DNS, cert)? Documented and verified?
- **Rollback**: if this deploy goes wrong, what's the rollback? One step or many?

### 7. Error handling & edge cases

- **The thing was deleted**: entity fetched by id later — handled when soft-deleted / missing?
- **External service down**: third-party API, yt-dlp, SSM, S3 — graceful failure and sane retry?
- **Empty inputs**: empty list, null string, zero rows — not a crash.
- **Limits**: what's the biggest realistic input (500 videos in a playlist, 10k bookmarks, 2GB upload)? Does the code hold up?
- **Wrong-profile load**: does this bean load under the wrong Spring profile and crash startup, or is it correctly `@Profile`-gated?

## Output format

When you run this skill, emit a table exactly like this in the response:

```
## Audit
| # | Category | Status | Note |
|---|----------|--------|------|
| 1 | Security | OK / ISSUE / N/A / DEFER | <one sentence> |
| 2 | Threading | ... | ... |
| 3 | Data integrity | ... | ... |
| 4 | Observability | ... | ... |
| 5 | Configuration | ... | ... |
| 6 | Deployment | ... | ... |
| 7 | Error handling | ... | ... |
```

Then, only if anything is ISSUE, list the issues with a proposed resolution per issue. Do not proceed to commit / approve until either (a) every row is OK / N/A / DEFER, or (b) the user has explicitly accepted the ISSUE as known.

## Deploy sequence gate

If ANY file under `terraform/` was modified in the changeset being audited, you MUST append a **Deploy Sequence** section after the audit table. This is not optional. The section must:

1. List every step required to deploy this change, numbered, in order.
2. Label each step by what it touches: **terraform** (`terraform apply`), **code** (`git push` / merge), or **automation** (workflow run / manual action).
3. If `user_data.sh` changed, explicitly warn that EC2 replacement will occur (brief downtime).
4. If new env vars were added, state which ones need values in `terraform.tfvars` before apply.
5. State the consequence of deploying out of order (e.g., "If you merge before applying terraform, the container will crash because X env var is missing").

Do NOT mark the Deployment row as OK without this section present. Do NOT say "ready to merge" or "ready for PR" until the user has acknowledged the deploy sequence.

## Anti-patterns to avoid

- Writing "looks fine" without walking each row.
- Collapsing two categories into one because they "feel related." Walk them separately.
- Marking something N/A to save time. N/A is legitimate only when the category genuinely cannot apply (e.g., pure documentation change has no threading concerns). If you're tempted to mark N/A because a category is inconvenient, that's the tell.
- Treating DEFER as a silent escape hatch. DEFER must name a target ("defer to Phase 9F observability") — otherwise it's just an ISSUE in disguise.
- Running this audit after committing. Before. Always before.
