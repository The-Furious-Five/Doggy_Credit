package com.doggycredit.assessment.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CreditAssessmentRepository extends JpaRepository<CreditAssessment, UUID> {

	Optional<CreditAssessment> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

	Optional<CreditAssessment> findByIdAndTenantId(UUID id, UUID tenantId);

	/** C-09. Filtra por tenant además de la RLS (defensa en profundidad). */
	@Query("""
			select a from CreditAssessment a
			where a.tenantId = :tenantId
			  and (:applicantId is null or a.applicantId = :applicantId)
			  and a.createdAt >= :from
			order by a.createdAt desc, a.id""")
	Page<CreditAssessment> search(UUID tenantId, UUID applicantId, Instant from, Pageable pageable);

}
