package com.doggycredit.assessment.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** C-05: una regla de decisión del tenant (la crea la semilla; la app solo la activa o pausa). */
@Entity
@Table(schema = "assessment", name = "policies")
class Policy {

	@Id
	private UUID id;

	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(nullable = false, length = 40)
	private String code;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(name = "condition_text", nullable = false, length = 120)
	private String conditionText;

	@Column(nullable = false, length = 24)
	private String effect;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected Policy() {
	}

	UUID id() {
		return id;
	}

	String code() {
		return code;
	}

	String name() {
		return name;
	}

	String conditionText() {
		return conditionText;
	}

	String effect() {
		return effect;
	}

	boolean active() {
		return active;
	}

	PolicyRule rule() {
		return PolicyRule.valueOf(code);
	}

	void setActive(boolean active) {
		this.active = active;
		this.updatedAt = Instant.now();
	}

}
