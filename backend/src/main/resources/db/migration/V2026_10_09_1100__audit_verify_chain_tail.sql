-- C-13 (LOS-114): verify_chain() también detecta que falte la cola de la cadena.
--
-- Hasta ahora solo comparaba cada fila con la anterior, así que si alguien borraba la
-- ÚLTIMA fila (o todas) lo que quedaba seguía siendo una cadena perfecta. Ahora se
-- compara además contra audit.chain_head, que el trigger actualiza en cada INSERT con
-- la secuencia y el hash que DEBERÍA tener el último eslabón:
--   MISSING_TAIL:  no existe la fila last_seq (se borró la cola, o toda la cadena).
--   HEAD_MISMATCH: la fila last_seq existe pero su hash no es el que guardó el trigger
--                  (se reescribió la última fila recalculando su hash).
--
-- Límite conocido: quien además retroceda chain_head (rol administrador) no se detecta
-- aquí; para eso está el ancla periódica de las cabezas (informe de arquitectura §12).

-- verify_chain es SECURITY INVOKER: la app necesita leer chain_head. Solo lectura, y la
-- RLS de chain_head (tenant_isolation) la limita a la cabeza de su propio tenant.
GRANT SELECT ON audit.chain_head TO doggycredit_app;

CREATE OR REPLACE FUNCTION audit.verify_chain(p_tenant_id UUID)
    RETURNS TABLE (chain_seq BIGINT, problem TEXT)
    LANGUAGE sql
    STABLE
    SET search_path = pg_catalog
AS $$
    SELECT breaks.chain_seq, breaks.problem
      FROM (
            SELECT l.chain_seq,
                   CASE WHEN l.row_hash <> audit.compute_row_hash(l.prev_hash, l.id, l.tenant_id, l.chain_seq, l.actor,
                                 l.action, l.resource_type, l.resource_id, l.correlation_id, l.created_at)
                        THEN 'HASH_MISMATCH' ELSE 'BROKEN_LINK' END AS problem
              FROM (SELECT a.*,
                           lag(a.row_hash)  OVER (ORDER BY a.chain_seq) AS expected_prev,
                           lag(a.chain_seq) OVER (ORDER BY a.chain_seq) AS previous_seq
                      FROM audit.access_log a
                     WHERE a.tenant_id = p_tenant_id) l
             WHERE l.row_hash <> audit.compute_row_hash(l.prev_hash, l.id, l.tenant_id, l.chain_seq, l.actor,
                                     l.action, l.resource_type, l.resource_id, l.correlation_id, l.created_at)
                OR l.prev_hash <> coalesce(l.expected_prev, repeat('0', 64))
                OR l.chain_seq <> coalesce(l.previous_seq, 0) + 1

            UNION ALL

            -- La cola: el último eslabón tiene que ser exactamente el que registró el trigger.
            SELECT h.last_seq,
                   CASE WHEN tail.row_hash IS NULL THEN 'MISSING_TAIL' ELSE 'HEAD_MISMATCH' END
              FROM audit.chain_head h
              LEFT JOIN audit.access_log tail
                     ON tail.tenant_id = h.tenant_id AND tail.chain_seq = h.last_seq
             WHERE h.tenant_id = p_tenant_id
               AND h.last_seq > 0
               AND (tail.row_hash IS NULL OR tail.row_hash <> h.last_hash)
           ) breaks
     ORDER BY breaks.chain_seq, breaks.problem
$$;
