-- =============================================================================
-- V2026_09_29_1400__consent_enable_row_level_security.sql
-- SPIKE-1: Row-Level Security sobre consent.consents. Segunda capa de
-- aislamiento, independiente del discriminador @TenantId de Hibernate
-- (com.doggycredit.shared.internal.TenantAwareDataSource fija la variable
-- de sesión app.tenant_id en cada conexión; este archivo es lo que la usa).
--
-- FORCE (no solo ENABLE) es imprescindible: doggy_app es el dueño de la
-- tabla (la crea Flyway con ese mismo rol), y PostgreSQL exime al dueño de
-- sus propias políticas de RLS salvo que se fuerce explícitamente.
--
-- Fallo cerrado: RESET app.tenant_id dentro de una sesión (lo que hace
-- TenantAwareDataSource sin tenant en contexto) no deja current_setting en
-- NULL, sino en '' — así se comporta un GUC de marcador de posición
-- (namespaced, nunca declarado en postgresql.conf) al resetearse, a
-- diferencia de un parámetro real con valor de arranque. Un ''::uuid
-- lanzaría un error de formato en vez de fallar limpiamente por RLS, así
-- que NULLIF convierte '' en NULL antes del cast, y `tenant_id = NULL`
-- nunca es verdadero. El cast va del lado del setting, no de la columna:
-- castear tenant_id impediría usar consents_tenant_documento_idx y cada
-- consulta filtrada por RLS sería un escaneo completo de la tabla.
-- Un SELECT sin tenant fijado devuelve cero filas; un INSERT sin tenant
-- fijado viola la política y falla, tal como pide la Definition of Done.
-- =============================================================================

ALTER TABLE consent.consents ENABLE ROW LEVEL SECURITY;
ALTER TABLE consent.consents FORCE ROW LEVEL SECURITY;

CREATE POLICY consents_tenant_isolation ON consent.consents
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
