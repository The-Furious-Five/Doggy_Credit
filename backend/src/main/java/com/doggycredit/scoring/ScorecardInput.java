package com.doggycredit.scoring;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Datos de entrada del scorecard básico.
 *
 * @param maxDaysPastDue12m días de mora máxima en 12 meses según el buró; vacío si el
 * buró no respondió. Vacío NO equivale a "sin mora" (RN-15: nunca inventar una respuesta).
 */
public record ScorecardInput(OptionalInt maxDaysPastDue12m, int activitySeniorityMonths,
		int financialSystemSeniorityMonths, BigDecimal requestedAmount, BigDecimal monthlyIncome,
		int termMonths, boolean bureauRecordFound) {

	public ScorecardInput {
		Objects.requireNonNull(maxDaysPastDue12m, "maxDaysPastDue12m");
		Objects.requireNonNull(requestedAmount, "requestedAmount");
		Objects.requireNonNull(monthlyIncome, "monthlyIncome");
	}

}
