package com.doggycredit.tenancy.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.doggycredit.shared.ErrorCode;
import com.doggycredit.shared.TenantAccessPolicy;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantRegistry;

/** E-14: consulta el servidor en cada petición; no conserva el estado del tenant en el token ni en caché. */
@Component
class TenantStatusAccessPolicy implements TenantAccessPolicy {

    private final TenantRegistry tenants;

    TenantStatusAccessPolicy(TenantRegistry tenants) {
        this.tenants = tenants;
    }

    @Override
    public Optional<ErrorCode> rejectionFor(UUID tenantId) {
        // La ausencia conserva el manejo existente de cada operación (p. ej. TENANT_NOT_FOUND).
        return tenants.findById(TenantId.of(tenantId))
                .filter(tenant -> !tenant.activo())
                .map(tenant -> ErrorCode.TENANT_SUSPENDED);
    }
}
