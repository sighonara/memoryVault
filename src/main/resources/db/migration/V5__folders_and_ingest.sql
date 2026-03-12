-- Folders table
CREATE TABLE folders (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    parent_id   UUID REFERENCES folders(id),
    user_id     UUID NOT NULL REFERENCES users(id),
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_folders_user_id ON folders(user_id);
CREATE INDEX idx_folders_parent_id ON folders(parent_id);

-- Full-text search on folders
ALTER TABLE folders ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (to_tsvector('english', coalesce(name, ''))) STORED;
CREATE INDEX idx_folders_search ON folders USING GIN(search_vector);

-- Bookmark changes
ALTER TABLE bookmarks ADD COLUMN folder_id UUID REFERENCES folders(id);
ALTER TABLE bookmarks ADD COLUMN sort_order INT NOT NULL DEFAULT 0;
ALTER TABLE bookmarks ADD COLUMN normalized_url VARCHAR(2048);

CREATE INDEX idx_bookmarks_folder_id ON bookmarks(folder_id);
CREATE INDEX idx_bookmarks_normalized_url ON bookmarks(normalized_url);

-- Backfill normalized_url for existing bookmarks
-- Note: This is an approximate backfill (lowercase + strip trailing slash only).
-- Full normalization (www. stripping, query param sorting) happens in IngestService.normalizeUrl().
-- A one-time Kotlin migration script should be run after deployment to fully normalize existing URLs.
UPDATE bookmarks SET normalized_url = lower(
    regexp_replace(
        regexp_replace(url, '^(https?://)www\.', '\1'),
        '/$', ''
    )
) WHERE normalized_url IS NULL;

-- Ingest previews table (stores preview state between CLI POST and UI commit)
CREATE TABLE ingest_previews (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id),
    preview_data JSONB NOT NULL,
    committed   BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '1 hour'),
    version     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_ingest_previews_user_id ON ingest_previews(user_id);
