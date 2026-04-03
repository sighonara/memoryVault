#!/usr/bin/env bash
set -euo pipefail

# Start both backend and frontend for local development.
# Ctrl+C stops both processes.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
  echo ""
  echo "Shutting down..."
  # Kill entire process groups (negative PID) to catch gradle/node child processes
  [ -n "$BACKEND_PID" ] && kill -- -"$BACKEND_PID" 2>/dev/null || true
  [ -n "$FRONTEND_PID" ] && kill -- -"$FRONTEND_PID" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "=== Starting MemoryVault dev environment ==="
echo ""

# Backend (Spring Boot on port 8085)
echo "[backend] Starting Spring Boot..."
cd "$PROJECT_ROOT"
set -m  # enable job control so & creates new process groups
./gradlew bootRun 2>&1 | sed 's/^/[backend] /' &
BACKEND_PID=$!

# Frontend (Angular on port 4200, proxies to backend)
echo "[frontend] Starting Angular dev server..."
cd "$PROJECT_ROOT/client"
npm start 2>&1 | sed 's/^/[frontend] /' &
FRONTEND_PID=$!

echo ""
echo "Backend:  http://localhost:8085"
echo "Frontend: http://localhost:4200"
echo "Press Ctrl+C to stop both."
echo ""

wait
