package com.doggycredit.tenancy;

import java.util.UUID;

/**
 * HAB-03: vista de lectura de un tenant. Record inmutable para que los
 * módulos consumidores nunca modifiquen el registro maestro.
 */
public record TenantInfo(
        UUID id,
        String nombre,
        String nit,
        String tipo,
        String plan,
        String pais,
        String moneda,
        boolean activo) {
}
