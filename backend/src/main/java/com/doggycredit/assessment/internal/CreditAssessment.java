package com.doggycredit.assessment.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.doggycredit.assessment.AssessmentResult;
import com.doggycredit.assessment.AssessmentStatus;
import com.doggycredit.assessment.AssessmentSummary;
import com.doggycredit.assessment.CreditAssessmentCompleted;
import com.doggycredit.scoring.BasicScoreResult;
import com.doggycredit.scoring.ScoreBand;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "assessment", name = "credit_assessments")
class CreditAssessment {

	@Id
	private UUID id;

	// SPIKE-1: discriminador de Hibernate; ver Consent.
	@org.hibernate.annotations.TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "applicant_id", nullable = false)
	private UUID applicantId;

	@Column(name = "consent_id", nullable = false)
	private UUID consentId;

	@Column(name = "idempotency_key", nullable = false, length = 128)
	private String idempotencyKey;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Column(length = 250)
	private String purpose;

	@Column(name = "requested_amount", precision = 19, scale = 2)
	private BigDecimal requestedAmount;

	@Column(name = "term_months")
	private Integer termMonths;

	@Column(nullable = false, length = 24)
	private String status;

	@Column(nullable = false)
	private int score;

	@Column(nullable = false, length = 1)
	private String band;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private String[] reasons;

	@Column(name = "model_version", nullable = false, length = 40)
	private String modelVersion;

	@Column(nullable = false)
	private boolean simulated;

	@Column(name = "correlation_id", nullable = false, length = 128)
	private String correlationId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "need_category", length = 32)
	private String needCategory;

	@Column(name = "recommended_product_type", length = 16)
	private String recommendedProductType;

	@Column(name = "recommended_amount", precision = 19, scale = 2)
	private BigDecimal recommendedAmount;

	@Column(name = "decision_time_ms")
	private Long decisionTimeMs;

	/** C-12 (LOS-113): el vector con el que se calculó el score; null en evaluaciones previas. */
	@Column(name = "scoring_input", columnDefinition = "text")
	private String scoringInput;

	@Column(name = "scoring_input_sha256", length = 64)
	private String scoringInputSha256;

	protected CreditAssessment() {
	}

	CreditAssessment(UUID tenantId, UUID applicantId, UUID consentId, String idempotencyKey,
			String requestHash, String purpose, BigDecimal requestedAmount, Integer termMonths,
			AssessmentStatus status, BasicScoreResult score, String correlationId) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.applicantId = applicantId;
		this.consentId = consentId;
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.purpose = purpose;
		this.requestedAmount = requestedAmount;
		this.termMonths = termMonths;
		this.status = status.name();
		this.score = score.score();
		this.band = score.band().name();
		this.reasons = score.reasons().stream().map(reason -> reason.code()).toArray(String[]::new);
		this.modelVersion = score.modelVersion();
		this.simulated = true;
		this.correlationId = correlationId;
		this.createdAt = Instant.now();
	}

	UUID id() {
		return id;
	}

	void captureAnalytics(AssessmentAnalyticsSnapshot.Snapshot snapshot, long elapsedMs) {
		this.needCategory = snapshot.needCategory();
		this.recommendedProductType = snapshot.productType();
		this.recommendedAmount = snapshot.recommendedAmount();
		this.decisionTimeMs = elapsedMs;
	}

	/** Se guarda en el mismo INSERT que la evaluación; después la tabla no admite UPDATE. */
	void captureScoringInput(ScoringInput input) {
		this.scoringInput = input.toJson();
		this.scoringInputSha256 = ScoringInput.sha256(this.scoringInput);
	}

	Optional<String> scoringInput() {
		return Optional.ofNullable(scoringInput);
	}

	String scoringInputSha256() {
		return scoringInputSha256;
	}

	int score() {
		return score;
	}

	String band() {
		return band;
	}

	List<String> reasons() {
		return List.of(reasons);
	}

	String modelVersion() {
		return modelVersion;
	}

	String requestHash() {
		return requestHash;
	}

	/** C-10: lo que la bandeja de revisión manual muestra de esta evaluación. */
	ReviewCase reviewCase() {
		return new ReviewCase(id, applicantId, AssessmentStatus.valueOf(status), score, ScoreBand.valueOf(band),
				List.of(reasons), requestedAmount, termMonths, purpose, createdAt);
	}

	record ReviewCase(UUID id, UUID applicantId, AssessmentStatus status, int score, ScoreBand band,
			List<String> reasons, BigDecimal requestedAmount, Integer termMonths, String purpose, Instant createdAt) {
	}

	CreditAssessmentCompleted completedEvent() {
		// El evento v1 no conoce SOLICITAR_DATOS: se publica el estado equivalente.
		return new CreditAssessmentCompleted(UUID.randomUUID(), Instant.now(), tenantId, id, applicantId,
				AssessmentStatus.valueOf(status).published(), score, ScoreBand.valueOf(band), List.of(reasons), modelVersion,
				simulated, correlationId);
	}

	AssessmentSummary toSummary() {
		return new AssessmentSummary(id, applicantId, AssessmentStatus.valueOf(status), score, ScoreBand.valueOf(band),
				requestedAmount, termMonths, createdAt);
	}

	AssessmentResult toResult() {
		return new AssessmentResult(id, AssessmentStatus.valueOf(status), score, ScoreBand.valueOf(band),
				List.of(reasons), modelVersion, simulated, correlationId);
	}

}
