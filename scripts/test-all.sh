#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

echo "=== MemoryVault: Running all tests ==="

echo ""
echo "--- Kotlin/Spring tests ---"
./gradlew test

echo ""
echo "--- Content processor tests (if exists) ---"
if [ -d "content-processor" ]; then
  (cd content-processor && python3 -m pytest --tb=short)
else
  echo "content-processor not yet created, skipping."
fi

echo ""
echo "--- Lambda tests (if exist) ---"
if [ -d "lambdas" ]; then
  for dir in lambdas/*/; do
    # Note: `compgen -G` echoes back a literal path even if it doesn't exist, so use -d for the dir.
    if compgen -G "${dir}test_*.py" > /dev/null || [ -d "${dir}tests" ]; then
      echo "Testing $dir..."
      (cd "$dir" && python3 -m pytest --tb=short)
    else
      echo "No tests in $dir, skipping."
    fi
  done
else
  echo "lambdas/ not yet created, skipping."
fi

echo ""
echo "--- GraphQL tests ---"
"$(dirname "$0")/test-graphql.sh"

echo ""
echo "--- Frontend unit tests ---"
"$(dirname "$0")/test-frontend.sh"

echo ""
echo "--- E2E tests (Playwright) ---"
source "$(dirname "$0")/require-backend.sh"
cd "$(dirname "$0")/../client"
npm run e2e

echo ""
echo "=== All tests passed ==="
