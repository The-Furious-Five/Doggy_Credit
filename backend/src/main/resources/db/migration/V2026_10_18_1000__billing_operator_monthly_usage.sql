-- E-14 / ADR-019: lectura mínima entre tenants, sin desactivar ni eludir RLS.
-- El dueño de la función no es dueño de la tabla, no inicia sesión y no puede escribir.
CREATE ROLE doggycredit_usage_reader NOLOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;

GRANT USAGE ON SCHEMA billing TO doggycredit_usage_reader;
GRANT SELECT (tenant_id, year_month, evaluaciones) ON billing.monthly_usage TO doggycredit_usage_reader;

-- FORCE ROW LEVEL SECURITY y tenant_isolation siguen vigentes para la aplicación.
-- Solo este rol sin miembros recibe una política adicional de SELECT.
CREATE POLICY operator_usage_read ON billing.monthly_usage
    FOR SELECT TO doggycredit_usage_reader USING (true);

CREATE FUNCTION billing.operator_monthly_usage(p_month TEXT)
    RETURNS TABLE (tenant_id UUID, evaluaciones INTEGER)
    LANGUAGE sql
    STABLE
    SECURITY DEFINER
    SET search_path = pg_catalog
AS $$
    SELECT u.tenant_id, u.evaluaciones
      FROM billing.monthly_usage u
     WHERE u.year_month = p_month
$$;

REVOKE ALL ON FUNCTION billing.operator_monthly_usage(TEXT) FROM PUBLIC;
GRANT CREATE ON SCHEMA billing TO doggycredit_usage_reader;
ALTER FUNCTION billing.operator_monthly_usage(TEXT) OWNER TO doggycredit_usage_reader;
REVOKE CREATE ON SCHEMA billing FROM doggycredit_usage_reader;
GRANT EXECUTE ON FUNCTION billing.operator_monthly_usage(TEXT) TO doggycredit_app;
