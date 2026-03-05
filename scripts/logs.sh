#!/usr/bin/env bash
# Usage: ./scripts/logs.sh [level] [lines]
# Example: ./scripts/logs.sh ERROR 50

LEVEL="${1:-INFO}"
LINES="${2:-100}"
LOG_FILE="logs/memoryvault.log"

if [ -f "$LOG_FILE" ]; then
  grep "\"level\":\"$LEVEL\"" "$LOG_FILE" | tail -n "$LINES" | python3 -m json.tool 2>/dev/null || tail -n "$LINES" "$LOG_FILE"
else
  echo "No log file found at $LOG_FILE. Is the app running?"
  echo "To view live logs: ./gradlew bootRun"
fi
