package com.doggycredit.compliance.internal;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.RestController;

import com.doggycredit.audit.AccessLog;
import com.doggycredit.audit.AccessSubject;
import com.doggycredit.audit.AuditTrail;
import com.doggycredit.consent.ConsentSummaries;
import com.doggycredit.customer.ApplicantRegistry;
import com.doggycredit.shared.ErrorCode;
import com.doggycredit.shared.RoleGuard;
import com.doggycredit.shared.contract.api.ReporteAccesosApi;
import com.doggycredit.shared.contract.model.AutorizacionAcceso;
import com.doggycredit.shared.contract.model.RegistroAcceso;
import com.doggycredit.shared.contract.model.ReporteAccesos;

/**
 * C-11 (LOS-112), RF-CON-03: GET /auditoria/accesos. Quién accedió a los datos de un
 * titular y con qué autorización, filtrado por titular, por correlation-id o por ambos.
 * Solo TENANT_ADMIN. La respuesta no lleva datos personales: el titular va por id.
 */
@RestController
class AccessReportController implements ReporteAccesosApi {

	static final String REQUIRED_ROLE = "TENANT_ADMIN";

	private final AccessLog log;

	private final ConsentSummaries consents;

	private final ApplicantRegistry applicants;

	private final AuditTrail audit;

	AccessReportController(AccessLog log, ConsentSummaries consents, ApplicantRegistry applicants, AuditTrail audit) {
		this.log = log;
		this.consents = consents;
		this.applicants = applicants;
		this.audit = audit;
	}

	@Override
	public ResponseEntity<ReporteAccesos> obtenerReporteAccesos(UUID solicitanteId, String correlationId,
			OffsetDateTime desde, OffsetDateTime hasta, Long cursor, Integer tamano) {
		JwtAuthenticationToken jwt = RoleGuard.requireJwt(REQUIRED_ROLE);
		UUID tenantId = UUID.fromString(jwt.getToken().getClaimAsString("tenant_id"));
		if (solicitanteId == null && (correlationId == null || correlationId.isBlank())) {
			throw new ErrorResponseException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR
				.toProblemDetail("Indica el titular (solicitanteId), el correlationId o ambos."), null);
		}
		if (solicitanteId != null) {
			// 404 UNKNOWN_APPLICANT si no existe o es de otro tenant.
			applicants.findById(tenantId, solicitanteId);
		}
		AccessLog.Page page = log.find(solicitanteId, correlationId, instant(desde), instant(hasta), cursor,
				tamano == null ? 50 : tamano);

		// Generar el reporte también es un acceso a los datos del titular (y queda después de
		// leer, para no aparecer en su propia primera página).
		if (solicitanteId != null) {
			audit.record(tenantId, "ACCESS_REPORT_GENERATED", "APPLICANT", solicitanteId,
					AccessSubject.applicant(solicitanteId));
		}
		else {
			audit.record(tenantId, "ACCESS_REPORT_GENERATED", "ACCESS_REPORT", null);
		}

		ReporteAccesos body = new ReporteAccesos()
			.solicitanteId(solicitanteId)
			.correlationId(correlationId)
			.generadoEn(OffsetDateTime.now(ZoneOffset.UTC))
			.items(page.items().stream().map(AccessReportController::toContract).toList())
			.autorizaciones(authorizations(tenantId, page.items()));
		if (page.hasMore()) {
			body.siguienteCursor(page.items().getLast().chainSeq());
		}
		return ResponseEntity.ok(body);
	}

	/** Los consentimientos que aparecen en la página, una vez cada uno, con su titular. */
	private List<AutorizacionAcceso> authorizations(UUID tenantId, List<AccessLog.Entry> entries) {
		Map<UUID, UUID> applicantByConsent = new LinkedHashMap<>();
		for (AccessLog.Entry entry : entries) {
			if (entry.consentId() != null && entry.applicantId() != null) {
				applicantByConsent.putIfAbsent(entry.consentId(), entry.applicantId());
			}
		}
		if (applicantByConsent.isEmpty()) {
			return List.of();
		}
		return consents.findAll(tenantId, applicantByConsent.keySet()).stream()
			.map(summary -> new AutorizacionAcceso()
				.consentimientoId(summary.id())
				.solicitanteId(applicantByConsent.get(summary.id()))
				.fuentes(summary.sources().stream()
					.map(source -> AutorizacionAcceso.FuentesEnum.fromValue(source.name()))
					.toList())
				.finalidad(summary.purpose())
				.estado(AutorizacionAcceso.EstadoEnum.fromValue(summary.status()))
				.creadoEn(summary.grantedAt().atOffset(ZoneOffset.UTC))
				.expiraEn(summary.expiresAt().atOffset(ZoneOffset.UTC))
				.revocadoEn(summary.revokedAt() == null ? null : summary.revokedAt().atOffset(ZoneOffset.UTC)))
			.toList();
	}

	private static RegistroAcceso toContract(AccessLog.Entry entry) {
		return new RegistroAcceso()
			.id(entry.id())
			.secuencia(entry.chainSeq())
			.actor(entry.actor())
			.accion(entry.action())
			.tipoRecurso(entry.resourceType())
			.idRecurso(entry.resourceId())
			.correlationId(entry.correlationId())
			.creadoEn(entry.createdAt().atOffset(ZoneOffset.UTC))
			.hash(entry.rowHash())
			.solicitanteId(entry.applicantId())
			.consentimientoId(entry.consentId());
	}

	private static Instant instant(OffsetDateTime value) {
		return value == null ? null : value.toInstant();
	}

}
