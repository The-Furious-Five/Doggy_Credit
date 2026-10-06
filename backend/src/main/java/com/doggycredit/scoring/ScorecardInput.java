package com.doggycredit.scoring;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Datos de entrada del scorecard básico.
 *
 * <p>Los tres datos del buró van vacíos si el buró no respondió. Vacío NO equivale a
 * "sin mora", "0 meses" ni "sin registro" (RN-15: nunca inventar una respuesta).
 *
 * @param maxDaysPastDue12m días de mora máxima en 12 meses según el buró.
 * @param financialSystemSeniorityMonths antigüedad en el sistema financiero según el buró.
 * @param bureauRecordFound si el buró tiene registro del titular.
 */
public record ScorecardInput(OptionalInt maxDaysPastDue12m, int activitySeniorityMonths,
		OptionalInt financialSystemSeniorityMonths, BigDecimal requestedAmount, BigDecimal monthlyIncome,
		int termMonths, Optional<Boolean> bureauRecordFound) {

	public ScorecardInput {
		Objects.requireNonNull(maxDaysPastDue12m, "maxDaysPastDue12m");
		Objects.requireNonNull(financialSystemSeniorityMonths, "financialSystemSeniorityMonths");
		Objects.requireNonNull(requestedAmount, "requestedAmount");
		Objects.requireNonNull(monthlyIncome, "monthlyIncome");
		Objects.requireNonNull(bureauRecordFound, "bureauRecordFound");
	}

}
