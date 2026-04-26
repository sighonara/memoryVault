CREATE TABLE backup_providers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('INTERNET_ARCHIVE', 'CUSTOM')),
    name VARCHAR(100) NOT NULL,
    credentials_encrypted TEXT NOT NULL,
    config JSONB DEFAULT '{}',
    is_primary BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE backup_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    video_id UUID NOT NULL REFERENCES videos(id),
    provider_id UUID NOT NULL REFERENCES backup_providers(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'UPLOADING', 'BACKED_UP', 'LOST', 'FAILED')),
    external_url VARCHAR(2048),
    external_id VARCHAR(255),
    error_message TEXT,
    last_health_check_at TIMESTAMPTZ,
    health_check_failures INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_backup_records_video_id ON backup_records(video_id);
CREATE INDEX idx_backup_records_provider_id ON backup_records(provider_id);
CREATE INDEX idx_backup_records_status ON backup_records(status);
CREATE INDEX idx_backup_providers_user_id ON backup_providers(user_id);
