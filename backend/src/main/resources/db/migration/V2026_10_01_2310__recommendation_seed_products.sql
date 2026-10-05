-- =============================================================================
-- V2026_10_01_2310__recommendation_seed_products.sql
-- C-02 (LOS-82): semilla del catálogo, un producto de cada tipo para cada
-- tenant del registro maestro (tenancy.tenants, que a su vez sale de
-- seed/tenants.yaml: aquí no se escribe ningún tenant_id a mano).
--
-- Rangos de monto y plazo: los de la vista /app/productos (web/src/app/data.ts),
-- como pide el ticket. Banda mínima: tabla de productos del informe de
-- arquitectura (MICRO C, VERDE C, PYME B). PYME arranca inactivo, como en la vista.
--
-- SUPUESTO POR VALIDAR (tasas): ni el ticket ni la vista definen la tasa. Se
-- usan los topes de tasa para crédito productivo de RN-17 (D.S. 2055): micro
-- 11,5 %, pequeña empresa 7 %. El crédito verde toma la del microcrédito
-- (aplica sus reglas cuando el titular es persona). Son datos de demo
-- sintéticos (RN-20): si negocio define otras tasas, se corrigen con una
-- migración nueva, sin tocar esta.
--
-- Flyway corre con el rol administrador (superusuario), que no está sujeto a
-- RLS: por eso puede insertar para todos los tenants. ON CONFLICT hace la
-- semilla idempotente. Los id son UUIDv4 (gen_random_uuid): PostgreSQL 17 no
-- trae uuidv7(); los productos que cree la aplicación usarán UUIDv7.
-- =============================================================================

INSERT INTO recommendation.products
    (id, tenant_id, product_type, currency, min_amount, max_amount,
     min_term_months, max_term_months, annual_rate, min_band, active)
SELECT gen_random_uuid(), t.id, p.product_type, 'BOB', p.min_amount, p.max_amount,
       p.min_term_months, p.max_term_months, p.annual_rate, p.min_band, p.active
FROM tenancy.tenants t
CROSS JOIN (VALUES
    ('MICROCREDITO',  1000.00,  20000.00,  3, 24, 11.50, 'C', true),
    ('VERDE',        10000.00, 150000.00, 12, 60, 11.50, 'C', true),
    ('PYME',         50000.00, 700000.00,  6, 84,  7.00, 'B', false)
) AS p (product_type, min_amount, max_amount, min_term_months, max_term_months,
        annual_rate, min_band, active)
ON CONFLICT ON CONSTRAINT products_tenant_type_uk DO NOTHING;
