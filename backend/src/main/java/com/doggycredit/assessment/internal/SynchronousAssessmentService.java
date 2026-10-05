package com.doggycredit.assessment.internal;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doggycredit.assessment.AssessmentCommand;
import com.doggycredit.assessment.AssessmentNotFoundException;
import com.doggycredit.assessment.AssessmentOperations;
import com.doggycredit.assessment.AssessmentResult;
import com.doggycredit.assessment.AssessmentStatus;
import com.doggycredit.assessment.IdempotencyKeyReusedException;
import com.doggycredit.audit.AuditTrail;
import com.doggycredit.billing.QuotaGuard;
import com.doggycredit.customer.ApplicantProfile;
import com.doggycredit.customer.ApplicantRegistry;
import com.doggycredit.consent.Source;
import com.doggycredit.tenancy.TenantRegistry;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.integration.BureauReport;
import com.doggycredit.scoring.BasicScoreResult;
import com.doggycredit.scoring.BasicScorecard;
import com.doggycredit.scoring.ScorecardInput;
import com.doggycredit.shared.DocumentNumber;

@Service
class SynchronousAssessmentService implements AssessmentOperations {

	private final CreditAssessmentRepository assessments;
	private final ApplicantRegistry applicants;
	private final SourceCollector sources;
	private final BasicScorecard scorecard;
	private final AuditTrail audit;
	private final QuotaGuard quotaGuard;
	private final TenantRegistry tenantRegistry;

	SynchronousAssessmentService(CreditAssessmentRepository assessments, ApplicantRegistry applicants,
			SourceCollector sources, BasicScorecard scorecard, AuditTrail audit,
			QuotaGuard quotaGuard, TenantRegistry tenantRegistry) {
		this.assessments = assessments;
		this.applicants = applicants;
		this.sources = sources;
		this.scorecard = scorecard;
		this.audit = audit;
		this.quotaGuard = quotaGuard;
		this.tenantRegistry = tenantRegistry;
	}

	@Override
	@Transactional
	public AssessmentResult create(AssessmentCommand command) {
		String requestHash = requestHash(command);
		Optional<CreditAssessment> replay = assessments.findByTenantIdAndIdempotencyKey(
				command.tenantId(), command.idempotencyKey());
		if (replay.isPresent()) {
			if (!replay.get().requestHash().equals(requestHash)) {
				throw new IdempotencyKeyReusedException();
			}
			return replay.get().toResult();
		}

		TenantInfo tenant = tenantRegistry.requireById(TenantId.of(command.tenantId()));
		quotaGuard.requireEvaluationQuota(command.tenantId(), tenant.plan());

		String normalizedDocument = DocumentNumber.normalize(command.document());
		ApplicantProfile applicant = applicants.findRequired(command.tenantId(), normalizedDocument);
		SourceCollection collection = sources.collect(command.tenantId(), command.consentId(), normalizedDocument);
		BasicScoreResult score = scorecard.score(toScorecardInput(command, applicant, collection));
		AssessmentStatus status = collection.partial() ? AssessmentStatus.PARCIAL : AssessmentStatus.REVISION_MANUAL;
		CreditAssessment assessment = new CreditAssessment(command.tenantId(), applicant.id(), command.consentId(),
				command.idempotencyKey(), requestHash, command.purpose(), command.requestedAmount(),
				command.termMonths(), status, score, command.correlationId());
		assessments.save(assessment);
		audit.record(command.tenantId(), "ASSESSMENT_CREATED", "CREDIT_ASSESSMENT", assessment.id());
		return assessment.toResult();
	}

	@Override
	@Transactional(readOnly = true)
	public AssessmentResult get(UUID tenantId, UUID assessmentId) {
		return assessments.findByIdAndTenantId(assessmentId, tenantId)
			.map(CreditAssessment::toResult)
			.orElseThrow(AssessmentNotFoundException::new);
	}

	private ScorecardInput toScorecardInput(AssessmentCommand command, ApplicantProfile applicant,
			SourceCollection collection) {
		Optional<BureauReport> bureau = bureauReport(collection);
		// La mora es desconocida solo si el BURÓ falló (RN-15: nunca 0 inventado); que caiga
		// otra fuente no la vuelve desconocida. Si el buró respondió "sin registro" (informe
		// vacío), es una respuesta válida: sin deudas, mora 0.
		OptionalInt maxDaysPastDue = collection.failed(Source.BURO) ? OptionalInt.empty()
				: OptionalInt.of(bureau.stream()
					.flatMap(report -> report.endeudamiento().stream())
					.mapToInt(BureauReport.Endeudamiento::diasMoraMax12m)
					.max().orElse(0));
		int financialSeniority = bureau.map(BureauReport::antiguedadSistemaFinancieroMeses).orElse(0);
		BigDecimal requestedAmount = command.requestedAmount() == null ? BigDecimal.ZERO : command.requestedAmount();
		return new ScorecardInput(maxDaysPastDue, applicant.activitySeniorityMonths(), financialSeniority,
				requestedAmount, applicant.monthlyIncome().amount(), command.termMonths(), bureau.isPresent());
	}

	private String requestHash(AssessmentCommand command) {
		String canonical = String.join("|", DocumentNumber.normalize(command.document()),
				command.consentId().toString(), String.valueOf(command.purpose()),
				String.valueOf(command.requestedAmount()), String.valueOf(command.termMonths()));
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(canonical.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 no disponible", exception);
		}
	}

	/** El conector del buró devuelve {@code Optional<BureauReport>}: vacío = "sin registro". */
	@SuppressWarnings("unchecked")
	private static Optional<BureauReport> bureauReport(SourceCollection collection) {
		return collection.payload(Source.BURO)
			.flatMap(payload -> (Optional<BureauReport>) payload);
	}

}
