package com.doggycredit.assessment.internal;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.assessment.AssessmentNotFoundException;
import com.doggycredit.assessment.AssessmentStatus;
import com.doggycredit.assessment.internal.CreditAssessment.ReviewCase;
import com.doggycredit.assessment.internal.ManualReviewResolution.Decision;
import com.doggycredit.audit.AuditTrail;
import com.doggycredit.customer.ApplicantRegistry;
import com.doggycredit.recommendation.ProductCatalog;
import com.doggycredit.shared.ErrorCode;
import com.doggycredit.shared.contract.model.Producto;

/**
 * C-10 (LOS-111), RF-DEC-03: bandeja de revisión manual. Lista los casos que las políticas
 * dejaron a una persona (nunca un rechazo automático) y registra su resolución con
 * justificación obligatoria, en la auditoría con el X-Correlation-Id de la petición.
 */
@Service
class ManualReviewService {

	/** Lo que necesita a una persona: revisión manual, pedir datos o resultado PARCIAL (RN-15). */
	static final Set<AssessmentStatus> IN_REVIEW = EnumSet.of(AssessmentStatus.REVISION_MANUAL,
			AssessmentStatus.SOLICITAR_DATOS, AssessmentStatus.PARCIAL);

	private static final String ONE_RESOLUTION_PER_CASE = "manual_review_resolutions_assessment_uk";

	private static final int MIN_JUSTIFICATION = 10;

	record Inbox(List<Item> items, long total, List<Producto> products) {
	}

	record Item(ReviewCase reviewCase, String document) {
	}

	private final CreditAssessmentRepository assessments;

	private final ManualReviewResolutionRepository resolutions;

	private final ApplicantRegistry applicants;

	private final ProductCatalog catalog;

	private final AuditTrail audit;

	ManualReviewService(CreditAssessmentRepository assessments, ManualReviewResolutionRepository resolutions,
			ApplicantRegistry applicants, ProductCatalog catalog, AuditTrail audit) {
		this.assessments = assessments;
		this.resolutions = resolutions;
		this.applicants = applicants;
		this.catalog = catalog;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	Inbox inbox(UUID tenantId, int page, int size) {
		Page<CreditAssessment> pending = assessments.findPendingReview(tenantId,
				IN_REVIEW.stream().map(Enum::name).toList(), PageRequest.of(page - 1, size));
		List<Item> items = pending.getContent().stream().map(CreditAssessment::reviewCase)
			.map(reviewCase -> new Item(reviewCase,
					applicants.findById(tenantId, reviewCase.applicantId()).document()))
			.toList();
		return new Inbox(items, pending.getTotalElements(), catalog.activeProducts());
	}

	@Transactional
	ManualReviewResolution resolve(UUID tenantId, UUID assessmentId, Decision decision, UUID productId,
			String justification, String resolvedBy) {
		String text = justification == null ? "" : justification.strip();
		if (text.length() < MIN_JUSTIFICATION) {
			throw invalid("La justificación es obligatoria (al menos " + MIN_JUSTIFICATION + " caracteres).");
		}
		if (decision == Decision.APROBADO && productId == null) {
			throw invalid("Para aprobar hay que elegir un producto.");
		}
		if (decision == Decision.RECHAZADO && productId != null) {
			throw invalid("Un rechazo no lleva producto.");
		}
		CreditAssessment assessment = assessments.findByIdAndTenantId(assessmentId, tenantId)
			.orElseThrow(AssessmentNotFoundException::new);
		if (!IN_REVIEW.contains(assessment.reviewCase().status())
				|| resolutions.existsByTenantIdAndAssessmentId(tenantId, assessmentId)) {
			throw alreadyResolved();
		}
		if (decision == Decision.APROBADO && !catalog.isActive(productId)) {
			throw new ErrorResponseException(HttpStatus.NOT_FOUND, ErrorCode.PRODUCT_NOT_FOUND
				.toProblemDetail("El producto no existe, está inactivo o pertenece a otro tenant."), null);
		}
		ManualReviewResolution resolution = new ManualReviewResolution(tenantId, assessmentId, decision, productId,
				text, resolvedBy);
		try {
			// saveAndFlush: el choque con otra resolución simultánea salta aquí, no en el commit.
			resolutions.saveAndFlush(resolution);
		}
		catch (DataIntegrityViolationException exception) {
			// Dos personas resolviendo el mismo caso a la vez: la base decide y la segunda recibe 409.
			if (violates(exception, ONE_RESOLUTION_PER_CASE)) {
				throw alreadyResolved();
			}
			throw exception;
		}
		audit.record(tenantId, "MANUAL_REVIEW_RESOLVED", "CREDIT_ASSESSMENT", assessmentId);
		return resolution;
	}

	private static ErrorResponseException invalid(String detail) {
		return new ErrorResponseException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.toProblemDetail(detail),
				null);
	}

	private static ErrorResponseException alreadyResolved() {
		return new ErrorResponseException(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_UPDATE
			.toProblemDetail("El caso ya no está en revisión manual: otra persona lo resolvió."), null);
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

}
