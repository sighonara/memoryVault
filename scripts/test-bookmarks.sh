#!/usr/bin/env bash
set -euo pipefail

echo "=== MemoryVault: Bookmark tests ==="
echo ""

echo "--- Unit tests ---"
./gradlew test --tests "*TagServiceTest" --tests "*BookmarkServiceTest" --tests "*BookmarkToolsTest"

echo ""
echo "--- Integration tests ---"
./gradlew test --tests "*BookmarkIntegrationTest"

echo ""
echo "=== All bookmark tests passed ==="
