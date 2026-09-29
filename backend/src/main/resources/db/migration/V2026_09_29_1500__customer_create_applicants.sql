-- RF-PER-01.1: datos declarados del solicitante. El documento se cifra y la
-- referencia HMAC permite unicidad por tenant sin persistirlo en claro.
CREATE TABLE customer.applicants (
    id                          uuid PRIMARY KEY,
    tenant_id                   uuid NOT NULL,
    document_ref                char(64) NOT NULL,
    document_ciphertext         text NOT NULL,
    activity                    varchar(120) NOT NULL,
    activity_seniority_months   integer NOT NULL CHECK (activity_seniority_months BETWEEN 0 AND 1200),
    monthly_income              numeric(19, 2) NOT NULL CHECK (monthly_income > 0),
    income_currency             char(3) NOT NULL CHECK (income_currency IN ('BOB', 'USD')),
    credit_destination          varchar(250) NOT NULL,
    created_at                  timestamp(6) with time zone NOT NULL,
    CONSTRAINT applicants_tenant_document_uk UNIQUE (tenant_id, document_ref)
);

CREATE INDEX applicants_tenant_id_idx ON customer.applicants (tenant_id, id);
