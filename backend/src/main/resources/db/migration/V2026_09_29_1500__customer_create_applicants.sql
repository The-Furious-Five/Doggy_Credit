-- RF-PER-01.1: datos declarados del solicitante. El documento se cifra y la
-- referencia HMAC permite unicidad por tenant sin persistirlo en claro.
-- varchar y no char(n): char rellena con espacios y Hibernate lo valida como
-- otro tipo JDBC (bpchar), así que ddl-auto: validate rechaza el arranque.
CREATE TABLE customer.applicants (
    id                          uuid PRIMARY KEY,
    tenant_id                   uuid NOT NULL,
    document_ref                varchar(64) NOT NULL,
    document_ciphertext         text NOT NULL,
    activity                    varchar(120) NOT NULL,
    activity_seniority_months   integer NOT NULL CHECK (activity_seniority_months BETWEEN 0 AND 1200),
    monthly_income              numeric(19, 2) NOT NULL CHECK (monthly_income > 0),
    income_currency             varchar(3) NOT NULL CHECK (income_currency IN ('BOB', 'USD')),
    credit_destination          varchar(250) NOT NULL,
    created_at                  timestamp(6) with time zone NOT NULL,
    CONSTRAINT applicants_tenant_document_uk UNIQUE (tenant_id, document_ref)
);

CREATE INDEX applicants_tenant_id_idx ON customer.applicants (tenant_id, id);

-- SPIKE-1: misma política que consent.consents (ver V2026_09_29_1400).
ALTER TABLE customer.applicants ENABLE ROW LEVEL SECURITY;
ALTER TABLE customer.applicants FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON customer.applicants
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT USAGE ON SCHEMA customer TO doggycredit_app;
GRANT SELECT, INSERT ON customer.applicants TO doggycredit_app;
