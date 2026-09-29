package com.doggycredit.consent.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Consentimiento del titular (esquema {@code consent}, migración
 * V2026_09_28_1000). Guarda fuentes, finalidad, vigencia y evidencia: quién
 * lo otorgó ({@code sub} del JWT), cuándo, y el SHA-256 del payload canónico.
 * EXPIRADO no se persiste: se evalúa al leer comparando {@code expiraEn}.
 */
@Entity
@Table(schema = "consent", name = "consents")
public class Consent {

	@Id
	private UUID id;

	/**
	 * SPIKE-1: discriminador de Hibernate. Se asigna solo al insertar, desde
	 * {@code TenantIdentifierResolver}; no lo toca el constructor pese a
	 * recibir {@code tenantId} (queda como defensa redundante: si algún día
	 * este valor y el del JWT difirieran, Hibernate impone el del contexto).
	 */
	@TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "titular_documento", nullable = false, length = 64)
	private String titularDocumento;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private String[] fuentes;

	@Column(nullable = false, length = 500)
	private String finalidad;

	@Column(name = "vigencia_dias", nullable = false)
	private int vigenciaDias;

	@Column(name = "otorgado_en", nullable = false)
	private Instant otorgadoEn;

	@Column(name = "expira_en", nullable = false)
	private Instant expiraEn;

	/** ACTIVO o REVOCADO (la revocación es otra historia). */
	@Column(nullable = false, length = 16)
	private String estado;

	@Column(name = "otorgado_por", nullable = false)
	private String otorgadoPor;

	@Column(name = "payload_sha256", nullable = false, length = 64)
	private String payloadSha256;

	protected Consent() {
	}

	Consent(UUID tenantId, String titularDocumento, String[] fuentes, String finalidad, int vigenciaDias,
			Instant otorgadoEn, String otorgadoPor, String payloadSha256) {
		this.id = UUID.randomUUID();
		this.tenantId = tenantId;
		this.titularDocumento = titularDocumento;
		this.fuentes = fuentes;
		this.finalidad = finalidad;
		this.vigenciaDias = vigenciaDias;
		this.otorgadoEn = otorgadoEn;
		this.expiraEn = otorgadoEn.plus(Duration.ofDays(vigenciaDias));
		this.estado = "ACTIVO";
		this.otorgadoPor = otorgadoPor;
		this.payloadSha256 = payloadSha256;
	}

	String estadoAt(Instant now) {
		return "ACTIVO".equals(estado) && !now.isBefore(expiraEn) ? "EXPIRADO" : estado;
	}

	UUID getId() {
		return id;
	}

	String getTitularDocumento() {
		return titularDocumento;
	}

	String[] getFuentes() {
		return fuentes;
	}

	String getFinalidad() {
		return finalidad;
	}

	int getVigenciaDias() {
		return vigenciaDias;
	}

	Instant getOtorgadoEn() {
		return otorgadoEn;
	}

}
