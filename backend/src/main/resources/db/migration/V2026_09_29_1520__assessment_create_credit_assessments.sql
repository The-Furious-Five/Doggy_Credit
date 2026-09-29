-- HAB-04: resultado inmutable de la evaluación síncrona e idempotencia por tenant.
CREATE TABLE assessment.credit_assessments (
    id                uuid PRIMARY KEY,
    tenant_id         uuid NOT NULL,
    applicant_id      uuid NOT NULL,
    consent_id        uuid NOT NULL,
    idempotency_key   varchar(128) NOT NULL,
    request_hash      char(64) NOT NULL,
    purpose           varchar(250),
    requested_amount  numeric(19, 2),
    term_months       integer,
    status            varchar(24) NOT NULL CHECK (status IN ('PENDIENTE', 'PARCIAL', 'PREAPROBADO', 'REVISION_MANUAL', 'FALLIDA')),
    score             integer NOT NULL CHECK (score BETWEEN 0 AND 1000),
    band              char(1) NOT NULL CHECK (band IN ('A', 'B', 'C', 'D', 'E')),
    reasons           text[] NOT NULL CHECK (cardinality(reasons) >= 3),
    model_version     varchar(40) NOT NULL,
    simulated         boolean NOT NULL DEFAULT true,
    correlation_id    varchar(128) NOT NULL,
    created_at        timestamp(6) with time zone NOT NULL,
    CONSTRAINT assessments_tenant_idempotency_uk UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX assessments_tenant_id_idx ON assessment.credit_assessments (tenant_id, id);
