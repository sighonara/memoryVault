#!/bin/bash
set -e

echo "=== YouTube Archival Tests ==="

echo ""
echo "--- Unit: YtDlpService ---"
./gradlew test --tests "*YtDlpServiceTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Unit: VideoSyncService ---"
./gradlew test --tests "*VideoSyncServiceTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Unit: YoutubeListService ---"
./gradlew test --tests "*YoutubeListServiceTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Unit: VideoService ---"
./gradlew test --tests "*VideoServiceTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Unit: YoutubeTools (MCP) ---"
./gradlew test --tests "*YoutubeToolsTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Unit: LocalStorageService ---"
./gradlew test --tests "*LocalStorageServiceTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Integration: YoutubeIntegrationTest ---"
./gradlew test --tests "*YoutubeIntegrationTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "=== All YouTube tests complete ==="
