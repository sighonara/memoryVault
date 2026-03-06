#!/bin/bash
set -e

echo "=== Cross-Cutting Tests ==="

echo ""
echo "--- Unit: SearchService ---"
./gradlew test --tests "*SearchServiceTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Unit: StatsService ---"
./gradlew test --tests "*StatsServiceTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Unit: SyncJobService ---"
./gradlew test --tests "*SyncJobServiceTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Unit: LocalLogService ---"
./gradlew test --tests "*LocalLogServiceTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Unit: CrossCuttingTools (MCP) ---"
./gradlew test --tests "*CrossCuttingToolsTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "--- Integration: CrossCuttingIntegrationTest ---"
./gradlew test --tests "*CrossCuttingIntegrationTest" 2>&1 | grep -E "(PASS|FAIL|BUILD|tests)"

echo ""
echo "=== All cross-cutting tests complete ==="
