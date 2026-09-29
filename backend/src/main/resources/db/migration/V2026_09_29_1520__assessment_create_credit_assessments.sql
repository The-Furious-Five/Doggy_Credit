-- HAB-04: resultado inmutable de la evaluación síncrona e idempotencia por tenant.
-- varchar y no char(n): ver V2026_09_29_1500.
CREATE TABLE assessment.credit_assessments (
    id                uuid PRIMARY KEY,
    tenant_id         uuid NOT NULL,
    applicant_id      uuid NOT NULL,
    consent_id        uuid NOT NULL,
    idempotency_key   varchar(128) NOT NULL,
    request_hash      varchar(64) NOT NULL,
    purpose           varchar(250),
    requested_amount  numeric(19, 2),
    term_months       integer,
    status            varchar(24) NOT NULL CHECK (status IN ('PENDIENTE', 'PARCIAL', 'PREAPROBADO', 'REVISION_MANUAL', 'FALLIDA')),
    score             integer NOT NULL CHECK (score BETWEEN 0 AND 1000),
    band              varchar(1) NOT NULL CHECK (band IN ('A', 'B', 'C', 'D', 'E')),
    reasons           text[] NOT NULL CHECK (cardinality(reasons) >= 3),
    model_version     varchar(40) NOT NULL,
    simulated         boolean NOT NULL DEFAULT true,
    correlation_id    varchar(128) NOT NULL,
    created_at        timestamp(6) with time zone NOT NULL,
    CONSTRAINT assessments_tenant_idempotency_uk UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX assessments_tenant_id_idx ON assessment.credit_assessments (tenant_id, id);

-- SPIKE-1: misma política que consent.consents (ver V2026_09_29_1400).
ALTER TABLE assessment.credit_assessments ENABLE ROW LEVEL SECURITY;
ALTER TABLE assessment.credit_assessments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON assessment.credit_assessments
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Sin UPDATE/DELETE: el resultado de una evaluación es inmutable.
GRANT USAGE ON SCHEMA assessment TO doggycredit_app;
GRANT SELECT, INSERT ON assessment.credit_assessments TO doggycredit_app;
