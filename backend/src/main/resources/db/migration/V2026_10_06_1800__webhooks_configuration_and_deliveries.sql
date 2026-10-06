CREATE SCHEMA webhooks;

CREATE TABLE webhooks.subscriptions (
    tenant_id          uuid PRIMARY KEY REFERENCES tenancy.tenants(id),
    url                varchar(2048),
    events             jsonb NOT NULL,
    secret_ciphertext  text,
    updated_at         timestamp(6) with time zone NOT NULL,
    CHECK (url IS NULL OR secret_ciphertext IS NOT NULL)
);

CREATE TABLE webhooks.deliveries (
    tenant_id          uuid NOT NULL REFERENCES tenancy.tenants(id),
    event_id           uuid NOT NULL,
    event_type         varchar(80) NOT NULL,
    url                varchar(2048) NOT NULL,
    secret_ciphertext  text NOT NULL,
    payload_ciphertext text NOT NULL,
    status             varchar(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED', 'CANCELLED')),
    attempts           integer NOT NULL DEFAULT 0,
    next_attempt_at    timestamp(6) with time zone NOT NULL,
    last_http_status   integer,
    delivered_at      timestamp(6) with time zone,
    PRIMARY KEY (tenant_id, event_id)
);

CREATE INDEX deliveries_pending_idx ON webhooks.deliveries (tenant_id, next_attempt_at)
    WHERE status = 'PENDING';

ALTER TABLE webhooks.subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhooks.subscriptions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON webhooks.subscriptions
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
ALTER TABLE webhooks.deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhooks.deliveries FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON webhooks.deliveries
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT USAGE ON SCHEMA webhooks TO doggycredit_app;
GRANT SELECT, INSERT, UPDATE ON webhooks.subscriptions, webhooks.deliveries TO doggycredit_app;
