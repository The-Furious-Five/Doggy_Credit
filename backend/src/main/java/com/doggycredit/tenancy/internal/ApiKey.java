package com.doggycredit.tenancy.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * D-09: clave de API del tenant (esquema {@code tenancy}, migración
 * V2026_10_05_1200). La credencial vive en Keycloak ({@code keycloakId});
 * aquí solo queda lo que se muestra en la consola y lo necesario para revocarla.
 */
@Entity
@Table(schema = "tenancy", name = "api_keys")
class ApiKey {

	enum Environment {
		SANDBOX, PRODUCCION
	}

	@Id
	private UUID id;

	// SPIKE-1: discriminador de Hibernate; ver Consent.
	@org.hibernate.annotations.TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(nullable = false, length = 80)
	private String nombre;

	@Column(name = "client_id", nullable = false, length = 64)
	private String clientId;

	@Column(name = "keycloak_id", nullable = false)
	private UUID keycloakId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Environment entorno;

	@Column(name = "sufijo_secreto", nullable = false, length = 4)
	private String sufijoSecreto;

	@Column(name = "creada_por", nullable = false)
	private String creadaPor;

	@Column(name = "creada_en", nullable = false)
	private Instant creadaEn;

	@Column(name = "revocada_en")
	private Instant revocadaEn;

	protected ApiKey() {
	}

	ApiKey(UUID tenantId, String nombre, String clientId, UUID keycloakId, Environment entorno,
			String sufijoSecreto, String creadaPor, Instant creadaEn) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.nombre = nombre;
		this.clientId = clientId;
		this.keycloakId = keycloakId;
		this.entorno = entorno;
		this.sufijoSecreto = sufijoSecreto;
		this.creadaPor = creadaPor;
		this.creadaEn = creadaEn;
	}

	void revoke(Instant when) {
		this.revocadaEn = when;
	}

	UUID id() {
		return id;
	}

	String nombre() {
		return nombre;
	}

	String clientId() {
		return clientId;
	}

	UUID keycloakId() {
		return keycloakId;
	}

	Environment entorno() {
		return entorno;
	}

	String sufijoSecreto() {
		return sufijoSecreto;
	}

	Instant creadaEn() {
		return creadaEn;
	}

}
