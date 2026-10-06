-- B-04: snapshots del Perfil Financiero 360 v1. Inmutables (solo INSERT): cada
-- consolidación agrega uno y la vista lee el más reciente. Todo el contenido
-- financiero (canónico v1 + resumen de la vista) va cifrado con la DEK del
-- tenant en payload_ciphertext; en claro solo claves, hash y fecha.
CREATE TABLE customer.financial_profiles (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    applicant_id        uuid NOT NULL REFERENCES customer.applicants (id),
    content_sha256      varchar(64) NOT NULL,
    payload_ciphertext  text NOT NULL,
    created_at          timestamp(6) with time zone NOT NULL
);

CREATE INDEX financial_profiles_latest_idx
    ON customer.financial_profiles (tenant_id, applicant_id, created_at DESC);

-- SPIKE-1: misma política que el resto de tablas con tenant_id.
ALTER TABLE customer.financial_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE customer.financial_profiles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON customer.financial_profiles
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT ON customer.financial_profiles TO doggycredit_app;
