-- B-08: segmento declarado del solicitante, para filtrar /app/clientes. Los
-- solicitantes existentes quedan como PERSONA (el valor por defecto del alta).
ALTER TABLE customer.applicants
    ADD COLUMN segment varchar(16) NOT NULL DEFAULT 'PERSONA'
        CHECK (segment IN ('PERSONA', 'EMPRENDEDOR', 'PYME'));

CREATE INDEX applicants_tenant_created_idx ON customer.applicants (tenant_id, created_at DESC);
