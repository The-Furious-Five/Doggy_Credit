-- E-16: elegibilidad disponible en todos los planes, capturada una sola vez.
-- Una fila incluso para [] distingue un catálogo vacío de evaluaciones históricas sin snapshot.
CREATE TABLE assessment.eligibility_snapshots (
    id            UUID        PRIMARY KEY,
    tenant_id     UUID        NOT NULL,
    assessment_id UUID        NOT NULL,
    products      JSONB       NOT NULL CHECK (jsonb_typeof(products) = 'array'),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       BIGINT      NOT NULL DEFAULT 0,
    UNIQUE (tenant_id, assessment_id)
);

ALTER TABLE assessment.eligibility_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE assessment.eligibility_snapshots FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON assessment.eligibility_snapshots
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
REVOKE ALL ON assessment.eligibility_snapshots FROM PUBLIC;
GRANT SELECT, INSERT ON assessment.eligibility_snapshots TO doggycredit_app;

-- Compatibilidad: filas de C-06 conservan null; no se inventan plazos ni razones históricas.
ALTER TABLE assessment.assessment_recommendations
    ADD COLUMN suggested_term_months INTEGER CHECK (suggested_term_months BETWEEN 1 AND 360),
    ADD COLUMN reasons TEXT[] CHECK (cardinality(reasons) >= 2),
    ADD CONSTRAINT assessment_recommendations_details_ck
        CHECK ((suggested_term_months IS NULL) = (reasons IS NULL));
