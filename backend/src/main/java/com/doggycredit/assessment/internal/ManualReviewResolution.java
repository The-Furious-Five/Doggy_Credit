package com.doggycredit.assessment.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * C-10 (LOS-111), RF-DEC-03: la resolución de una persona sobre un caso de la bandeja de
 * revisión manual (override). Inmutable: la app solo tiene SELECT, INSERT sobre la tabla.
 */
@Entity
@Table(schema = "assessment", name = "manual_review_resolutions")
class ManualReviewResolution {

	enum Decision {
		APROBADO, RECHAZADO
	}

	@Id
	private UUID id;

	// SPIKE-1: discriminador de Hibernate; ver Consent.
	@org.hibernate.annotations.TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "assessment_id", nullable = false)
	private UUID assessmentId;

	@Column(nullable = false, length = 16)
	private String decision;

	@Column(name = "product_id")
	private UUID productId;

	/** C-14 (LOS-130): snapshot del tipo del producto aprobado; null en rechazos y resoluciones previas. */
	@Column(name = "product_type", length = 16)
	private String productType;

	@Column(nullable = false, length = 1000)
	private String justification;

	@Column(name = "resolved_by", nullable = false, length = 255)
	private String resolvedBy;

	@Column(name = "resolved_at", nullable = false)
	private Instant resolvedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(nullable = false)
	private long version;

	protected ManualReviewResolution() {
	}

	ManualReviewResolution(UUID tenantId, UUID assessmentId, Decision decision, UUID productId, String productType,
			String justification, String resolvedBy) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.assessmentId = assessmentId;
		this.decision = decision.name();
		this.productId = productId;
		this.productType = productType;
		this.justification = justification;
		this.resolvedBy = resolvedBy;
		this.resolvedAt = Instant.now();
		this.createdAt = this.resolvedAt;
		this.updatedAt = this.resolvedAt;
	}

	UUID assessmentId() {
		return assessmentId;
	}

	Decision decision() {
		return Decision.valueOf(decision);
	}

	UUID productId() {
		return productId;
	}

	String productType() {
		return productType;
	}

	String justification() {
		return justification;
	}

	String resolvedBy() {
		return resolvedBy;
	}

	Instant resolvedAt() {
		return resolvedAt;
	}

}
