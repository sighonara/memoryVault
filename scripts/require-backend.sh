#!/usr/bin/env bash
# Checks that the backend is running. If not, prompts the user to start it.
# Usage: source this script, or call it directly before tests that need the backend.

BACKEND_URL="${BACKEND_URL:-http://localhost:8085/actuator/health}"

if curl -sf "$BACKEND_URL" > /dev/null 2>&1; then
  return 0 2>/dev/null || exit 0
fi

echo ""
echo "⚠  Backend is not running at $BACKEND_URL"
echo "   Start it with: ./gradlew bootRun"
echo ""
read -rp "Press Enter when the backend is ready (or Ctrl+C to skip)... "

if ! curl -sf "$BACKEND_URL" > /dev/null 2>&1; then
  echo "ERROR: Backend still not reachable at $BACKEND_URL"
  exit 1
fi
