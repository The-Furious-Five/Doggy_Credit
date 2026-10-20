package com.doggycredit.tenancy;

import java.util.List;
import java.util.UUID;

/**
 * A-11 / A-12: lo que el cambio de plan necesita de las integraciones del tenant.
 * Lo implementa el módulo integration: tenancy no puede depender de él, porque
 * integration ya depende de tenancy. Las fuentes van por nombre (BANCO, WALLET).
 * <p>
 * Quien llama debe tener al tenant en {@code TenantContext} antes de abrir la
 * transacción: las filas están bajo RLS.
 */
public interface PlanIntegrations {

    /** RF-ANA-01: integraciones CONECTADO hoy, el buró incluido. */
    int activeCount(UUID tenantId);

    /** Integraciones activas que no caben en {@code maxIntegraciones}; no cambia nada. */
    List<String> exceeding(UUID tenantId, int maxIntegraciones);

    /** Desactiva, sin borrar, las que no caben en {@code maxIntegraciones} y las devuelve. */
    List<String> suspendExceeding(UUID tenantId, int maxIntegraciones);

    /** Integraciones que una bajada de plan dejó desactivadas y siguen así. */
    List<String> suspended(UUID tenantId);

    /** Reactiva las que desactivó una bajada, hasta donde permita {@code maxIntegraciones}. */
    List<String> restoreSuspended(UUID tenantId, int maxIntegraciones);

}
