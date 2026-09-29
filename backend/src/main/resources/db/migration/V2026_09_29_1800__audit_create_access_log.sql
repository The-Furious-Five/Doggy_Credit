-- =============================================================================
-- V2026_09_29_1800__audit_create_access_log.sql
-- RF-SEG-03.1 (LOS-57): bitácora de auditoría append-only (solo inserción).
--
-- Tres capas de defensa, de la más blanda a la más dura:
--   1. Permisos: doggy_app solo puede INSERT y SELECT.
--   2. RLS: cada tenant solo ve e inserta sus propias filas (capítulo 5).
--   3. Triggers: rechazan UPDATE, DELETE y TRUNCATE incluso para un rol
--      elevado (dueño o superusuario), que se salta las capas 1 y 2.
--
-- resource_type/resource_id dicen QUÉ se tocó (p. ej. APPLICANT + su id);
-- resource_id es opcional porque hay acciones sin un recurso concreto.
--
-- Sin updated_at ni version: una fila de auditoría nunca se modifica, así que
-- esas columnas base no tendrían ningún uso. El id (UUIDv7) lo genera la
-- aplicación: PostgreSQL 17 todavía no trae uuidv7().
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

-- --- Capa 2: RLS con fallo cerrado ------------------------------------------
-- Si nadie fija app.tenant_id, current_setting(..., true) devuelve NULL, la
-- comparación da NULL y no se ve ni se inserta ninguna fila. NULLIF cubre el
-- caso del pool: tras una transacción con set_config(..., true) el valor queda
-- en '' (no NULL), y ''::uuid lanzaría un error en vez de devolver 0 filas.
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
GRANT USAGE ON SCHEMA audit TO doggy_app;
REVOKE UPDATE, DELETE, TRUNCATE ON audit.access_log FROM doggy_app;
GRANT INSERT, SELECT ON audit.access_log TO doggy_app;
