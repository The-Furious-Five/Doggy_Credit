package com.doggycredit.assessment.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CreditAssessmentRepository extends JpaRepository<CreditAssessment, UUID> {

	Optional<CreditAssessment> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

	Optional<CreditAssessment> findByIdAndTenantId(UUID id, UUID tenantId);

}
