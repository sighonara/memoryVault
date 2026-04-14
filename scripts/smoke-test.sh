#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8085}"
PASS=0
FAIL=0
TOKEN=""

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

echo "=== MemoryVault smoke test against $BASE_URL ==="
echo ""

# --- Unauthenticated checks ---
echo "-- Public endpoints --"
check "Health endpoint" "$BASE_URL/actuator/health"
check "Home page serves HTML" "$BASE_URL/"
check "GraphQL rejects unauthenticated" "$BASE_URL/graphql" 401

# --- Login flow ---
echo ""
echo "-- Authentication --"
LOGIN_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"system@memoryvault.local","password":"memoryvault"}')

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
else
  echo ""
  echo "  SKIP  Authenticated endpoint checks (no token)"
fi

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ] || exit 1
