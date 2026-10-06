-- D-08 (LOS-97): monitoreo de deriva del score (PSI) por tenant y modelo.

-- Vista publicada por el dueño de los datos (assessment) para analítica
-- (DEBT-008: analítica sobre vistas de la base transaccional). security_invoker
-- hace que la RLS de credit_assessments se aplique a quien consulta.
CREATE VIEW assessment.scores_v1 WITH (security_invoker = true) AS
    SELECT tenant_id, model_version, score, created_at
    FROM assessment.credit_assessments;

GRANT SELECT ON assessment.scores_v1 TO doggycredit_app;

-- Resultado semanal del job. Una fila por tenant, modelo y ventana.
CREATE TABLE analytics.model_drift (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    model_version   varchar(40) NOT NULL,
    window_start    timestamp(6) with time zone NOT NULL,
    window_end      timestamp(6) with time zone NOT NULL,
    psi             numeric(10, 6),
    actual_count    integer NOT NULL CHECK (actual_count >= 0),
    baseline_count  integer NOT NULL CHECK (baseline_count >= 0),
    status          varchar(16) NOT NULL CHECK (status IN ('ESTABLE', 'VIGILAR', 'ALERTA', 'INSUFICIENTE')),
    computed_at     timestamp(6) with time zone NOT NULL,
    CONSTRAINT model_drift_window_uk UNIQUE (tenant_id, model_version, window_end)
);

CREATE INDEX model_drift_tenant_latest_idx ON analytics.model_drift (tenant_id, model_version, window_end DESC);

-- SPIKE-1: misma política que consent.consents (ver V2026_09_29_1400).
ALTER TABLE analytics.model_drift ENABLE ROW LEVEL SECURITY;
ALTER TABLE analytics.model_drift FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON analytics.model_drift
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT USAGE ON SCHEMA analytics TO doggycredit_app;
GRANT SELECT, INSERT, UPDATE ON analytics.model_drift TO doggycredit_app;
