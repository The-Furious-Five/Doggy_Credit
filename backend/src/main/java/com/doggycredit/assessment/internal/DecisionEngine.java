package com.doggycredit.assessment.internal;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.doggycredit.assessment.AssessmentStatus;
import com.doggycredit.assessment.internal.PolicyRule.DecisionFacts;

/**
 * C-05: aplica las reglas activas del tenant y decide el estado de la evaluación.
 *
 * <ul>
 * <li>PARCIAL (falló una fuente) nunca se preaprueba: queda PARCIAL para revisión humana (RN-15).</li>
 * <li>Gana la regla más prudente: SOLICITAR_DATOS, luego REVISION_MANUAL, luego PREAPROBADO.</li>
 * <li>Sin ninguna regla que aplique, REVISION_MANUAL.</li>
 * <li>Nunca hay rechazo automático: ningún camino devuelve otro estado.</li>
 * </ul>
 */
final class DecisionEngine {

	/** Los únicos estados que puede decidir el motor. */
	static final Set<AssessmentStatus> POSSIBLE_DECISIONS = EnumSet.of(AssessmentStatus.PARCIAL,
			AssessmentStatus.PREAPROBADO, AssessmentStatus.REVISION_MANUAL, AssessmentStatus.SOLICITAR_DATOS);

	private DecisionEngine() {
	}

	static AssessmentStatus decide(DecisionFacts facts, Collection<PolicyRule> activeRules) {
		if (facts.partial()) {
			return AssessmentStatus.PARCIAL;
		}
		Set<AssessmentStatus> effects = activeRules.stream()
			.filter(rule -> rule.matches(facts))
			.map(PolicyRule::effect)
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(AssessmentStatus.class)));
		if (effects.contains(AssessmentStatus.SOLICITAR_DATOS)) {
			return AssessmentStatus.SOLICITAR_DATOS;
		}
		if (effects.contains(AssessmentStatus.REVISION_MANUAL)) {
			return AssessmentStatus.REVISION_MANUAL;
		}
		if (effects.contains(AssessmentStatus.PREAPROBADO)) {
			return AssessmentStatus.PREAPROBADO;
		}
		return AssessmentStatus.REVISION_MANUAL;
	}

}
