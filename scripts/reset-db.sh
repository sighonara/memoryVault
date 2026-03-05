#!/usr/bin/env bash
set -euo pipefail

echo "WARNING: This will drop and recreate the memoryvault database."
read -p "Are you sure? (yes/N): " confirm

if [ "$confirm" != "yes" ]; then
  echo "Aborted."
  exit 0
fi

echo "Resetting database..."
docker compose exec postgres psql -U memoryvault -c "DROP DATABASE IF EXISTS memoryvault;"
docker compose exec postgres psql -U memoryvault -c "CREATE DATABASE memoryvault;"
echo "Database reset. Flyway will re-run migrations on next app start."
