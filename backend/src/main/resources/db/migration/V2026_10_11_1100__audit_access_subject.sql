-- C-11 (LOS-112), RF-CON-03: reporte de accesos. Cada registro de la bitácora puede decir
-- de qué titular son los datos que tocó (applicant_id) y con qué consentimiento
-- (consent_id). Así el reporte responde «quién consultó los datos de este titular y con
-- qué autorización» leyendo solo audit.access_log, sin JOIN entre esquemas de módulos.
--
-- Nulos en los registros anteriores y en las acciones que no tocan datos de un titular:
-- no se infiere un titular que no se registró.
--
-- Límite conocido (deuda): estas dos columnas no entran en el hash de la cadena (C-04).
-- Las protege el trigger append-only (nadie puede hacer UPDATE ni DELETE), pero un
-- superusuario que lo desactive podría reasignarlas sin que verify_chain() lo note.
-- Meterlas en el hash exige una versión 2 de compute_row_hash para las filas nuevas.
ALTER TABLE audit.access_log
    ADD COLUMN applicant_id UUID,
    ADD COLUMN consent_id   UUID;

-- El reporte filtra por titular o por correlation-id, de lo más reciente a lo más antiguo
-- con cursor sobre chain_seq (igual que /auditoria).
CREATE INDEX access_log_tenant_applicant_idx
    ON audit.access_log (tenant_id, applicant_id, chain_seq DESC)
    WHERE applicant_id IS NOT NULL;
CREATE INDEX access_log_tenant_correlation_idx
    ON audit.access_log (tenant_id, correlation_id, chain_seq DESC);
