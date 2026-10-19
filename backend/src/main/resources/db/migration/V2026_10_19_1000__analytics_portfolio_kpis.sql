-- C-14 (LOS-130), RF-ANA-02: KPIs de cartera para el objeto pro de GET /analytics/resumen.
-- Distribución de bandas, tasa de override y aceptación por producto.

-- 1. La banda se suma al final de la vista de E-02 (CREATE OR REPLACE solo admite columnas
--    nuevas al final). Se repite security_invoker para que RLS siga aplicándose a quien consulta.
CREATE OR REPLACE VIEW assessment.analytics_evaluations_v1 WITH (security_invoker = true) AS
    SELECT tenant_id, created_at, status, score, need_category,
           recommended_product_type, recommended_amount, decision_time_ms, band
    FROM assessment.credit_assessments;

-- 2. Snapshot del tipo del producto elegido al aprobar en la bandeja, igual que
--    recommended_product_type: el producto vive en el módulo recommendation y no se hace
--    JOIN entre esquemas. Las resoluciones anteriores quedan en null (no se reconstruyen con
--    el catálogo actual) y no cuentan como aceptadas.
ALTER TABLE assessment.manual_review_resolutions
    ADD COLUMN product_type VARCHAR(16)
        CHECK (product_type IN ('MICROCREDITO', 'VERDE', 'PYME')),
    ADD CONSTRAINT manual_review_resolutions_product_type_ck
        CHECK (product_type IS NULL OR decision = 'APROBADO');

-- 3. Resoluciones con los datos de su evaluación, dentro del mismo módulo (assessment).
--    El override se cuenta por la fecha de la evaluación, no por la de la resolución.
CREATE VIEW assessment.analytics_resolutions_v1 WITH (security_invoker = true) AS
    SELECT r.tenant_id, a.created_at AS assessment_created_at, a.status AS assessment_status,
           a.recommended_product_type, r.decision, r.product_type
    FROM assessment.manual_review_resolutions r
    JOIN assessment.credit_assessments a ON a.id = r.assessment_id AND a.tenant_id = r.tenant_id;

GRANT SELECT ON assessment.analytics_resolutions_v1 TO doggycredit_app;
