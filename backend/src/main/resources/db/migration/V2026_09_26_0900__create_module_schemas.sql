-- =============================================================================
-- V2026_09_26_0900__create_module_schemas.sql
-- Un esquema por módulo (§10.2 del informe de arquitectura). Cada módulo es
-- dueño exclusivo de las tablas de su esquema; está prohibido hacer JOIN
-- entre esquemas de dos módulos distintos (capítulo 6 de la guía).
--
-- Esta migración NO crea tablas, roles ni políticas de RLS: eso es
-- SPIKE-1 (Juanjo), sobre la tabla que decida usar primero. Aquí solo se
-- reserva el namespace de cada módulo para que nadie tenga que decidirlo
-- por su cuenta ni improvisar un nombre distinto.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS tenancy;
CREATE SCHEMA IF NOT EXISTS billing;
CREATE SCHEMA IF NOT EXISTS consent;
CREATE SCHEMA IF NOT EXISTS customer;
CREATE SCHEMA IF NOT EXISTS integration;
CREATE SCHEMA IF NOT EXISTS scoring;
CREATE SCHEMA IF NOT EXISTS recommendation;
CREATE SCHEMA IF NOT EXISTS assessment;
CREATE SCHEMA IF NOT EXISTS analytics;
CREATE SCHEMA IF NOT EXISTS audit;
