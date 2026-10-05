-- B-06: conectores activos por tenant. Sin fila, el estado es el por defecto
-- (solo el buró, obligatorio, activo); la app solo guarda banco y billetera.
CREATE TABLE integration.tenant_integrations (
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL,
    source      varchar(16) NOT NULL CHECK (source IN ('BANCO', 'WALLET')),
    active      boolean NOT NULL,
    updated_at  timestamp(6) with time zone NOT NULL,
    updated_by  varchar(255) NOT NULL,
    CONSTRAINT tenant_integrations_tenant_source_uk UNIQUE (tenant_id, source)
);

-- SPIKE-1: misma política que el resto de tablas con tenant_id.
ALTER TABLE integration.tenant_integrations ENABLE ROW LEVEL SECURITY;
ALTER TABLE integration.tenant_integrations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON integration.tenant_integrations
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT USAGE ON SCHEMA integration TO doggycredit_app;
GRANT SELECT, INSERT, UPDATE ON integration.tenant_integrations TO doggycredit_app;
