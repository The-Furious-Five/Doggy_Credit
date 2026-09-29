-- =============================================================================
-- V2026_09_29_1900__customer_create_applicant.sql
-- RF-PER-01.1 (LOS-58): alta de solicitante con datos declarados
-- (identificación, actividad, ingresos y destino del crédito).
--
-- El documento nunca se guarda en claro:
--   - document_ciphertext: cifrado con FieldEncryption (AES-256-GCM, IV
--     aleatorio). Sirve para recuperar el documento, pero no para buscarlo:
--     cifrar dos veces el mismo documento da dos textos distintos.
--   - document_ref: HMAC-SHA-256 (hex) del documento normalizado con
--     DocumentNumber.normalize(). Siempre da lo mismo para el mismo documento
--     y tenant, así que permite el UNIQUE sin guardar el documento en claro
--     ("blind index").
--
-- El id (UUIDv7) lo genera la aplicación: PostgreSQL 17 no trae uuidv7().
-- =============================================================================

CREATE TABLE customer.applicant (
    id                        UUID          PRIMARY KEY,
    tenant_id                 UUID          NOT NULL,
    document_ref              CHAR(64)      NOT NULL,
    document_ciphertext       TEXT          NOT NULL,
    activity                  VARCHAR(120)  NOT NULL,
    activity_seniority_months INTEGER       NOT NULL
        CHECK (activity_seniority_months BETWEEN 0 AND 1200),
    monthly_income            NUMERIC(19,2) NOT NULL CHECK (monthly_income > 0),
    income_currency           CHAR(3)       NOT NULL CHECK (income_currency IN ('BOB', 'USD')),
    credit_destination        VARCHAR(250)  NOT NULL,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                   BIGINT        NOT NULL DEFAULT 0,
    -- Criterio de salida: el mismo titular no se duplica dentro del tenant.
    CONSTRAINT applicant_tenant_document_uk UNIQUE (tenant_id, document_ref)
);

-- --- RLS con fallo cerrado (capítulo 5 de la guía) ---------------------------
-- NULLIF: en una conexión reusada del pool, app.tenant_id queda en '' tras la
-- transacción anterior, y ''::uuid lanzaría un error en vez de devolver 0 filas.
ALTER TABLE customer.applicant ENABLE ROW LEVEL SECURITY;
ALTER TABLE customer.applicant FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON customer.applicant
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- --- Permisos del rol de la aplicación --------------------------------------
-- Sin DELETE: en el Sprint 1 no hay baja de solicitantes. Si una historia
-- futura la necesita, se concede en su propia migración.
REVOKE ALL ON customer.applicant FROM PUBLIC;
GRANT USAGE ON SCHEMA customer TO doggy_app;
GRANT SELECT, INSERT, UPDATE ON customer.applicant TO doggy_app;
