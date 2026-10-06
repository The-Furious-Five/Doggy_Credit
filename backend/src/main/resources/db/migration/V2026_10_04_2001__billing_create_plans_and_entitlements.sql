-- =============================================================================
-- V2026_10_04_2001__billing_create_plans_and_entitlements.sql
-- LOS-65: Planes y entitlements en BD
-- =============================================================================

-- Plan catalog (no tenant_id — shared across all tenants)
CREATE TABLE billing.plans (
    id              varchar(20) PRIMARY KEY,  -- FREE, PRO, ENTERPRISE
    nombre          varchar(60) NOT NULL,
    evaluaciones_mes int        NOT NULL,     -- monthly evaluation quota (0 = unlimited)
    max_usuarios    int         NOT NULL,     -- max team members
    max_integraciones int      NOT NULL,      -- max integrations
    scoring_avanzado boolean    NOT NULL DEFAULT false,
    webhooks        boolean    NOT NULL DEFAULT false,
    analytics       boolean    NOT NULL DEFAULT false,
    activo          boolean    NOT NULL DEFAULT true
);

-- Seed the three plans
INSERT INTO billing.plans (id, nombre, evaluaciones_mes, max_usuarios, max_integraciones, scoring_avanzado, webhooks, analytics) VALUES
    ('FREE',       'Gratis',      500, 3, 1, false, false, false),
    ('PRO',        'Pro',           0, 50, 999, true, true, true),
    ('ENTERPRISE', 'Enterprise',    0, 999, 999, true, true, true);

-- Monthly usage counter per tenant (has tenant_id → needs RLS)
CREATE TABLE billing.monthly_usage (
    tenant_id       uuid        NOT NULL,
    year_month      char(7)     NOT NULL,  -- '2026-10'
    evaluaciones    int         NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, year_month)
);

-- RLS for monthly_usage (follows DEBT-017 pattern)
ALTER TABLE billing.monthly_usage ENABLE ROW LEVEL SECURITY;
ALTER TABLE billing.monthly_usage FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON billing.monthly_usage
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- GRANTs for doggycredit_app (follows V2026_09_29_1401 pattern)
GRANT USAGE ON SCHEMA billing TO doggycredit_app;
GRANT SELECT ON billing.plans TO doggycredit_app;
GRANT SELECT, INSERT, UPDATE ON billing.monthly_usage TO doggycredit_app;
