CREATE TABLE cost_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    billing_date    DATE NOT NULL UNIQUE,
    service_costs   JSONB NOT NULL DEFAULT '{}',
    total_cost_usd  DECIMAL(10,4) NOT NULL DEFAULT 0,
    fetched_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_cost_records_billing_date ON cost_records(billing_date DESC);
