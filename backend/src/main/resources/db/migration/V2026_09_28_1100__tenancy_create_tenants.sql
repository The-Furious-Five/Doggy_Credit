-- =============================================================================
-- V2026_09_28_1100__tenancy_create_tenants.sql
-- HAB-03: registro maestro de tenant (módulo tenancy).
-- Única fuente de verdad del tenant_id (ADR-002/ADR-003). Los datos iniciales
-- se siembran desde seed/tenants.yaml. RLS queda para SPIKE-1.
-- =============================================================================

CREATE TABLE tenancy.tenants (
    id         uuid         PRIMARY KEY,
    nombre     varchar(100) NOT NULL,
    nit        varchar(20)  NOT NULL UNIQUE,
    tipo       varchar(20)  NOT NULL CHECK (tipo IN ('IMF', 'FINTECH', 'BANCO', 'COOPERATIVA')),
    plan       varchar(20)  NOT NULL CHECK (plan IN ('FREE', 'PRO', 'ENTERPRISE')),
    pais       char(2)      NOT NULL,
    moneda     char(3)      NOT NULL,
    activo     boolean      NOT NULL DEFAULT true,
    creado_en  timestamp(6) with time zone NOT NULL DEFAULT now()
);

-- Semilla de demo (seed/tenants.yaml). Nadie escribe otro tenant_id de demo
-- en ningún otro lugar del repositorio (lección 2 de MedicalSys).
INSERT INTO tenancy.tenants (id, nombre, nit, tipo, plan, pais, moneda) VALUES
    ('9c7e7c1a-0000-4000-8000-000000000001', 'Progreso Andino', '1000000010', 'IMF', 'FREE', 'BO', 'BOB'),
    ('9c7e7c1a-0000-4000-8000-000000000002', 'Fintech Vecina', '1000000020', 'FINTECH', 'PRO', 'BO', 'BOB');
