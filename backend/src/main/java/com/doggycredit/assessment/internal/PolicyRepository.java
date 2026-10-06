package com.doggycredit.assessment.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Filtra por tenant además de la RLS (defensa en profundidad). */
interface PolicyRepository extends JpaRepository<Policy, UUID> {

	List<Policy> findByTenantIdOrderByCode(UUID tenantId);

	List<Policy> findByTenantIdAndActiveTrue(UUID tenantId);

	Optional<Policy> findByIdAndTenantId(UUID id, UUID tenantId);

}
