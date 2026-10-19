package com.doggycredit.billing;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto hacia el plan vigente de un tenant. Lo implementa tenancy, dueño de
 * {@code tenancy.tenants}; así billing no depende de tenancy (tenancy ya depende de billing).
 */
public interface TenantPlans {

    Optional<String> planOf(UUID tenantId);

}
