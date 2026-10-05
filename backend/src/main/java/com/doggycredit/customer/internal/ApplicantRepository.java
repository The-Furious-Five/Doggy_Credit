package com.doggycredit.customer.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ApplicantRepository extends JpaRepository<Applicant, UUID> {

	boolean existsByTenantIdAndDocumentRef(UUID tenantId, String documentRef);

	Optional<Applicant> findByTenantIdAndDocumentRef(UUID tenantId, String documentRef);

	Optional<Applicant> findByIdAndTenantId(UUID id, UUID tenantId);

}
