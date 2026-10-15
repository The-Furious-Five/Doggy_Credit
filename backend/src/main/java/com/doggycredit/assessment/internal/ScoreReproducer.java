package com.doggycredit.assessment.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.doggycredit.assessment.AssessmentNotFoundException;
import com.doggycredit.scoring.BasicScoreResult;
import com.doggycredit.scoring.CreditScoring;
import com.doggycredit.scoring.ScoreReason;

/**
 * C-12 (LOS-113), RF-SCO-04 / FF-10: recalcula el score de una evaluación con el vector
 * que se guardó y la misma versión de modelo, y dice si da exactamente lo mismo (score,
 * banda y razones en el mismo orden).
 *
 * <p>Antes de recalcular comprueba el SHA-256 del vector: si alguien lo alteró, no se
 * recalcula nada. Nunca sustituye una versión de modelo que ya no está cargada por otra.
 */
@Component
class ScoreReproducer {

	enum Outcome {
		/** Mismo score, misma banda y mismas razones en el mismo orden. */
		IDENTICAL,
		/** Se recalculó con la misma versión y no coincide. */
		DIFFERENT,
		/** Evaluación anterior a C-12: no se guardó con qué datos se calculó. */
		NO_INPUT,
		/** El vector guardado no coincide con su hash. */
		INPUT_TAMPERED,
		/** La versión que produjo el score ya no está cargada en esta instancia. */
		MODEL_VERSION_UNAVAILABLE
	}

	/** @param recalculated el resultado del recálculo; vacío si no se pudo recalcular. */
	record Reproduction(Outcome outcome, Optional<BasicScoreResult> recalculated) {
	}

	private final CreditAssessmentRepository assessments;

	private final CreditScoring scoring;

	ScoreReproducer(CreditAssessmentRepository assessments, CreditScoring scoring) {
		this.assessments = assessments;
		this.scoring = scoring;
	}

	@Transactional(readOnly = true)
	Reproduction reproduce(UUID tenantId, UUID assessmentId) {
		CreditAssessment assessment = assessments.findByIdAndTenantId(assessmentId, tenantId)
			.orElseThrow(AssessmentNotFoundException::new);
		Optional<String> json = assessment.scoringInput();
		if (json.isEmpty()) {
			return new Reproduction(Outcome.NO_INPUT, Optional.empty());
		}
		if (!ScoringInput.sha256(json.get()).equals(assessment.scoringInputSha256())) {
			return new Reproduction(Outcome.INPUT_TAMPERED, Optional.empty());
		}
		ScoringInput input = ScoringInput.fromJson(json.get());
		Optional<BasicScoreResult> recalculated = scoring.rescore(assessment.modelVersion(), input.basicInput(),
				input.features());
		if (recalculated.isEmpty()) {
			return new Reproduction(Outcome.MODEL_VERSION_UNAVAILABLE, Optional.empty());
		}
		BasicScoreResult result = recalculated.get();
		boolean identical = result.score() == assessment.score() && result.band().name().equals(assessment.band())
				&& result.reasons().stream().map(ScoreReason::code).toList().equals(assessment.reasons());
		return new Reproduction(identical ? Outcome.IDENTICAL : Outcome.DIFFERENT, recalculated);
	}

}
