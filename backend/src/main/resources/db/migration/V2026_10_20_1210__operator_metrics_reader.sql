-- D-14 (RF-ANA-04) / ADR-029: agregados entre tenants para el operador, con el mismo patrón que
-- ADR-019. RLS sigue habilitado y forzado en ambas tablas; la aplicación no recibe BYPASSRLS ni se
-- conecta con otro rol. Cada función es propiedad de un rol lector sin login, sin miembros y sin
-- permisos de escritura, y solo devuelve conteos: nunca datos de una evaluación ni de un titular.
CREATE ROLE doggycredit_metrics_reader NOLOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;

GRANT USAGE ON SCHEMA assessment, tenancy TO doggycredit_metrics_reader;
GRANT SELECT (tenant_id, created_at) ON assessment.credit_assessments TO doggycredit_metrics_reader;
GRANT SELECT (plan_anterior, plan_nuevo, cambiado_en) ON tenancy.plan_changes TO doggycredit_metrics_reader;

CREATE POLICY operator_metrics_read ON assessment.credit_assessments
    FOR SELECT TO doggycredit_metrics_reader USING (true);
CREATE POLICY operator_metrics_read ON tenancy.plan_changes
    FOR SELECT TO doggycredit_metrics_reader USING (true);

-- Evaluaciones por tenant en [p_desde, p_hasta). El id del tenant no sale de la API: la aplicación
-- lo usa para cruzarlo con el plan actual y contar tenants activados.
CREATE FUNCTION assessment.operator_evaluations(p_desde timestamptz, p_hasta timestamptz)
    RETURNS TABLE (tenant_id UUID, evaluaciones BIGINT)
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = pg_catalog
AS $$
    SELECT a.tenant_id, count(*)
      FROM assessment.credit_assessments a
     WHERE a.created_at >= p_desde AND a.created_at < p_hasta
     GROUP BY a.tenant_id
$$;

-- Cambios de plan en [p_desde, p_hasta), agregados por origen y destino: sin tenants.
CREATE FUNCTION tenancy.operator_plan_changes(p_desde timestamptz, p_hasta timestamptz)
    RETURNS TABLE (plan_anterior VARCHAR, plan_nuevo VARCHAR, cambios BIGINT)
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = pg_catalog
AS $$
    SELECT c.plan_anterior, c.plan_nuevo, count(*)
      FROM tenancy.plan_changes c
     WHERE c.cambiado_en >= p_desde AND c.cambiado_en < p_hasta
     GROUP BY c.plan_anterior, c.plan_nuevo
$$;

REVOKE ALL ON FUNCTION assessment.operator_evaluations(timestamptz, timestamptz) FROM PUBLIC;
REVOKE ALL ON FUNCTION tenancy.operator_plan_changes(timestamptz, timestamptz) FROM PUBLIC;

-- Para transferir la propiedad el rol necesita CREATE en el esquema solo durante la migración.
GRANT CREATE ON SCHEMA assessment, tenancy TO doggycredit_metrics_reader;
ALTER FUNCTION assessment.operator_evaluations(timestamptz, timestamptz) OWNER TO doggycredit_metrics_reader;
ALTER FUNCTION tenancy.operator_plan_changes(timestamptz, timestamptz) OWNER TO doggycredit_metrics_reader;
REVOKE CREATE ON SCHEMA assessment, tenancy FROM doggycredit_metrics_reader;

GRANT EXECUTE ON FUNCTION assessment.operator_evaluations(timestamptz, timestamptz) TO doggycredit_app;
GRANT EXECUTE ON FUNCTION tenancy.operator_plan_changes(timestamptz, timestamptz) TO doggycredit_app;
