package com.doggycredit.customer.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface FinancialProfileRepository extends JpaRepository<FinancialProfileSnapshot, UUID> {

	Optional<FinancialProfileSnapshot> findFirstByTenantIdAndApplicantIdOrderByCreatedAtDesc(UUID tenantId,
			UUID applicantId);

}
