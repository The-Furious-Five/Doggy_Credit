-- E-02: snapshot inmutable de las dimensiones que necesita analítica.
-- Los registros previos quedan en NULL: no reconstruimos una recomendación
-- histórica con productos o reglas que pudieron cambiar.
ALTER TABLE assessment.credit_assessments
    ADD COLUMN need_category varchar(32)
        CHECK (need_category IN ('CAPITAL_TRABAJO', 'INVERSION_PRODUCTIVA', 'EFICIENCIA_ENERGETICA', 'CONSUMO_CONSOLIDACION')),
    ADD COLUMN recommended_product_type varchar(16)
        CHECK (recommended_product_type IN ('MICROCREDITO', 'VERDE', 'PYME')),
    ADD COLUMN recommended_amount numeric(19,2),
    ADD COLUMN decision_time_ms bigint CHECK (decision_time_ms >= 0),
    ADD CONSTRAINT assessment_recommendation_ck CHECK (
        (recommended_product_type IS NULL AND recommended_amount IS NULL)
        OR (recommended_product_type IS NOT NULL AND recommended_amount IS NOT NULL AND recommended_amount > 0));

CREATE INDEX assessments_tenant_created_idx ON assessment.credit_assessments (tenant_id, created_at);

-- Vistas publicadas por los módulos dueños. security_invoker conserva RLS
-- incluso con una consulta sin WHERE tenant_id; además el consumidor filtra
-- explícitamente el tenant autenticado y el intervalo.
CREATE VIEW assessment.analytics_evaluations_v1 WITH (security_invoker = true) AS
    SELECT tenant_id, created_at, status, score, need_category,
           recommended_product_type, recommended_amount, decision_time_ms
    FROM assessment.credit_assessments;

-- El buró obligatorio está activo aun si no hay configuración guardada.
CREATE VIEW integration.analytics_active_v1 WITH (security_invoker = true) AS
    SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid AS tenant_id,
           1 + count(*) FILTER (WHERE active) AS active_count
    FROM integration.tenant_integrations
    WHERE tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid
    HAVING NULLIF(current_setting('app.tenant_id', true), '') IS NOT NULL;

GRANT SELECT ON assessment.analytics_evaluations_v1, integration.analytics_active_v1 TO doggycredit_app;
