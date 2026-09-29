-- =============================================================================
-- V2026_09_29_1401__create_restricted_app_role.sql
-- SPIKE-1: sin esto, la política de RLS de la migración anterior no aísla
-- nada. PostgreSQL exime a los superusuarios de Row-Level Security de forma
-- incondicional, incluso con FORCE ROW LEVEL SECURITY (eso solo cubre al
-- dueño de la tabla cuando NO es superusuario) — y el rol con el que se
-- conecta la app (doggy_app en infra/compose.yaml, "test" en los tests con
-- Testcontainers) ES superusuario: la imagen oficial de PostgreSQL siempre
-- le da ese atributo al rol que llega por POSTGRES_USER al iniciar el
-- contenedor. Comprobado de forma empírica antes de esta migración: la
-- prueba de concurrencia de RLS pasaba sin que la política filtrara una
-- sola fila.
--
-- No se le quita el superusuario al rol de arranque (PostgreSQL lo
-- prohíbe: "the bootstrap superuser must have the SUPERUSER attribute").
-- En su lugar, se crea un segundo rol SIN superusuario ni LOGIN propio, y
-- com.doggycredit.shared.internal.TenantAwareDataSource hace `SET ROLE` a
-- él en cada conexión que use la app (nunca en las que usa Flyway —esa
-- corre por flywayDataSource, sin pasar por esa clase— porque migrar sí
-- necesita privilegios de administrador, incluida la creación de este
-- mismo rol).
-- =============================================================================

-- Los roles son del cluster, no de la base: si se recrea la base doggycredit
-- (o la corre otro entorno contra el mismo servidor) el rol ya existe. Se
-- crea solo si falta y siempre se fuerzan los atributos seguros, por si un
-- rol preexistente con ese nombre los tuviera distintos.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'doggycredit_app') THEN
        CREATE ROLE doggycredit_app;
    END IF;
END
$$;
ALTER ROLE doggycredit_app NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
GRANT doggycredit_app TO CURRENT_USER;

GRANT USAGE ON SCHEMA consent, tenancy TO doggycredit_app;

-- Alta y lectura de consentimientos (RF-CON-01.1). Sin UPDATE/DELETE:
-- todavía no existe ningún flujo de revocación que los necesite.
GRANT SELECT, INSERT ON consent.consents TO doggycredit_app;

-- Solo lectura: el registro maestro de tenants lo alimenta la semilla vía
-- migración (como esta misma, con privilegio de administrador), no la app.
GRANT SELECT ON tenancy.tenants TO doggycredit_app;

-- Nota para el resto del equipo: cualquier módulo nuevo que agregue un
-- esquema o una tabla (assessment, scoring, audit, ...) necesita su propio
-- GRANT a doggycredit_app en la misma migración que la crea. Los privilegios
-- por defecto de PostgreSQL son siempre por esquema; no hay una forma de
-- decir "para cualquier esquema futuro también".
