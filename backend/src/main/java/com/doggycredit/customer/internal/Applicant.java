package com.doggycredit.customer.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "customer", name = "applicants")
class Applicant {

	@Id
	private UUID id;

	// SPIKE-1: discriminador de Hibernate; ver Consent.
	@org.hibernate.annotations.TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "document_ref", nullable = false, length = 64)
	private String documentRef;

	@Column(name = "document_ciphertext", nullable = false)
	private String documentCiphertext;

	@Column(nullable = false, length = 120)
	private String activity;

	@Column(name = "activity_seniority_months", nullable = false)
	private int activitySeniorityMonths;

	@Column(name = "monthly_income", nullable = false, precision = 19, scale = 2)
	private BigDecimal monthlyIncome;

	@Column(name = "income_currency", nullable = false, length = 3)
	private String incomeCurrency;

	@Column(name = "credit_destination", nullable = false, length = 250)
	private String creditDestination;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Applicant() {
	}

	Applicant(UUID tenantId, String documentRef, String documentCiphertext, String activity,
			int activitySeniorityMonths, BigDecimal monthlyIncome, Currency incomeCurrency,
			String creditDestination) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.documentRef = documentRef;
		this.documentCiphertext = documentCiphertext;
		this.activity = activity;
		this.activitySeniorityMonths = activitySeniorityMonths;
		this.monthlyIncome = monthlyIncome;
		this.incomeCurrency = incomeCurrency.getCurrencyCode();
		this.creditDestination = creditDestination;
		this.createdAt = Instant.now();
	}

	UUID id() {
		return id;
	}

	String documentRef() {
		return documentRef;
	}

	String documentCiphertext() {
		return documentCiphertext;
	}

	String activity() {
		return activity;
	}

	int activitySeniorityMonths() {
		return activitySeniorityMonths;
	}

	BigDecimal monthlyIncome() {
		return monthlyIncome;
	}

	Currency incomeCurrency() {
		return Currency.getInstance(incomeCurrency);
	}

	String creditDestination() {
		return creditDestination;
	}

	Instant createdAt() {
		return createdAt;
	}

}
