#!/bin/bash
set -e

echo "=== GraphQL Tests ==="

cd "$(dirname "$0")/.."

# No GraphQL resolver tests exist yet — run backend tests that cover the API layer
echo ""
echo "--- Auth Integration ---"
./gradlew test --tests "*AuthIntegrationTest"

echo ""
echo "--- Bookmark Integration ---"
./gradlew test --tests "*BookmarkIntegrationTest"

echo ""
echo "=== GraphQL tests complete ==="
