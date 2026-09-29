package com.doggycredit.shared.internal;

import java.util.UUID;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

import com.doggycredit.shared.TenantContext;

/**
 * SPIKE-1: capa 1 de aislamiento (ORM). Alimenta el discriminador
 * {@code @TenantId} de Hibernate: toda entidad anotada recibe
 * automáticamente {@code WHERE tenant_id = <esto>} en cada consulta, y este
 * valor se asigna solo al insertar.
 * <p>
 * Sin tenant en {@link TenantContext} (arranque, migraciones, rutas
 * públicas), se resuelve a {@link #NONE}: un UUID que ningún tenant real
 * usará nunca, para que la ausencia de contexto filtre a cero filas en vez
 * de lanzar en medio de la inicialización de Spring.
 */
final class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    static final UUID NONE = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID tenant = TenantContext.currentOrNone();
        return tenant != null ? tenant : NONE;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
