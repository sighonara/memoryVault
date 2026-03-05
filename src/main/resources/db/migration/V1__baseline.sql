-- V1__baseline.sql
-- Baseline schema: extensions and shared infrastructure only.
-- Domain tables are added in subsequent migrations per phase.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";  -- for gen_random_uuid()

-- Flyway tracks its own history in flyway_schema_history (created automatically).
-- This migration intentionally contains no domain tables.
-- Phase 1 (bookmarks) will add V2__bookmarks.sql, etc.
