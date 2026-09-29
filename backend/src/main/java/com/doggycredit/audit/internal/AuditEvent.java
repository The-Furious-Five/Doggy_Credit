package com.doggycredit.audit.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "audit", name = "access_log")
class AuditEvent {

	@Id
	private UUID id;

	// SPIKE-1: discriminador de Hibernate; ver Consent.
	@org.hibernate.annotations.TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(nullable = false, length = 255)
	private String actor;

	@Column(nullable = false, length = 100)
	private String action;

	@Column(name = "resource_type", nullable = false, length = 100)
	private String resourceType;

	@Column(name = "resource_id")
	private UUID resourceId;

	@Column(name = "correlation_id", nullable = false, length = 128)
	private String correlationId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AuditEvent() {
	}

	AuditEvent(UUID tenantId, String actor, String action, String resourceType, UUID resourceId,
			String correlationId) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.actor = actor;
		this.action = action;
		this.resourceType = resourceType;
		this.resourceId = resourceId;
		this.correlationId = correlationId;
		this.createdAt = Instant.now();
	}

}
