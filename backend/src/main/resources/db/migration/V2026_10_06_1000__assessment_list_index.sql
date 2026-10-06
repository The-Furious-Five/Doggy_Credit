-- C-09: el listado de evaluaciones ordena por fecha dentro del tenant.
-- Sin este índice, cada página recorrería todas las evaluaciones del tenant.
CREATE INDEX assessments_tenant_created_idx
    ON assessment.credit_assessments (tenant_id, created_at DESC, id);
