package com.doggycredit.tenancy;

import java.util.List;
import java.util.Optional;

/**
 * HAB-03: API pública del módulo tenancy. Única fuente de verdad del
 * tenant_id (ADR-002/ADR-003). Los demás módulos consultan aquí si un
 * tenant existe y cuál es su plan.
 */
public interface TenantRegistry {

    /** Busca un tenant por su id; vacío si no existe. */
    Optional<TenantInfo> findById(TenantId id);

    /**
     * Exige que el tenant exista; si no, lanza excepción.
     * @throws TenantNotFoundException si el tenant no está registrado
     */
    TenantInfo requireById(TenantId id);

    /** Lists all tenants (for platform admin). */
    List<TenantInfo> findAll();

    /** Updates tenant fields (activation, plan change). */
    TenantInfo update(TenantId id, String plan, Boolean activo);

    /** Updates a tenant's display name, for its own tenant administrator. */
    TenantInfo updateName(TenantId id, String nombre);
    /** Tenants activos, para procesos que recorren todos (p. ej. el job de deriva, D-08). */
    List<TenantInfo> findAllActive();
}
