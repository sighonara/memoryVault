-- Feed categories (single-level, no hierarchy)
CREATE TABLE feed_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    version INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_feed_categories_user_name ON feed_categories(user_id, name);

-- Seed "Subscribed" category for existing users
INSERT INTO feed_categories (id, user_id, name, sort_order)
SELECT gen_random_uuid(), id, 'Subscribed', 0 FROM users;

-- Add category_id to feeds (populate with user's "Subscribed" category)
ALTER TABLE feeds ADD COLUMN category_id UUID;
UPDATE feeds SET category_id = (
    SELECT fc.id FROM feed_categories fc
    WHERE fc.user_id = feeds.user_id AND fc.name = 'Subscribed'
);
ALTER TABLE feeds ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE feeds ADD CONSTRAINT fk_feeds_category FOREIGN KEY (category_id) REFERENCES feed_categories(id);

-- Starred articles stub
ALTER TABLE feed_items ADD COLUMN starred_at TIMESTAMPTZ;

-- User preferences
ALTER TABLE users ADD COLUMN view_mode VARCHAR(10) NOT NULL DEFAULT 'LIST';
ALTER TABLE users ADD COLUMN sort_order VARCHAR(20) NOT NULL DEFAULT 'NEWEST_FIRST';

-- API keys stub
CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    key_hash VARCHAR(255) NOT NULL,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    version INT NOT NULL DEFAULT 0
);

-- OAuth stub
CREATE TABLE user_auth_providers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    provider VARCHAR(50) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    access_token VARCHAR(1024),
    refresh_token VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    version INT NOT NULL DEFAULT 0,
    UNIQUE(provider, external_id)
);
