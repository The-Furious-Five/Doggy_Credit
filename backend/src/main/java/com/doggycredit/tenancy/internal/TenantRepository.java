package com.doggycredit.tenancy.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TenantRepository extends JpaRepository<Tenant, UUID> {

    /** A-12: tenants con una bajada programada cuyo período ya cerró. */
    List<Tenant> findByBajadaEfectivaEnLessThanEqual(Instant now);
}
