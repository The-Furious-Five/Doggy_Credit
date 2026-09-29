package com.doggycredit.scoring;

import java.math.BigDecimal;
import java.util.Objects;

public record ScorecardInput(int maxDaysPastDue12m, int activitySeniorityMonths,
		int financialSystemSeniorityMonths, BigDecimal requestedAmount, BigDecimal monthlyIncome,
		int termMonths, boolean bureauRecordFound) {

	public ScorecardInput {
		Objects.requireNonNull(requestedAmount, "requestedAmount");
		Objects.requireNonNull(monthlyIncome, "monthlyIncome");
	}

}
