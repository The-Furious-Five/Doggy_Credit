package com.doggycredit.shared;

import java.util.UUID;

/**
 * SPIKE-1: tenant de la operación en curso, en un {@link ThreadLocal}.
 * <p>
 * En una petición HTTP normal la puebla {@code TenantClaimFilter} justo
 * después de validar el {@code tenant_id} del JWT, y de ahí toman el
 * discriminador {@code @TenantId} de Hibernate y la variable de sesión de
 * PostgreSQL que hace cumplir Row-Level Security — ningún módulo de
 * negocio necesita tocar esta clase para eso.
 * <p>
 * Público (no {@code shared.internal}) para el código que legítimamente
 * corre <b>sin</b> una petición HTTP detrás — un job programado, un
 * listener de eventos, o una prueba que ejercita un componente
 * directamente sin pasar por el filtro de seguridad — y necesita declarar
 * "esto corre en nombre del tenant X" antes de tocar el repositorio.
 * Siempre en un bloque {@code try/finally} con {@link #clear()}: sin eso,
 * el valor se filtra a la siguiente operación que reutilice el mismo hilo
 * (HikariCP y los pools de hilos del servidor los reciclan).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    /** {@code null} si no hay tenant en la operación en curso (falla cerrado en quien lo consuma). */
    public static UUID currentOrNone() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
