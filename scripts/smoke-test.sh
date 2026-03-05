#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0

check() {
  local name="$1"
  local url="$2"
  local expected_status="${3:-200}"

  status=$(curl -s -o /dev/null -w "%{http_code}" "$url")
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

check "Health endpoint" "$BASE_URL/actuator/health"

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ] || exit 1
