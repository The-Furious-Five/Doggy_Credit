-- =============================================================================
-- V2026_10_01_2300__recommendation_create_products.sql
-- C-02 (LOS-82): catálogo de productos de crédito por tenant (módulo
-- recommendation). Alimenta la vista /app/productos y, más adelante, el motor
-- de elegibilidad (C-03).
--
-- Un producto por tipo y tenant: MICROCREDITO, VERDE (crédito ecológico) y
-- PYME. Montos en la moneda del producto, plazos en meses, tasa anual en %.
-- min_band es la banda mínima del score para ser elegible (informe de
-- arquitectura, tabla de productos: MICRO C, VERDE C, PYME B).
--
-- La semilla va en una migración aparte (los rangos de la vista y las tasas).
-- El id (UUIDv7) lo genera la aplicación: PostgreSQL 17 no trae uuidv7().
-- =============================================================================

CREATE TABLE recommendation.products (
    id               UUID          PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    product_type     TEXT          NOT NULL CHECK (product_type IN ('MICROCREDITO', 'VERDE', 'PYME')),
    currency         VARCHAR(3)    NOT NULL DEFAULT 'BOB' CHECK (currency IN ('BOB', 'USD')),
    min_amount       NUMERIC(19,2) NOT NULL CHECK (min_amount > 0),
    max_amount       NUMERIC(19,2) NOT NULL,
    min_term_months  INTEGER       NOT NULL CHECK (min_term_months >= 1),
    max_term_months  INTEGER       NOT NULL,
    annual_rate      NUMERIC(5,2)  NOT NULL CHECK (annual_rate > 0 AND annual_rate < 100),
    min_band         VARCHAR(1)    NOT NULL CHECK (min_band IN ('A', 'B', 'C', 'D', 'E')),
    active           BOOLEAN       NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT products_amount_range_ck CHECK (max_amount >= min_amount),
    CONSTRAINT products_term_range_ck   CHECK (max_term_months >= min_term_months),
    -- Un producto de cada tipo por tenant; también hace idempotente la semilla.
    CONSTRAINT products_tenant_type_uk  UNIQUE (tenant_id, product_type)
);

-- --- RLS con fallo cerrado (capítulo 5 de la guía) ---------------------------
-- NULLIF: app.tenant_id puede quedar en '' en una conexión reusada del pool, y
-- ''::uuid lanzaría un error en vez de devolver 0 filas.
ALTER TABLE recommendation.products ENABLE ROW LEVEL SECURITY;
ALTER TABLE recommendation.products FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON recommendation.products
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- --- Permisos del rol restringido de la app (V2026_09_29_1401) --------------
-- SELECT para listar el catálogo y UPDATE para el interruptor "activo" de la
-- vista. Sin INSERT ni DELETE: el catálogo lo carga la semilla; si una
-- historia futura permite crear o borrar productos, se concede en su migración.
REVOKE ALL ON recommendation.products FROM PUBLIC;
GRANT USAGE ON SCHEMA recommendation TO doggycredit_app;
GRANT SELECT, UPDATE ON recommendation.products TO doggycredit_app;
