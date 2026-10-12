-- V2026_10_12_1000__tenancy_add_downgrade_grace.sql
-- LOS-116: Bajar de plan con 30 dias de gracia
--
-- La bajada que pide el tenant se programa para el cierre del período (bajada_*)
-- y, al aplicarse, abre la gracia (plan_anterior, gracia_*). Nada de esto borra datos.

ALTER TABLE tenancy.tenants
  ADD COLUMN plan_anterior VARCHAR(20),
  ADD COLUMN gracia_desde TIMESTAMPTZ,
  ADD COLUMN gracia_hasta TIMESTAMPTZ,
  ADD COLUMN bajada_plan_destino VARCHAR(20),
  ADD COLUMN bajada_efectiva_en TIMESTAMPTZ,
  ADD COLUMN bajada_solicitada_por VARCHAR(255),
  ADD COLUMN bajada_solicitada_en TIMESTAMPTZ;

COMMENT ON COLUMN tenancy.tenants.plan_anterior IS 'Plan antes de la última bajada (null si nunca bajó o si volvió a subir)';
COMMENT ON COLUMN tenancy.tenants.gracia_desde IS 'Instante en que se aplicó la bajada y empezó la gracia';
COMMENT ON COLUMN tenancy.tenants.gracia_hasta IS 'Fecha límite del período de gracia del downgrade; antes no se purga nada';
COMMENT ON COLUMN tenancy.tenants.bajada_plan_destino IS 'Plan al que bajará el tenant al cierre del período (null si no hay bajada programada)';
COMMENT ON COLUMN tenancy.tenants.bajada_efectiva_en IS 'Cierre del período (mes UTC) en que se aplica la bajada programada';

-- El job que aplica las bajadas vencidas busca por esta columna cada minuto.
CREATE INDEX tenants_bajada_efectiva_en_idx ON tenancy.tenants (bajada_efectiva_en)
  WHERE bajada_efectiva_en IS NOT NULL;

-- La app solo puede escribir columnas concretas de tenancy.tenants (V2026_10_04_2200).
GRANT UPDATE (plan_anterior, gracia_desde, gracia_hasta, bajada_plan_destino, bajada_efectiva_en,
              bajada_solicitada_por, bajada_solicitada_en)
  ON tenancy.tenants TO doggycredit_app;
