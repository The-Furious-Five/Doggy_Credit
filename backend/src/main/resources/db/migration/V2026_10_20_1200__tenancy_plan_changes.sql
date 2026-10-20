-- D-14 (RF-ANA-04): historial de cambios de plan, solo inserción.
--
-- audit.access_log registra PLAN_UPGRADED / PLAN_DOWNGRADED, pero no desde qué plan ni hacia cuál,
-- y las métricas del operador necesitan distinguir FREE → PRO de PRO → ENTERPRISE. PlanChangeService
-- inserta una fila por cada cambio efectivo, a nombre del tenant afectado (como su bitácora).
CREATE TABLE tenancy.plan_changes (
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL,
    plan_anterior  varchar(20) NOT NULL CHECK (plan_anterior IN ('FREE', 'PRO', 'ENTERPRISE')),
    plan_nuevo     varchar(20) NOT NULL CHECK (plan_nuevo IN ('FREE', 'PRO', 'ENTERPRISE')),
    cambiado_en    timestamp(6) with time zone NOT NULL,
    CONSTRAINT plan_changes_distinct_ck CHECK (plan_anterior <> plan_nuevo)
);

CREATE INDEX plan_changes_cambiado_en_idx ON tenancy.plan_changes (cambiado_en);
CREATE INDEX plan_changes_tenant_idx ON tenancy.plan_changes (tenant_id, cambiado_en);

-- DEBT-017: RLS y GRANT en la misma migración que crea la tabla.
ALTER TABLE tenancy.plan_changes ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenancy.plan_changes FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tenancy.plan_changes
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- Solo inserción: la aplicación no recibe UPDATE ni DELETE, y un trigger lo impide también con
-- cualquier otro rol, como en audit.access_log.
GRANT SELECT, INSERT ON tenancy.plan_changes TO doggycredit_app;

CREATE FUNCTION tenancy.plan_changes_forbid_write() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog
AS $$
BEGIN
    RAISE EXCEPTION 'tenancy.plan_changes es de solo inserción: % no está permitido', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER plan_changes_no_edit
    BEFORE UPDATE OR DELETE ON tenancy.plan_changes
    FOR EACH STATEMENT EXECUTE FUNCTION tenancy.plan_changes_forbid_write();
CREATE TRIGGER plan_changes_no_truncate
    BEFORE TRUNCATE ON tenancy.plan_changes
    FOR EACH STATEMENT EXECUTE FUNCTION tenancy.plan_changes_forbid_write();
