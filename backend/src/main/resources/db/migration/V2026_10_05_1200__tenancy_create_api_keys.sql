-- D-09 (LOS-98): claves de API del tenant. Cada clave es un cliente
-- client_credentials de Keycloak; aquí se guarda solo lo necesario para
-- listarla y revocarla. El secreto nunca se persiste: solo sus últimos 4
-- caracteres, para reconocerla en la consola.
CREATE TABLE tenancy.api_keys (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    nombre          varchar(80) NOT NULL,
    client_id       varchar(64) NOT NULL UNIQUE,
    keycloak_id     uuid NOT NULL,
    entorno         varchar(16) NOT NULL CHECK (entorno IN ('SANDBOX', 'PRODUCCION')),
    sufijo_secreto  varchar(4) NOT NULL,
    creada_por      varchar(255) NOT NULL,
    creada_en       timestamp(6) with time zone NOT NULL,
    revocada_en     timestamp(6) with time zone
);

CREATE INDEX api_keys_tenant_vigentes_idx ON tenancy.api_keys (tenant_id, creada_en DESC)
    WHERE revocada_en IS NULL;

-- SPIKE-1: misma política que consent.consents (ver V2026_09_29_1400).
ALTER TABLE tenancy.api_keys ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenancy.api_keys FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tenancy.api_keys
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE ON tenancy.api_keys TO doggycredit_app;
