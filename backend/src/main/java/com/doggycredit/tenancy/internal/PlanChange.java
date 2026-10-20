package com.doggycredit.tenancy.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * D-14: un cambio de plan efectivo (migración V2026_10_20_1200), solo inserción. La auditoría dice
 * que hubo un cambio; esta fila dice desde qué plan y hacia cuál, para contar conversiones.
 */
@Entity
@Table(schema = "tenancy", name = "plan_changes")
class PlanChange {

	@Id
	private UUID id;

	// SPIKE-1: discriminador de Hibernate; ver Consent.
	@org.hibernate.annotations.TenantId
	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	@Column(name = "plan_anterior", nullable = false, length = 20, updatable = false)
	private String planAnterior;

	@Column(name = "plan_nuevo", nullable = false, length = 20, updatable = false)
	private String planNuevo;

	@Column(name = "cambiado_en", nullable = false, updatable = false)
	private Instant cambiadoEn;

	protected PlanChange() {
	}

	PlanChange(String planAnterior, String planNuevo, Instant cambiadoEn) {
		this.id = UUID.randomUUID();
		this.planAnterior = planAnterior;
		this.planNuevo = planNuevo;
		this.cambiadoEn = cambiadoEn;
	}

	String planAnterior() {
		return planAnterior;
	}

	String planNuevo() {
		return planNuevo;
	}
}
