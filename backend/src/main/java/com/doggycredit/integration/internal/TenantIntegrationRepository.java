package com.doggycredit.integration.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TenantIntegrationRepository extends JpaRepository<TenantIntegration, UUID> {

	List<TenantIntegration> findByTenantId(UUID tenantId);

	Optional<TenantIntegration> findByTenantIdAndSource(UUID tenantId, String source);

}
