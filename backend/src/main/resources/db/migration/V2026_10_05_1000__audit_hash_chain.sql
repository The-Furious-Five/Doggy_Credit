-- =============================================================================
-- V2026_10_05_1000__audit_hash_chain.sql
-- C-04 (LOS-84): cadena de hash por tenant sobre audit.access_log (informe de
-- arquitectura §12): cada registro guarda el hash del anterior, así que alterar
-- o borrar una fila rompe la cadena y la verificación lo detecta. Se comunica
-- como "evidente ante manipulación", no como "inmutable" (el ancla WORM es
-- DEBT-009).
--
-- Todo el cálculo vive en la base de datos: un trigger encadena cada INSERT y
-- verify_chain() recalcula con la misma función. Una sola fórmula, y nadie puede
-- insertar sin encadenar, venga de la app o de otro lado.
-- =============================================================================

ALTER TABLE audit.access_log
    ADD COLUMN chain_seq BIGINT,
    ADD COLUMN prev_hash VARCHAR(64),
    ADD COLUMN row_hash  VARCHAR(64);

-- --- Hash de una fila ---------------------------------------------------------
-- Campos separados por chr(31) (separador de unidad) y la fecha en UTC con
-- microsegundos: el resultado no depende de la zona horaria de la sesión.
CREATE FUNCTION audit.compute_row_hash(p_prev_hash TEXT, p_id UUID, p_tenant_id UUID, p_chain_seq BIGINT,
        p_actor TEXT, p_action TEXT, p_resource_type TEXT, p_resource_id UUID, p_correlation_id TEXT,
        p_created_at TIMESTAMPTZ)
    RETURNS VARCHAR(64)
    LANGUAGE sql
    STABLE
    SET search_path = pg_catalog
AS $$
    SELECT encode(sha256(convert_to(concat_ws(chr(31),
        coalesce(p_prev_hash, ''), p_id::text, p_tenant_id::text, p_chain_seq::text,
        p_actor, p_action, p_resource_type, coalesce(p_resource_id::text, ''), p_correlation_id,
        to_char(p_created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')), 'UTF8')), 'hex')
$$;

-- --- Encadenar las filas que ya existían -------------------------------------
-- El trigger audit_no_edit bloquea todo UPDATE: se desactiva solo dentro de esta
-- migración (que corre con el rol administrador) y se vuelve a activar abajo.
ALTER TABLE audit.access_log DISABLE TRIGGER audit_no_edit;

DO $$
DECLARE
    r          RECORD;
    cur_tenant UUID;
    seq        BIGINT;
    prev       VARCHAR(64);
BEGIN
    FOR r IN SELECT * FROM audit.access_log ORDER BY tenant_id, created_at, id LOOP
        IF cur_tenant IS DISTINCT FROM r.tenant_id THEN
            cur_tenant := r.tenant_id;
            seq := 0;
            prev := repeat('0', 64);
        END IF;
        seq := seq + 1;
        UPDATE audit.access_log
           SET chain_seq = seq,
               prev_hash = prev,
               row_hash  = audit.compute_row_hash(prev, r.id, r.tenant_id, seq, r.actor, r.action,
                               r.resource_type, r.resource_id, r.correlation_id, r.created_at)
         WHERE id = r.id
        RETURNING row_hash INTO prev;
    END LOOP;
END
$$;

ALTER TABLE audit.access_log ENABLE TRIGGER audit_no_edit;

ALTER TABLE audit.access_log
    ALTER COLUMN chain_seq SET NOT NULL,
    ALTER COLUMN prev_hash SET NOT NULL,
    ALTER COLUMN row_hash  SET NOT NULL,
    ADD CONSTRAINT access_log_tenant_seq_uk UNIQUE (tenant_id, chain_seq);

-- --- Cabeza de la cadena por tenant ------------------------------------------
-- El trigger bloquea esta fila (SELECT ... FOR UPDATE) mientras encadena: dos
-- INSERT simultáneos del mismo tenant se serializan y nunca apuntan al mismo
-- "anterior" (la cadena no se bifurca). La app no tiene permisos sobre ella: solo
-- la toca el trigger, que corre con los privilegios de su dueño.
CREATE TABLE audit.chain_head (
    tenant_id  UUID        PRIMARY KEY,
    last_seq   BIGINT      NOT NULL,
    last_hash  VARCHAR(64)    NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO audit.chain_head (tenant_id, last_seq, last_hash)
SELECT DISTINCT ON (tenant_id) tenant_id, chain_seq, row_hash
FROM audit.access_log
ORDER BY tenant_id, chain_seq DESC;

ALTER TABLE audit.chain_head ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit.chain_head FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit.chain_head
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
REVOKE ALL ON audit.chain_head FROM PUBLIC;

-- --- Trigger que encadena cada INSERT ----------------------------------------
-- SECURITY DEFINER: corre como el dueño (administrador) para poder leer y
-- actualizar chain_head; search_path fijo para que nadie suplante funciones.
CREATE FUNCTION audit.link_to_chain() RETURNS trigger
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = pg_catalog
AS $$
DECLARE
    head_seq  BIGINT;
    head_hash VARCHAR(64);
BEGIN
    INSERT INTO audit.chain_head (tenant_id, last_seq, last_hash)
    VALUES (NEW.tenant_id, 0, repeat('0', 64))
    ON CONFLICT (tenant_id) DO NOTHING;

    SELECT last_seq, last_hash INTO head_seq, head_hash
      FROM audit.chain_head
     WHERE tenant_id = NEW.tenant_id
       FOR UPDATE;

    NEW.chain_seq := head_seq + 1;
    NEW.prev_hash := head_hash;
    NEW.row_hash  := audit.compute_row_hash(NEW.prev_hash, NEW.id, NEW.tenant_id, NEW.chain_seq, NEW.actor,
                         NEW.action, NEW.resource_type, NEW.resource_id, NEW.correlation_id, NEW.created_at);

    UPDATE audit.chain_head
       SET last_seq = NEW.chain_seq, last_hash = NEW.row_hash, updated_at = now()
     WHERE tenant_id = NEW.tenant_id;
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION audit.link_to_chain() FROM PUBLIC;

CREATE TRIGGER audit_link_chain
    BEFORE INSERT ON audit.access_log
    FOR EACH ROW EXECUTE FUNCTION audit.link_to_chain();

-- --- Verificación -----------------------------------------------------------
-- Devuelve una fila por cada eslabón roto del tenant (vacío = cadena íntegra):
--   HASH_MISMATCH: la fila se alteró (su hash no coincide con su contenido).
--   BROKEN_LINK:   prev_hash no es el hash de la fila anterior, o falta una
--                  secuencia (se borró una fila).
-- SECURITY INVOKER: la app la llama con su rol y la RLS limita a su tenant.
CREATE FUNCTION audit.verify_chain(p_tenant_id UUID)
    RETURNS TABLE (chain_seq BIGINT, problem TEXT)
    LANGUAGE sql
    STABLE
    SET search_path = pg_catalog
AS $$
    SELECT l.chain_seq,
           CASE WHEN l.row_hash <> audit.compute_row_hash(l.prev_hash, l.id, l.tenant_id, l.chain_seq, l.actor,
                         l.action, l.resource_type, l.resource_id, l.correlation_id, l.created_at)
                THEN 'HASH_MISMATCH' ELSE 'BROKEN_LINK' END
      FROM (SELECT a.*,
                   lag(a.row_hash)  OVER (ORDER BY a.chain_seq) AS expected_prev,
                   lag(a.chain_seq) OVER (ORDER BY a.chain_seq) AS previous_seq
              FROM audit.access_log a
             WHERE a.tenant_id = p_tenant_id) l
     WHERE l.row_hash <> audit.compute_row_hash(l.prev_hash, l.id, l.tenant_id, l.chain_seq, l.actor,
                             l.action, l.resource_type, l.resource_id, l.correlation_id, l.created_at)
        OR l.prev_hash <> coalesce(l.expected_prev, repeat('0', 64))
        OR l.chain_seq <> coalesce(l.previous_seq, 0) + 1
     ORDER BY l.chain_seq
$$;

GRANT EXECUTE ON FUNCTION audit.verify_chain(UUID) TO doggycredit_app;
GRANT EXECUTE ON FUNCTION audit.compute_row_hash(TEXT, UUID, UUID, BIGINT, TEXT, TEXT, TEXT, UUID, TEXT, TIMESTAMPTZ)
    TO doggycredit_app;
