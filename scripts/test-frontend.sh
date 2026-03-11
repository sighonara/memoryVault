#!/bin/bash
set -e

echo "=== Frontend Tests ==="

echo ""
echo "--- Unit Tests (Vitest) ---"
cd "$(dirname "$0")/../client"
npm test

echo ""
echo "=== Frontend tests complete ==="
