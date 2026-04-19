#!/usr/bin/env bash
# When adding a new top-level Angular route, update ALL THREE:
#   1. SecurityConfig.kt — add the path to the SPA-routes permitAll line
#   2. SpaWebConfig.kt — add the path to SpaForwardController's @GetMapping
#   3. This file — add a `check "SPA route /<path> serves HTML" "$BASE_URL/<path>"` line
# Spring Security evaluates auth per path, so a working `/` does NOT prove other SPA routes work.
set -euo pipefail

BASE_URL="${1:-http://localhost:8085}"

# Load credentials from .env at repo root if present (gitignored).
# See .env.sample for the expected keys.
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ -f "$REPO_ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$REPO_ROOT/.env"
  set +a
fi

# Credentials for the /api/auth/login smoke check. Set via env vars or
# .env. If SMOKE_PASSWORD is empty, the login check is skipped — this lets
# CI run against envs where REST /api/auth/login is gated off (prod on
# `aws` profile uses Cognito instead of the REST endpoint).
SMOKE_EMAIL="${SMOKE_EMAIL:-system@memoryvault.local}"
SMOKE_PASSWORD="${SMOKE_PASSWORD:-}"
PASS=0
FAIL=0
TOKEN=""

# Probe /api/config to detect whether the backend is using Cognito. When
# Cognito is configured, REST /api/auth/login is disabled — attempting it
# here would false-fail every prod run. Bash can't do the SRP auth flow
# Cognito requires, so we skip the authenticated block entirely in that
# case. To cover authenticated endpoints against prod, extend ssm.py or
# add a Cognito-aware smoke client.
CONFIG_JSON=$(curl -s "$BASE_URL/api/config" || echo "")
if echo "$CONFIG_JSON" | grep -q '"userPoolId":"[^"]\+"'; then
  COGNITO_ACTIVE=1
else
  COGNITO_ACTIVE=0
fi

check() {
  local name="$1"
  local url="$2"
  local expected_status="${3:-200}"
  local extra_args="${4:-}"

  status=$(curl -s -o /dev/null -w "%{http_code}" $extra_args "$url" || echo "000")
  if [ "$status" -eq "$expected_status" ]; then
    echo "  PASS  $name ($status)"
    PASS=$((PASS + 1))
  else
    echo "  FAIL  $name (expected $expected_status, got $status)"
    FAIL=$((FAIL + 1))
  fi
}

# SPA routes: SpaForwardController forwards known Angular routes to /index.html
# with a 200 status. The ErrorPageRegistrar handles truly unknown paths (404).

echo "=== MemoryVault smoke test against $BASE_URL ==="
echo ""

# --- Unauthenticated checks ---
echo "-- Public endpoints --"
check "Health endpoint" "$BASE_URL/actuator/health"
check "Home page serves HTML" "$BASE_URL/"
check "SPA route /login serves HTML" "$BASE_URL/login"
check "SPA route /reader serves HTML" "$BASE_URL/reader"
check "SPA route /bookmarks serves HTML" "$BASE_URL/bookmarks"
check "GraphQL rejects unauthenticated" "$BASE_URL/graphql" 401

# Internal sync endpoint only exists under the aws profile (Cognito active = aws profile).
if [ "$COGNITO_ACTIVE" -eq 1 ]; then
  check "Internal sync rejects without key" "$BASE_URL/api/internal/sync/feeds" 401 "-X POST"
  check "Internal cost refresh rejects without key" "$BASE_URL/api/internal/sync/costs/refresh" 401 "-X POST"
fi

# --- Login flow ---
if [ "$COGNITO_ACTIVE" -eq 1 ]; then
  echo ""
  echo "  SKIP  Authentication checks (backend uses Cognito; REST /api/auth/login is disabled)"
  echo ""
  echo "=== Results: $PASS passed, $FAIL failed ==="
  [ "$FAIL" -eq 0 ] || exit 1
  exit 0
fi

if [ -z "$SMOKE_PASSWORD" ]; then
  echo ""
  echo "  SKIP  Authentication checks (SMOKE_PASSWORD not set)"
  echo ""
  echo "=== Results: $PASS passed, $FAIL failed ==="
  [ "$FAIL" -eq 0 ] || exit 1
  exit 0
fi

echo ""
echo "-- Authentication --"
LOGIN_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" -d "{\"email\":\"$SMOKE_EMAIL\",\"password\":\"$SMOKE_PASSWORD\"}")

LOGIN_STATUS=$(echo "$LOGIN_RESPONSE" | tail -1)
LOGIN_BODY=$(echo "$LOGIN_RESPONSE" | sed '$d')

if [ "$LOGIN_STATUS" -eq 200 ]; then
  echo "  PASS  Login endpoint ($LOGIN_STATUS)"
  PASS=$((PASS + 1))
  TOKEN=$(echo "$LOGIN_BODY" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
  if [ -n "$TOKEN" ]; then
    echo "  PASS  JWT token present in response"
    PASS=$((PASS + 1))
  else
    echo "  FAIL  JWT token missing from login response"
    FAIL=$((FAIL + 1))
  fi
else
  echo "  FAIL  Login endpoint (expected 200, got $LOGIN_STATUS)"
  FAIL=$((FAIL + 1))
  echo "  SKIP  JWT token check (login failed)"
fi

# --- Authenticated checks (only if login succeeded) ---
if [ -n "$TOKEN" ]; then
  echo ""
  echo "-- Authenticated endpoints --"
  AUTH_HEADER="-H \"Authorization: Bearer $TOKEN\""

  # GraphQL introspection
  GQL_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/graphql" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"query":"{ __typename }"}')
  if [ "$GQL_STATUS" -eq 200 ]; then
    echo "  PASS  GraphQL with auth ($GQL_STATUS)"
    PASS=$((PASS + 1))
  else
    echo "  FAIL  GraphQL with auth (expected 200, got $GQL_STATUS)"
    FAIL=$((FAIL + 1))
  fi

  # REST endpoints via authenticated curl
  for endpoint in "bookmarks" "feeds" "youtube/lists"; do
    EP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
      -H "Authorization: Bearer $TOKEN" \
      "$BASE_URL/api/$endpoint")
    EP_NAME="GET /api/$endpoint"
    if [ "$EP_STATUS" -eq 200 ]; then
      echo "  PASS  $EP_NAME ($EP_STATUS)"
      PASS=$((PASS + 1))
    else
      echo "  FAIL  $EP_NAME (expected 200, got $EP_STATUS)"
      FAIL=$((FAIL + 1))
    fi
  done

  # GraphQL costs query
  COST_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$BASE_URL/graphql" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"query":"{ costs { current { totalCostUsd } monthlyTotals { month totalCostUsd } } }"}')
  if [ "$COST_STATUS" -eq 200 ]; then
    echo "  PASS  GraphQL costs query ($COST_STATUS)"
    PASS=$((PASS + 1))
  else
    echo "  FAIL  GraphQL costs query (expected 200, got $COST_STATUS)"
    FAIL=$((FAIL + 1))
  fi
else
  echo ""
  echo "  SKIP  Authenticated endpoint checks (no token)"
fi

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ] || exit 1
