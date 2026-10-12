package com.doggycredit.integration.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** B-06: activación de un conector para un tenant. Sin fila = estado por defecto (solo el buró activo). */
@Entity
@Table(schema = "integration", name = "tenant_integrations")
class TenantIntegration {

	@Id
	private UUID id;

	// SPIKE-1: discriminador de Hibernate; ver Consent.
	@org.hibernate.annotations.TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(nullable = false, length = 16)
	private String source;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "updated_by", nullable = false)
	private String updatedBy;

	protected TenantIntegration() {
	}

	TenantIntegration(UUID tenantId, String source) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.source = source;
	}

	void set(boolean active, String by) {
		this.active = active;
		this.updatedAt = Instant.now();
		this.updatedBy = by;
	}

	String source() {
		return source;
	}

	boolean active() {
		return active;
	}

	/** A-12: la apagó una bajada de plan y nadie la tocó después. */
	boolean suspendedByDowngrade() {
		return !active && IntegrationService.SYSTEM_DOWNGRADE.equals(updatedBy);
	}

}
