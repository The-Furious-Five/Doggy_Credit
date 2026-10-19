package com.doggycredit.tenancy.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface TenantRepository extends JpaRepository<Tenant, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tenant t where t.id = :id")
    Optional<Tenant> findForUpdate(UUID id);

    /** A-12: tenants con una bajada programada cuyo período ya cerró. */
    List<Tenant> findByBajadaEfectivaEnLessThanEqual(Instant now);
}
