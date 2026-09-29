-- =============================================================================
-- V2026_09_29_1810__consent_rename_rls_policy.sql
-- Alinea el nombre de la política RLS de consent.consents con la convención
-- de la guía (capítulo 5): toda tabla con tenant_id usa la política
-- "tenant_isolation". Solo cambia el nombre; la regla (USING / WITH CHECK)
-- queda igual. Es una migración nueva porque V2026_09_28_1000 ya está en main
-- y editarla rompería el checksum de Flyway en las bases que ya la aplicaron.
-- =============================================================================

ALTER POLICY consents_tenant_isolation ON consent.consents RENAME TO tenant_isolation;
