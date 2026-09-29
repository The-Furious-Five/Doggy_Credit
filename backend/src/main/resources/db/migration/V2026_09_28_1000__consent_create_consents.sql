-- =============================================================================
-- V2026_09_28_1000__consent_create_consents.sql
-- RF-CON-01.1: consentimientos del titular (módulo consent).
-- Fuentes, finalidad, vigencia y evidencia (quién, cuándo, SHA-256 del
-- payload canónico). EXPIRADO no se guarda: se evalúa al leer con expira_en.
-- =============================================================================

CREATE TABLE consent.consents (
    id                uuid                     PRIMARY KEY,
    tenant_id         uuid                     NOT NULL,
    titular_documento varchar(64)              NOT NULL,
    fuentes           text[]                   NOT NULL CHECK (cardinality(fuentes) > 0
                                                  AND fuentes <@ ARRAY['BURO', 'BANCO', 'WALLET']),
    finalidad         varchar(500)             NOT NULL,
    vigencia_dias     integer                  NOT NULL CHECK (vigencia_dias BETWEEN 1 AND 365),
    otorgado_en       timestamp(6) with time zone NOT NULL,
    expira_en         timestamp(6) with time zone NOT NULL,
    estado            varchar(16)              NOT NULL CHECK (estado IN ('ACTIVO', 'REVOCADO')),
    otorgado_por      varchar(255)             NOT NULL,
    payload_sha256    varchar(64)              NOT NULL
);

CREATE INDEX consents_tenant_documento_idx ON consent.consents (tenant_id, titular_documento);

-- SPIKE-1: la política se aplica también al propietario de la tabla. Sin
-- contexto transaccional app.tenant_id, USING/WITH CHECK evalúan a NULL (fail closed).
ALTER TABLE consent.consents ENABLE ROW LEVEL SECURITY;
ALTER TABLE consent.consents FORCE ROW LEVEL SECURITY;
CREATE POLICY consents_tenant_isolation ON consent.consents
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
