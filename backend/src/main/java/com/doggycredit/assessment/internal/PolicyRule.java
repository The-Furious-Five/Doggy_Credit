package com.doggycredit.assessment.internal;

import java.math.BigDecimal;
import java.util.function.Predicate;

import com.doggycredit.assessment.AssessmentStatus;
import com.doggycredit.scoring.ScoreBand;

/**
 * C-05: las reglas de decisión son fijas (el editor está fuera de alcance); cada tenant
 * solo las activa o pausa en {@code assessment.policies}. Ninguna tiene como efecto un
 * rechazo: los rechazos siempre pasan por una persona.
 */
enum PolicyRule {

	PREAPROBACION_SCORE(AssessmentStatus.PREAPROBADO, facts -> facts.score() >= 650),

	MONTO_ALTO(AssessmentStatus.REVISION_MANUAL, facts -> facts.requestedAmount() != null
			&& facts.requestedAmount().compareTo(new BigDecimal("200000")) > 0),

	BANDA_BAJA(AssessmentStatus.REVISION_MANUAL,
			facts -> facts.band() == ScoreBand.D || facts.band() == ScoreBand.E),

	DATOS_INSUFICIENTES(AssessmentStatus.SOLICITAR_DATOS, facts -> facts.sourcesReceived() < 2);

	private final AssessmentStatus effect;
	private final Predicate<DecisionFacts> condition;

	PolicyRule(AssessmentStatus effect, Predicate<DecisionFacts> condition) {
		this.effect = effect;
		this.condition = condition;
	}

	AssessmentStatus effect() {
		return effect;
	}

	boolean matches(DecisionFacts facts) {
		return condition.test(facts);
	}

	/** Lo que las reglas miran de una evaluación ya puntuada. */
	record DecisionFacts(int score, ScoreBand band, boolean partial, BigDecimal requestedAmount,
			int sourcesReceived) {
	}

}
