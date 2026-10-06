package com.doggycredit.customer.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ApplicantRepository extends JpaRepository<Applicant, UUID> {

	boolean existsByTenantIdAndDocumentRef(UUID tenantId, String documentRef);

	Optional<Applicant> findByTenantIdAndDocumentRef(UUID tenantId, String documentRef);

	Optional<Applicant> findByIdAndTenantId(UUID id, UUID tenantId);

	@Query("""
			select a from Applicant a
			where a.tenantId = :tenantId
			  and (:segment is null or a.segment = :segment)
			  and (:documentRef is null or a.documentRef = :documentRef)
			order by a.createdAt desc, a.id""")
	Page<Applicant> search(UUID tenantId, String segment, String documentRef, Pageable pageable);

}
