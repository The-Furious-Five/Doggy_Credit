-- B-07: revocación de consentimientos con evidencia (quién y cuándo). Antes la
-- app solo tenía SELECT, INSERT (V2026_09_29_1401); el UPDATE se limita a las
-- columnas que cambia la revocación: el resto del consentimiento es inmutable.
ALTER TABLE consent.consents
    ADD COLUMN revocado_en  timestamp(6) with time zone,
    ADD COLUMN revocado_por varchar(255),
    ADD CONSTRAINT consents_revocacion_ck
        CHECK ((estado = 'REVOCADO') = (revocado_en IS NOT NULL AND revocado_por IS NOT NULL));

GRANT UPDATE (estado, revocado_en, revocado_por) ON consent.consents TO doggycredit_app;
