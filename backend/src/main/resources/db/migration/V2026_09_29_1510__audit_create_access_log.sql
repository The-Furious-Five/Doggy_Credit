-- =============================================================================
-- V2026_09_29_1510__audit_create_access_log.sql
-- RF-SEG-03.1 (LOS-57, Jhon): bitácora de auditoría append-only (solo inserción).
--
-- Tres capas de defensa, de la más blanda a la más dura:
--   1. Permisos: el rol de la app (doggycredit_app, SPIKE-1) solo puede
--      INSERT y SELECT.
--   2. RLS: cada tenant solo ve e inserta sus propias filas.
--   3. Triggers: rechazan UPDATE, DELETE y TRUNCATE incluso para un rol
--      elevado (dueño o superusuario), que se salta las capas 1 y 2.
--
-- Integración (2026-09-29): versión de Jhon, renumerada para quedar después
-- de las migraciones ya aplicadas (la original, V2026_09_27_2330, Flyway la
-- rechazaba en bases que ya tenían V2026_09_28_*), con NULLIF para el fallo
-- cerrado limpio (ver V2026_09_29_1400), permisos para doggycredit_app en vez
-- de doggy_app (que en Compose es superusuario) y las columnas resource_type/
-- resource_id que usa AuditTrail (qué recurso se tocó, no solo la acción).
--
-- Sin updated_at ni version: una fila de auditoría nunca se modifica.
-- =============================================================================

CREATE TABLE audit.access_log (
    id             UUID        PRIMARY KEY,
    tenant_id      UUID        NOT NULL,
    actor          TEXT        NOT NULL,
    action         TEXT        NOT NULL,
    resource_type  TEXT        NOT NULL,
    resource_id    UUID,
    correlation_id TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX access_log_tenant_created_idx ON audit.access_log (tenant_id, created_at);

-- --- Capa 2: RLS con fallo cerrado ------------------------------------------
ALTER TABLE audit.access_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit.access_log FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON audit.access_log
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- --- Capa 3: triggers que bloquean cualquier modificación -------------------
-- search_path fijo: evita que alguien "reemplace" funciones del sistema
-- creando objetos con el mismo nombre en otro esquema.
CREATE FUNCTION audit.forbid_write() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = pg_catalog
AS $$
BEGIN
    RAISE EXCEPTION 'audit.access_log es de solo inserción: % no está permitido', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

-- FOR EACH STATEMENT: rechaza el intento aunque no toque ninguna fila.
CREATE TRIGGER audit_no_edit
    BEFORE UPDATE OR DELETE ON audit.access_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit.forbid_write();

-- TRUNCATE no dispara los triggers de DELETE: necesita el suyo propio.
CREATE TRIGGER audit_no_truncate
    BEFORE TRUNCATE ON audit.access_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit.forbid_write();

-- --- Capa 1: permisos del rol de la aplicación ------------------------------
REVOKE ALL ON audit.access_log FROM PUBLIC;
GRANT USAGE ON SCHEMA audit TO doggycredit_app;
GRANT INSERT, SELECT ON audit.access_log TO doggycredit_app;
