-- V2__full_schema.sql
-- Full data model: users, tags, bookmarks, feeds, feed_items, youtube_lists, videos, and all join tables.

-- ============================================================
-- Users
-- ============================================================
CREATE TABLE users (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email        VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    role         VARCHAR(20)  NOT NULL DEFAULT 'OWNER',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ,
    version      BIGINT       NOT NULL DEFAULT 0
);

-- Seed system user for Phase 1 (no auth yet)
INSERT INTO users (id, email, password_hash, display_name, role)
VALUES ('00000000-0000-0000-0000-000000000001', 'system@memoryvault.local', 'nologin', 'System', 'OWNER');

-- ============================================================
-- Tags (shared across bookmarks, feed items, videos)
-- ============================================================
CREATE TABLE tags (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id),
    name       VARCHAR(100) NOT NULL,
    color      VARCHAR(7),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, name)
);

-- ============================================================
-- Bookmarks
-- ============================================================
CREATE TABLE bookmarks (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id),
    url        VARCHAR(2048) NOT NULL,
    title      VARCHAR(500)  NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    version    BIGINT        NOT NULL DEFAULT 0
);

CREATE TABLE bookmark_tags (
    bookmark_id UUID NOT NULL REFERENCES bookmarks(id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (bookmark_id, tag_id)
);

-- ============================================================
-- Feeds
-- ============================================================
CREATE TABLE feeds (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID         NOT NULL REFERENCES users(id),
    url                    VARCHAR(2048) NOT NULL,
    title                  VARCHAR(500),
    description            TEXT,
    site_url               VARCHAR(2048),
    last_fetched_at        TIMESTAMPTZ,
    fetch_interval_minutes INT          NOT NULL DEFAULT 60,
    failure_count          INT          NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ,
    version                BIGINT       NOT NULL DEFAULT 0
);

-- ============================================================
-- Feed Items
-- ============================================================
CREATE TABLE feed_items (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    feed_id      UUID        NOT NULL REFERENCES feeds(id) ON DELETE CASCADE,
    guid         VARCHAR(2048) NOT NULL,
    title        VARCHAR(500),
    url          VARCHAR(2048),
    content      TEXT,
    author       VARCHAR(255),
    image_url    VARCHAR(2048),
    published_at TIMESTAMPTZ,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (feed_id, guid)
);

CREATE TABLE feed_item_tags (
    feed_item_id UUID NOT NULL REFERENCES feed_items(id) ON DELETE CASCADE,
    tag_id       UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (feed_item_id, tag_id)
);

-- ============================================================
-- YouTube Lists
-- ============================================================
CREATE TABLE youtube_lists (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id),
    youtube_list_id VARCHAR(255) NOT NULL,
    url             VARCHAR(2048) NOT NULL,
    name            VARCHAR(500),
    description     TEXT,
    last_synced_at  TIMESTAMPTZ,
    failure_count   INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0
);

-- ============================================================
-- Videos
-- ============================================================
CREATE TABLE videos (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    youtube_list_id       UUID         NOT NULL REFERENCES youtube_lists(id) ON DELETE CASCADE,
    youtube_video_id      VARCHAR(255) NOT NULL,
    title                 VARCHAR(500),
    description           TEXT,
    channel_name          VARCHAR(255),
    thumbnail_path        VARCHAR(1024),
    youtube_url           VARCHAR(2048) NOT NULL,
    file_path             VARCHAR(1024),
    downloaded_at         TIMESTAMPTZ,
    duration_seconds      INT,
    removed_from_youtube  BOOLEAN      NOT NULL DEFAULT false,
    removed_detected_at   TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE video_tags (
    video_id UUID NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    tag_id   UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (video_id, tag_id)
);

-- ============================================================
-- Indexes
-- ============================================================
CREATE INDEX idx_bookmarks_user_id ON bookmarks(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_tags_user_id ON tags(user_id);
CREATE INDEX idx_feeds_user_id ON feeds(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_feed_items_feed_id ON feed_items(feed_id);
CREATE INDEX idx_videos_youtube_list_id ON videos(youtube_list_id);
