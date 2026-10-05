package com.doggycredit.customer;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/** Datos declarados necesarios para dar de alta a un solicitante. */
public record ApplicantRegistration(String document, String activity, int activitySeniorityMonths,
		BigDecimal monthlyIncome, Currency currency, String creditDestination, ApplicantSegment segment) {

	public ApplicantRegistration(String document, String activity, int activitySeniorityMonths,
			BigDecimal monthlyIncome, Currency currency, String creditDestination) {
		this(document, activity, activitySeniorityMonths, monthlyIncome, currency, creditDestination, null);
	}

	public ApplicantRegistration {
		Objects.requireNonNull(document, "document");
		Objects.requireNonNull(activity, "activity");
		Objects.requireNonNull(monthlyIncome, "monthlyIncome");
		Objects.requireNonNull(currency, "currency");
		Objects.requireNonNull(creditDestination, "creditDestination");
		segment = Objects.requireNonNullElse(segment, ApplicantSegment.PERSONA);
		if (monthlyIncome.signum() <= 0) {
			throw new IllegalArgumentException("monthlyIncome must be positive");
		}
	}

}
