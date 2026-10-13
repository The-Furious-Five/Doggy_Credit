-- C-10 (LOS-111), RF-DEC-03: bandeja de revisión manual. Una persona resuelve cada caso que
-- las políticas dejaron en REVISION_MANUAL, SOLICITAR_DATOS o PARCIAL, con justificación
-- obligatoria (override). La evaluación no se modifica (la app no tiene UPDATE sobre
-- credit_assessments): la resolución es un registro aparte, también inmutable.
CREATE TABLE assessment.manual_review_resolutions (
    id             UUID          PRIMARY KEY,
    tenant_id      UUID          NOT NULL,
    assessment_id  UUID          NOT NULL REFERENCES assessment.credit_assessments (id),
    decision       VARCHAR(16)   NOT NULL CHECK (decision IN ('APROBADO', 'RECHAZADO')),
    -- Producto del catálogo del tenant (módulo recommendation): sin FK entre módulos.
    product_id     UUID,
    justification  VARCHAR(1000) NOT NULL CHECK (char_length(btrim(justification)) >= 10),
    resolved_by    VARCHAR(255)  NOT NULL,
    resolved_at    TIMESTAMPTZ   NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version        BIGINT        NOT NULL DEFAULT 0,
    -- Un caso se resuelve una sola vez: si dos personas lo resuelven a la vez, la base decide
    -- y la segunda recibe 409 CONCURRENT_UPDATE.
    CONSTRAINT manual_review_resolutions_assessment_uk UNIQUE (tenant_id, assessment_id),
    -- Aprobar exige un producto; rechazar no lleva producto.
    CONSTRAINT manual_review_resolutions_product_ck CHECK ((decision = 'APROBADO') = (product_id IS NOT NULL))
);

ALTER TABLE assessment.manual_review_resolutions ENABLE ROW LEVEL SECURITY;
ALTER TABLE assessment.manual_review_resolutions FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON assessment.manual_review_resolutions
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

REVOKE ALL ON assessment.manual_review_resolutions FROM PUBLIC;
-- Inmutable, como la evaluación: sin UPDATE ni DELETE.
GRANT SELECT, INSERT ON assessment.manual_review_resolutions TO doggycredit_app;

-- La bandeja lista los casos pendientes del tenant, del más antiguo al más reciente.
CREATE INDEX credit_assessments_review_idx
    ON assessment.credit_assessments (tenant_id, created_at, id)
    WHERE status IN ('REVISION_MANUAL', 'SOLICITAR_DATOS', 'PARCIAL');
