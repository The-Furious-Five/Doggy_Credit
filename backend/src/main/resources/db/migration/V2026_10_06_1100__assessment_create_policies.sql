-- C-05: reglas de decisión por tenant. Las reglas son fijas (el código de cada una
-- vive en Java); el tenant solo las activa o pausa. Ninguna rechaza: un rechazo
-- siempre pasa por una persona.
CREATE TABLE assessment.policies (
    id              UUID          PRIMARY KEY,
    tenant_id       UUID          NOT NULL,
    code            VARCHAR(40)   NOT NULL CHECK (code IN ('PREAPROBACION_SCORE', 'MONTO_ALTO',
                                                           'BANDA_BAJA', 'DATOS_INSUFICIENTES')),
    name            VARCHAR(80)   NOT NULL,
    condition_text  VARCHAR(120)  NOT NULL,
    effect          VARCHAR(24)   NOT NULL CHECK (effect IN ('PREAPROBADO', 'REVISION_MANUAL', 'SOLICITAR_DATOS')),
    active          BOOLEAN       NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         BIGINT        NOT NULL DEFAULT 0,
    -- Una regla de cada código por tenant; también hace idempotente la semilla.
    CONSTRAINT policies_tenant_code_uk UNIQUE (tenant_id, code)
);

ALTER TABLE assessment.policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE assessment.policies FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON assessment.policies
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

REVOKE ALL ON assessment.policies FROM PUBLIC;
-- Sin INSERT ni DELETE: las reglas las crea la semilla; la app solo las lee y activa/pausa.
GRANT SELECT, UPDATE ON assessment.policies TO doggycredit_app;

-- Semilla para cada tenant del registro. Flyway corre como administrador, que no
-- está sujeto a la RLS (igual que la semilla de productos, V2026_10_01_2310).
INSERT INTO assessment.policies (id, tenant_id, code, name, condition_text, effect, active)
SELECT gen_random_uuid(), t.id, p.code, p.name, p.condition_text, p.effect, p.active
FROM tenancy.tenants t
CROSS JOIN (VALUES
    ('PREAPROBACION_SCORE', 'Preaprobación por score',      'score ≥ 650 y resultado completo', 'PREAPROBADO',     true),
    ('MONTO_ALTO',          'Revisión por monto alto',      'monto > 200.000',                   'REVISION_MANUAL', true),
    ('BANDA_BAJA',          'Revisión de bandas bajas',     'banda D o E',                       'REVISION_MANUAL', true),
    ('DATOS_INSUFICIENTES', 'Datos insuficientes',          'fuentes que respondieron < 2',      'SOLICITAR_DATOS', false)
) AS p (code, name, condition_text, effect, active)
ON CONFLICT ON CONSTRAINT policies_tenant_code_uk DO NOTHING;

-- La evaluación puede quedar en SOLICITAR_DATOS (nunca en un rechazo).
ALTER TABLE assessment.credit_assessments DROP CONSTRAINT credit_assessments_status_check;
ALTER TABLE assessment.credit_assessments ADD CONSTRAINT credit_assessments_status_check
    CHECK (status IN ('PENDIENTE', 'PARCIAL', 'PREAPROBADO', 'REVISION_MANUAL', 'SOLICITAR_DATOS', 'FALLIDA'));
