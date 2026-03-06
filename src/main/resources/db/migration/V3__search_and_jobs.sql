-- V3__search_and_jobs.sql
-- Job history tracking + full-text search infrastructure

-- ============================================================
-- Sync Jobs (append-only job execution history)
-- ============================================================
CREATE TABLE sync_jobs (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id),
    type            VARCHAR(30)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    error_message   TEXT,
    triggered_by    VARCHAR(20)  NOT NULL,
    metadata        JSONB
);

CREATE INDEX idx_sync_jobs_user_type ON sync_jobs(user_id, type);
CREATE INDEX idx_sync_jobs_started_at ON sync_jobs(started_at DESC);

-- ============================================================
-- Full-Text Search Vectors
-- ============================================================

-- Bookmarks: search by title (weight A) and url (weight B)
ALTER TABLE bookmarks ADD COLUMN search_vector tsvector;

CREATE INDEX idx_bookmarks_search ON bookmarks USING GIN (search_vector);

CREATE OR REPLACE FUNCTION bookmarks_search_trigger() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.url, '')), 'B');
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bookmarks_search
    BEFORE INSERT OR UPDATE OF title, url ON bookmarks
    FOR EACH ROW EXECUTE FUNCTION bookmarks_search_trigger();

-- Feed items: search by title (A), content (B), author (C)
ALTER TABLE feed_items ADD COLUMN search_vector tsvector;

CREATE INDEX idx_feed_items_search ON feed_items USING GIN (search_vector);

CREATE OR REPLACE FUNCTION feed_items_search_trigger() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.content, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(NEW.author, '')), 'C');
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_feed_items_search
    BEFORE INSERT OR UPDATE OF title, content, author ON feed_items
    FOR EACH ROW EXECUTE FUNCTION feed_items_search_trigger();

-- Videos: search by title (A), channel_name (B), description (C)
ALTER TABLE videos ADD COLUMN search_vector tsvector;

CREATE INDEX idx_videos_search ON videos USING GIN (search_vector);

CREATE OR REPLACE FUNCTION videos_search_trigger() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.channel_name, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(NEW.description, '')), 'C');
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_videos_search
    BEFORE INSERT OR UPDATE OF title, channel_name, description ON videos
    FOR EACH ROW EXECUTE FUNCTION videos_search_trigger();

-- ============================================================
-- Backfill search vectors for existing rows
-- ============================================================
UPDATE bookmarks SET search_vector =
    setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(url, '')), 'B');

UPDATE feed_items SET search_vector =
    setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(content, '')), 'B') ||
    setweight(to_tsvector('english', coalesce(author, '')), 'C');

UPDATE videos SET search_vector =
    setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(channel_name, '')), 'B') ||
    setweight(to_tsvector('english', coalesce(description, '')), 'C');
