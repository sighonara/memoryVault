#!/usr/bin/env bash
set -euo pipefail

echo "=== MemoryVault: Feed tests ==="
echo ""

echo "--- Unit tests ---"
./gradlew test --tests "*RssFetchServiceTest" --tests "*FeedItemServiceTest" --tests "*FeedServiceTest" --tests "*FeedToolsTest"

echo ""
echo "--- Integration tests ---"
./gradlew test --tests "*FeedIntegrationTest"

echo ""
echo "=== All feed tests passed ==="
