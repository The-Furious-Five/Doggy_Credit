package com.doggycredit.customer;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/** Datos declarados necesarios para dar de alta a un solicitante. */
public record ApplicantRegistration(String document, String activity, int activitySeniorityMonths,
		BigDecimal monthlyIncome, Currency currency, String creditDestination) {

	public ApplicantRegistration {
		Objects.requireNonNull(document, "document");
		Objects.requireNonNull(activity, "activity");
		Objects.requireNonNull(monthlyIncome, "monthlyIncome");
		Objects.requireNonNull(currency, "currency");
		Objects.requireNonNull(creditDestination, "creditDestination");
		if (monthlyIncome.signum() <= 0) {
			throw new IllegalArgumentException("monthlyIncome must be positive");
		}
	}

}
