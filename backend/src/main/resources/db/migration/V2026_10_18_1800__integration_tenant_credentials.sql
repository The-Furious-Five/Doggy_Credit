-- RF-INT-07 / E-15: claves por proveedor, cifradas con la DEK del tenant.
CREATE TABLE integration.tenant_credentials (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    provider varchar(16) NOT NULL CHECK (provider IN ('BURO', 'BANCO', 'BANCO_DOS', 'WALLET')),
    secret_ciphertext text NOT NULL,
    last_four varchar(4) NOT NULL CHECK (length(last_four) = 4),
    updated_at timestamp(6) with time zone NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, provider)
);
ALTER TABLE integration.tenant_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE integration.tenant_credentials FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON integration.tenant_credentials
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE, DELETE ON integration.tenant_credentials TO doggycredit_app;
