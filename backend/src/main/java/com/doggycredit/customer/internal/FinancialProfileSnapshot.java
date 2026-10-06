package com.doggycredit.customer.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Snapshot inmutable del Perfil 360 (B-04). Todo el contenido financiero va en
 * {@code payloadCiphertext} (AES-GCM con la DEK del tenant); en claro quedan solo
 * claves, el hash del canónico y la fecha.
 */
@Entity
@Table(schema = "customer", name = "financial_profiles")
class FinancialProfileSnapshot {

	@Id
	private UUID id;

	// SPIKE-1: discriminador de Hibernate; ver Consent.
	@org.hibernate.annotations.TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "applicant_id", nullable = false)
	private UUID applicantId;

	@Column(name = "content_sha256", nullable = false, length = 64)
	private String contentSha256;

	@Column(name = "payload_ciphertext", nullable = false)
	private String payloadCiphertext;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected FinancialProfileSnapshot() {
	}

	FinancialProfileSnapshot(UUID id, UUID tenantId, UUID applicantId, String contentSha256, String payloadCiphertext,
			Instant createdAt) {
		this.id = id;
		this.tenantId = tenantId;
		this.applicantId = applicantId;
		this.contentSha256 = contentSha256;
		this.payloadCiphertext = payloadCiphertext;
		this.createdAt = createdAt;
	}

	String payloadCiphertext() {
		return payloadCiphertext;
	}

}
