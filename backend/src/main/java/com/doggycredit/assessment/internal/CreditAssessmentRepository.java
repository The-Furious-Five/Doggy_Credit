package com.doggycredit.assessment.internal;

import java.time.Instant;
import java.util.Collection;
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

	/**
	 * C-10 (RF-DEC-03): casos de la bandeja, los que una persona tiene que resolver y nadie
	 * resolvió todavía, del más antiguo al más reciente. Mismo esquema (assessment): sin
	 * JOIN entre módulos.
	 */
	@Query("""
			select a from CreditAssessment a
			where a.tenantId = :tenantId
			  and a.status in :statuses
			  and not exists (select r.id from ManualReviewResolution r
			                  where r.tenantId = a.tenantId and r.assessmentId = a.id)
			order by a.createdAt asc, a.id""")
	Page<CreditAssessment> findPendingReview(UUID tenantId, Collection<String> statuses, Pageable pageable);

}
