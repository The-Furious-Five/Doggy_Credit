-- RF-SEG-03.1: bitácora append-only. El trigger también protege frente al
-- propietario de la tabla; los privilegios reducen la superficie del rol app.
CREATE TABLE audit.access_log (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    actor           varchar(255) NOT NULL,
    action          varchar(100) NOT NULL,
    resource_type   varchar(100) NOT NULL,
    resource_id     uuid,
    correlation_id  varchar(128) NOT NULL,
    created_at      timestamp(6) with time zone NOT NULL DEFAULT now()
);

CREATE INDEX access_log_tenant_created_idx ON audit.access_log (tenant_id, created_at);

CREATE OR REPLACE FUNCTION audit.forbid_access_log_edit()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit.access_log is append-only'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER audit_no_edit
BEFORE UPDATE OR DELETE ON audit.access_log
FOR EACH ROW EXECUTE FUNCTION audit.forbid_access_log_edit();

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'doggy_app') THEN
        REVOKE UPDATE, DELETE, TRUNCATE ON audit.access_log FROM doggy_app;
        GRANT INSERT, SELECT ON audit.access_log TO doggy_app;
    END IF;
END;
$$;
