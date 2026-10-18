-- C-06: recomendaciones[] de cada evaluación (solo planes con Feature.RECOMENDACIONES).
-- Se guardan al evaluar para que GET /credit-assessments/{id} y el perfil del cliente
-- muestren lo mismo que vio el analista, sin recalcular con un catálogo que pudo cambiar.
CREATE TABLE assessment.assessment_recommendations (
    id                UUID          PRIMARY KEY,
    tenant_id         UUID          NOT NULL,
    assessment_id     UUID          NOT NULL,
    position          INTEGER       NOT NULL CHECK (position >= 1),
    product_type      VARCHAR(16)   NOT NULL CHECK (product_type IN ('MICROCREDITO', 'VERDE', 'PYME')),
    currency          VARCHAR(3)    NOT NULL CHECK (currency IN ('BOB', 'USD')),
    suggested_amount  NUMERIC(19,2) NOT NULL CHECK (suggested_amount > 0),
    reason            VARCHAR(250)  NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT assessment_recommendations_position_uk UNIQUE (tenant_id, assessment_id, position)
);

ALTER TABLE assessment.assessment_recommendations ENABLE ROW LEVEL SECURITY;
ALTER TABLE assessment.assessment_recommendations FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON assessment.assessment_recommendations
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

REVOKE ALL ON assessment.assessment_recommendations FROM PUBLIC;
-- Inmutables, como la evaluación: sin UPDATE ni DELETE.
GRANT SELECT, INSERT ON assessment.assessment_recommendations TO doggycredit_app;

-- true si se evaluaron recomendaciones (plan con la función y resultado completo), aunque
-- ningún producto aplique; false si no corresponde (FREE o PARCIAL): así la API distingue
-- «sin productos elegibles» ([]) de «no disponible» (campo ausente).
ALTER TABLE assessment.credit_assessments
    ADD COLUMN recommendations_evaluated BOOLEAN NOT NULL DEFAULT false;
