#!/usr/bin/env bash
set -euo pipefail

# Start both backend and frontend for local development.
# Ctrl+C stops both processes.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

BACKEND_PID=""
FRONTEND_PID=""
CLEANED_UP=""

# Signal entire process groups (negative PID) to catch gradle/node child processes
signal_groups() {
  local sig="$1"
  [ -n "$BACKEND_PID" ] && kill "-$sig" -- -"$BACKEND_PID" 2>/dev/null || true
  [ -n "$FRONTEND_PID" ] && kill "-$sig" -- -"$FRONTEND_PID" 2>/dev/null || true
}

groups_alive() {
  { [ -n "$BACKEND_PID" ] && kill -0 -- -"$BACKEND_PID" 2>/dev/null; } ||
    { [ -n "$FRONTEND_PID" ] && kill -0 -- -"$FRONTEND_PID" 2>/dev/null; }
}

cleanup() {
  [ -n "$CLEANED_UP" ] && return
  CLEANED_UP=1
  echo ""
  echo "Shutting down..."
  # A stopped process (state T) ignores SIGTERM until continued, so CONT first.
  signal_groups CONT
  signal_groups TERM
  # Give Spring Boot / ng serve up to 15s to exit cleanly, then force it.
  for _ in $(seq 1 15); do
    groups_alive || break
    sleep 1
  done
  if groups_alive; then
    echo "Processes did not exit in 15s — sending SIGKILL."
    signal_groups KILL
  fi
  echo "Stopped."
}
trap cleanup EXIT INT TERM

echo "=== Starting MemoryVault dev environment ==="
echo ""

set -m  # enable job control so & creates new process groups

# stdin from /dev/null on both jobs: a background job that reads the terminal
# gets SIGTTIN and freezes, which previously made Ctrl+C hang forever.

# Backend (Spring Boot on port 8085)
echo "[backend] Starting Spring Boot..."
cd "$PROJECT_ROOT"
./gradlew bootRun < /dev/null 2>&1 | sed 's/^/[backend] /' &
BACKEND_PID=$!

# Frontend (Angular on port 4200, proxies to backend)
echo "[frontend] Starting Angular dev server..."
cd "$PROJECT_ROOT/client"
npm start < /dev/null 2>&1 | sed 's/^/[frontend] /' &
FRONTEND_PID=$!

echo ""
echo "Backend:  http://localhost:8085"
echo "Frontend: http://localhost:4200"
echo "Press Ctrl+C to stop both."
echo ""

wait
