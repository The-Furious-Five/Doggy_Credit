-- =============================================================================
-- V2026_09_29_1402__create_event_publication_registry.sql
-- Paga DEBT-015: Spring Modulith no distribuye el SQL de su tabla interna
-- de registro de eventos para Flyway, así que se dejó temporalmente en
-- ddl-auto: update. El disparador que la propia deuda nombraba ("cuando
-- exista la primera entidad de negocio real") ya se cumplió con Consent, y
-- ddl-auto: update dejó de ser viable de todas formas: con el rol
-- restringido de SPIKE-1 (V2026_09_29_1401) la app no puede alterar el
-- esquema, así que ese auto-DDL fallaba en silencio (solo un WARN).
--
-- Columnas: las de JpaEventPublication, pero con TEXT (como el esquema
-- oficial de Spring Modulith para PostgreSQL) en vez del varchar(255) que
-- generaba Hibernate: el evento serializado a JSON supera 255 caracteres
-- en cuanto tiene más de un par de campos, y el INSERT fallaría con el
-- primer evento real publicado por el outbox (DEBT-002).
-- =============================================================================

CREATE TABLE event_publication (
    id                      uuid                        NOT NULL PRIMARY KEY,
    listener_id             text                        NOT NULL,
    event_type              text                        NOT NULL,
    serialized_event        text                        NOT NULL,
    publication_date        timestamp(6) with time zone NOT NULL,
    completion_date         timestamp(6) with time zone,
    completion_attempts     integer                     NOT NULL,
    last_resubmission_date  timestamp(6) with time zone,
    status                  varchar(255)                CHECK (status IN
                                ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED'))
);

-- Modulith busca las publicaciones pendientes (completion_date IS NULL) al
-- arrancar y al reintentar; sin índice, es un escaneo completo cada vez.
CREATE INDEX event_publication_incomplete_idx ON event_publication (publication_date)
    WHERE completion_date IS NULL;

GRANT SELECT, INSERT, UPDATE, DELETE ON event_publication TO doggycredit_app;
