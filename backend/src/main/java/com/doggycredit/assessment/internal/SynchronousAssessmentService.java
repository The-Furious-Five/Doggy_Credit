package com.doggycredit.assessment.internal;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doggycredit.assessment.AssessmentCommand;
import com.doggycredit.assessment.AssessmentNotFoundException;
import com.doggycredit.assessment.AssessmentOperations;
import com.doggycredit.assessment.AssessmentPage;
import com.doggycredit.assessment.AssessmentRecommendation;
import com.doggycredit.assessment.AssessmentResult;
import com.doggycredit.assessment.AssessmentStatus;
import com.doggycredit.assessment.IdempotencyKeyReusedException;
import com.doggycredit.audit.AuditTrail;
import com.doggycredit.billing.QuotaGuard;
import com.doggycredit.customer.ApplicantProfile;
import com.doggycredit.customer.ApplicantRegistry;
import com.doggycredit.customer.FinancialProfiles;
import com.doggycredit.consent.Source;
import com.doggycredit.tenancy.TenantRegistry;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.integration.BureauReport;
import com.doggycredit.scoring.BasicScoreResult;
import com.doggycredit.scoring.CreditScoring;
import com.doggycredit.scoring.ScorecardInput;
import com.doggycredit.scoring.ScoringFeatures;
import com.doggycredit.shared.DocumentNumber;

@Service
class SynchronousAssessmentService implements AssessmentOperations {

	private final CreditAssessmentRepository assessments;
	private final ApplicantRegistry applicants;
	private final FinancialProfiles profiles;
	private final SourceCollector sources;
	private final CreditScoring scoring;
	private final AuditTrail audit;
	private final QuotaGuard quotaGuard;
	private final TenantRegistry tenantRegistry;
	private final ApplicationEventPublisher events;
	private final PolicyRepository policies;
	private final AssessmentAnalyticsSnapshot analytics;
	private final AssessmentRecommendationStore recommendationStore;

	SynchronousAssessmentService(CreditAssessmentRepository assessments, ApplicantRegistry applicants,
			FinancialProfiles profiles, SourceCollector sources, CreditScoring scoring, AuditTrail audit,
			QuotaGuard quotaGuard, TenantRegistry tenantRegistry, ApplicationEventPublisher events,
			PolicyRepository policies, AssessmentAnalyticsSnapshot analytics,
			AssessmentRecommendationStore recommendationStore) {
		this.assessments = assessments;
		this.applicants = applicants;
		this.profiles = profiles;
		this.sources = sources;
		this.scoring = scoring;
		this.audit = audit;
		this.quotaGuard = quotaGuard;
		this.tenantRegistry = tenantRegistry;
		this.events = events;
		this.policies = policies;
		this.analytics = analytics;
		this.recommendationStore = recommendationStore;
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
			return withRecommendations(replay.get());
		}

		long started = System.nanoTime();
		TenantInfo tenant = tenantRegistry.requireById(TenantId.of(command.tenantId()));
		quotaGuard.requireEvaluationQuota(command.tenantId(), tenant.plan());

		String normalizedDocument = DocumentNumber.normalize(command.document());
		ApplicantProfile applicant = applicants.findRequired(command.tenantId(), normalizedDocument);
		SourceCollection collection = sources.collect(command.tenantId(), command.consentId(), normalizedDocument);
		// B-04: el Perfil 360 se consolida con lo mismo que ve el score, en la misma transacción.
		profiles.consolidate(command.tenantId(), applicant.id(), collection.toSourceResults());
		BasicScoreResult score = scoring.score(command.tenantId(),
				toScorecardInput(command, applicant, collection),
				toScoringFeatures(applicant, bureauReport(collection)));
		AssessmentStatus status = decide(command, collection, score);
		CreditAssessment assessment = new CreditAssessment(command.tenantId(), applicant.id(), command.consentId(),
				command.idempotencyKey(), requestHash, command.purpose(), command.requestedAmount(),
				command.termMonths(), status, score, command.correlationId());
		var snapshot = analytics.capture(command, applicant, collection, score.band(), tenant.moneda(), tenant.plan());
		assessment.captureAnalytics(snapshot,
				java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
		assessments.save(assessment);
		// C-06: la lista completa, en la misma transacción que la evaluación.
		List<AssessmentRecommendation> recommendations = snapshot.recommendations() == null ? null
				: snapshot.recommendations().stream()
					.map(r -> new AssessmentRecommendation(r.productType(), tenant.moneda(), r.amount(), r.reason()))
					.toList();
		if (recommendations != null) {
			recommendationStore.save(command.tenantId(), assessment.id(), recommendations);
		}
		audit.record(command.tenantId(), "ASSESSMENT_CREATED", "CREDIT_ASSESSMENT", assessment.id());
		// D-03: en la misma transacción que la evaluación (outbox); una repetición idempotente no lo publica.
		events.publishEvent(assessment.completedEvent());
		return assessment.toResult(recommendations);
	}

	/** C-06: las recomendaciones guardadas, o null si no se evaluaron (FREE o PARCIAL). */
	private AssessmentResult withRecommendations(CreditAssessment assessment) {
		return assessment.toResult(assessment.recommendationsEvaluated()
				? recommendationStore.load(assessment.tenantId(), assessment.id()) : null);
	}

	/** C-05: las reglas activas del tenant deciden; nunca hay rechazo automático. */
	private AssessmentStatus decide(AssessmentCommand command, SourceCollection collection, BasicScoreResult score) {
		var facts = new PolicyRule.DecisionFacts(score.score(), score.band(), collection.partial(),
				command.requestedAmount(), collection.receivedCount());
		var activeRules = policies.findByTenantIdAndActiveTrue(command.tenantId()).stream().map(Policy::rule).toList();
		return DecisionEngine.decide(facts, activeRules);
	}

	@Override
	@Transactional(readOnly = true)
	public AssessmentPage list(UUID tenantId, UUID applicantId, Instant from, int page, int size) {
		// Sin "desde" se usa el inicio de los tiempos: así la consulta no compara con un null sin tipo.
		var result = assessments.search(tenantId, applicantId, from == null ? Instant.EPOCH : from,
				PageRequest.of(page - 1, size));
		return new AssessmentPage(result.map(CreditAssessment::toSummary).getContent(), result.getTotalElements());
	}

	@Override
	@Transactional(readOnly = true)
	public AssessmentResult get(UUID tenantId, UUID assessmentId) {
		return assessments.findByIdAndTenantId(assessmentId, tenantId)
			.map(this::withRecommendations)
			.orElseThrow(AssessmentNotFoundException::new);
	}

	private ScorecardInput toScorecardInput(AssessmentCommand command, ApplicantProfile applicant,
			SourceCollection collection) {
		BigDecimal requestedAmount = command.requestedAmount() == null ? BigDecimal.ZERO : command.requestedAmount();
		// Los datos del buró son desconocidos solo si el BURÓ falló (RN-15: nunca 0 ni "sin
		// registro" inventados); que caiga otra fuente no los vuelve desconocidos.
		if (collection.failed(Source.BURO)) {
			return new ScorecardInput(OptionalInt.empty(), applicant.activitySeniorityMonths(), OptionalInt.empty(),
					requestedAmount, applicant.monthlyIncome().amount(), command.termMonths(), Optional.empty());
		}
		// Si el buró respondió "sin registro" (informe vacío), es una respuesta válida: sin
		// deudas (mora 0), sin historial (0 meses) y sin registro.
		Optional<BureauReport> bureau = bureauReport(collection);
		int maxDaysPastDue = bureau.stream()
			.flatMap(report -> report.endeudamiento().stream())
			.mapToInt(BureauReport.Endeudamiento::diasMoraMax12m)
			.max().orElse(0);
		int financialSeniority = bureau.map(BureauReport::antiguedadSistemaFinancieroMeses).orElse(0);
		return new ScorecardInput(OptionalInt.of(maxDaysPastDue), applicant.activitySeniorityMonths(),
				OptionalInt.of(financialSeniority), requestedAmount, applicant.monthlyIncome().amount(),
				command.termMonths(), Optional.of(bureau.isPresent()));
	}

	/**
	 * D-02: variables del modelo avanzado con lo que la evaluación tiene hoy
	 * (buró y datos declarados). Banco y billetera llegan con B-03/B-05 (Perfil
	 * 360, B-04); mientras tanto van en null y el modelo las imputa con la media.
	 */
	static ScoringFeatures toScoringFeatures(ApplicantProfile applicant, Optional<BureauReport> bureau) {
		double income = applicant.monthlyIncome().amount().doubleValue();
		Double dti = null;
		Double maxDaysPastDue = null;
		Double creditors = null;
		Double worstRating = null;
		Double seniority = null;
		Double sme = null;
		Double smeSales = null;
		if (bureau.isPresent()) {
			List<BureauReport.Endeudamiento> debts = bureau.get().endeudamiento();
			double installments = debts.stream().mapToDouble(debt -> debt.cuota().amount().doubleValue()).sum();
			dti = income > 0 ? installments / income : null;
			maxDaysPastDue = (double) debts.stream().mapToInt(BureauReport.Endeudamiento::diasMoraMax12m).max().orElse(0);
			creditors = (double) debts.size();
			worstRating = (double) debts.stream().mapToInt(debt -> ratingIndex(debt.calificacion())).max().orElse(0);
			seniority = (double) bureau.get().antiguedadSistemaFinancieroMeses();
			sme = bureau.get().tipo() == BureauReport.TipoTitular.PYME ? 1.0 : 0.0;
			// Sin ventas declaradas todavía: una persona no tiene, una PYME queda imputada.
			smeSales = sme == 0.0 ? 0.0 : null;
		}
		return new ScoringFeatures(dti, maxDaysPastDue, creditors, worstRating, seniority, income, null, null, sme,
				smeSales, null, null, null, null, null);
	}

	/** Escala ASFI A–F del buró a la 0–4 (A–E) del entrenamiento; F se trata como E. */
	private static int ratingIndex(String rating) {
		if (rating == null || rating.isBlank()) {
			return 0;
		}
		return Math.min(4, Math.max(0, Character.toUpperCase(rating.strip().charAt(0)) - 'A'));
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
	static Optional<BureauReport> bureauReport(SourceCollection collection) {
		return collection.payload(Source.BURO)
			.flatMap(payload -> (Optional<BureauReport>) payload);
	}

}
