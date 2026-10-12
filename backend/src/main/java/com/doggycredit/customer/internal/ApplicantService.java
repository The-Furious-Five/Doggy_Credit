package com.doggycredit.customer.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doggycredit.audit.AccessSubject;
import com.doggycredit.audit.AuditTrail;
import com.doggycredit.consent.ApplicantDocuments;
import com.doggycredit.customer.ApplicantAlreadyExistsException;
import com.doggycredit.customer.ApplicantNotFoundException;
import com.doggycredit.customer.ApplicantPage;
import com.doggycredit.customer.ApplicantSegment;
import com.doggycredit.customer.ApplicantProfile;
import com.doggycredit.customer.ApplicantRegistration;
import com.doggycredit.customer.ApplicantRegistry;
import com.doggycredit.shared.DocumentNumber;
import com.doggycredit.shared.DocumentReference;
import com.doggycredit.shared.FieldEncryption;
import com.doggycredit.shared.Money;
import com.doggycredit.shared.UnknownApplicantException;

@Service
class ApplicantService implements ApplicantRegistry, ApplicantDocuments {

	/** UNIQUE (tenant_id, document_ref) de V2026_09_29_1500__customer_create_applicants.sql. */
	private static final String DOCUMENT_UNIQUE_CONSTRAINT = "applicants_tenant_document_uk";

	private final ApplicantRepository applicants;
	private final FieldEncryption encryption;
	private final DocumentReference documentReference;
	private final AuditTrail audit;

	ApplicantService(ApplicantRepository applicants, FieldEncryption encryption,
			DocumentReference documentReference, AuditTrail audit) {
		this.applicants = applicants;
		this.encryption = encryption;
		this.documentReference = documentReference;
		this.audit = audit;
	}

	@Override
	@Transactional
	public ApplicantProfile register(UUID tenantId, ApplicantRegistration registration) {
		String normalized = DocumentNumber.normalize(registration.document());
		if (normalized.isBlank()) {
			throw new IllegalArgumentException("document must not be blank");
		}
		String reference = documentReference.create(tenantId, normalized);
		if (applicants.existsByTenantIdAndDocumentRef(tenantId, reference)) {
			throw new ApplicantAlreadyExistsException();
		}
		Applicant applicant = new Applicant(tenantId, reference, encryption.encrypt(tenantId, normalized),
				registration.activity().strip(), registration.activitySeniorityMonths(),
				registration.monthlyIncome(), registration.currency(), registration.creditDestination().strip(),
				registration.segment().name());
		try {
			// saveAndFlush: el INSERT sale ya, así el choque con el UNIQUE se captura aquí
			// y no al hacer commit, fuera de este método.
			applicants.saveAndFlush(applicant);
		}
		catch (DataIntegrityViolationException exception) {
			// Dos altas simultáneas pueden pasar el existsBy... a la vez: la base de datos
			// decide y la perdedora responde 409, no 500.
			if (violates(exception, DOCUMENT_UNIQUE_CONSTRAINT)) {
				throw new ApplicantAlreadyExistsException();
			}
			throw exception;
		}
		audit.record(tenantId, "APPLICANT_CREATED", "APPLICANT", applicant.id(), AccessSubject.applicant(applicant.id()));
		return toProfile(tenantId, applicant);
	}

	@Override
	@Transactional(readOnly = true)
	public ApplicantProfile findRequired(UUID tenantId, String document) {
		String normalized = DocumentNumber.normalize(document);
		String reference = documentReference.create(tenantId, normalized);
		return applicants.findByTenantIdAndDocumentRef(tenantId, reference)
			.map(applicant -> toProfile(tenantId, applicant))
			.orElseThrow(ApplicantNotFoundException::new);
	}

	@Override
	@Transactional(readOnly = true)
	public ApplicantProfile findById(UUID tenantId, UUID applicantId) {
		return applicants.findByIdAndTenantId(applicantId, tenantId)
			.map(applicant -> toProfile(tenantId, applicant))
			.orElseThrow(UnknownApplicantException::new);
	}

	@Override
	@Transactional(readOnly = true)
	public ApplicantPage list(UUID tenantId, ApplicantSegment segment, String document, int page, int size) {
		String normalized = document == null ? "" : DocumentNumber.normalize(document);
		var result = applicants.search(tenantId, segment == null ? null : segment.name(),
				normalized.isEmpty() ? null : documentReference.create(tenantId, normalized),
				PageRequest.of(page - 1, size));
		return new ApplicantPage(result.map(applicant -> toProfile(tenantId, applicant)).getContent(),
				result.getTotalElements());
	}

	/** B-07: consent lista por solicitante a través de este puerto. */
	@Override
	@Transactional(readOnly = true)
	public Optional<String> documentOf(UUID tenantId, UUID applicantId) {
		return applicants.findByIdAndTenantId(applicantId, tenantId)
			.map(applicant -> encryption.decrypt(tenantId, applicant.documentCiphertext()));
	}

	/** C-11: consent sabe de qué titular es un consentimiento a través de este puerto. */
	@Override
	@Transactional(readOnly = true)
	public Optional<UUID> applicantIdOf(UUID tenantId, String documento) {
		String reference = documentReference.create(tenantId, DocumentNumber.normalize(documento));
		return applicants.findByTenantIdAndDocumentRef(tenantId, reference).map(applicant -> applicant.id());
	}

	private static boolean violates(DataIntegrityViolationException exception, String constraint) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation
					&& constraint.equalsIgnoreCase(violation.getConstraintName())) {
				return true;
			}
		}
		return false;
	}

	private ApplicantProfile toProfile(UUID tenantId, Applicant applicant) {
		return new ApplicantProfile(applicant.id(), encryption.decrypt(tenantId, applicant.documentCiphertext()),
				applicant.activity(), applicant.activitySeniorityMonths(),
				new Money(applicant.monthlyIncome(), applicant.incomeCurrency()), applicant.creditDestination(),
				applicant.createdAt(), ApplicantSegment.valueOf(applicant.segment()));
	}

}
