-- LOS-71: persist the tenant-level masking preference under RLS.
CREATE TABLE tenancy.tenant_preferences (
    tenant_id uuid PRIMARY KEY REFERENCES tenancy.tenants(id),
    enmascarar_datos boolean NOT NULL DEFAULT true
);

ALTER TABLE tenancy.tenant_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenancy.tenant_preferences FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tenancy.tenant_preferences
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

GRANT SELECT, INSERT, UPDATE ON tenancy.tenant_preferences TO doggycredit_app;
GRANT UPDATE (nombre, plan, activo) ON tenancy.tenants TO doggycredit_app;
ALTER TABLE tenancy.tenants
    ADD CONSTRAINT tenants_plan_fk FOREIGN KEY (plan) REFERENCES billing.plans(id);
