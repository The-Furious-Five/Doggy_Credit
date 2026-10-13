package com.doggycredit.assessment.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ManualReviewResolutionRepository extends JpaRepository<ManualReviewResolution, UUID> {

	boolean existsByTenantIdAndAssessmentId(UUID tenantId, UUID assessmentId);

}
