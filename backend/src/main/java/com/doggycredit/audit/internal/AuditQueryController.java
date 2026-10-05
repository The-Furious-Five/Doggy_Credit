package com.doggycredit.audit.internal;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.RestController;

import com.doggycredit.shared.ErrorCode;
import com.doggycredit.shared.contract.api.AuditoriaApi;
import com.doggycredit.shared.contract.model.PaginaAuditoria;
import com.doggycredit.shared.contract.model.RegistroAuditoria;

/**
 * C-04 (LOS-84): consulta de la bitácora del tenant. Ruta y DTOs vienen del contrato
 * OpenAPI. El tenant no se lee de ningún parámetro: lo aplican @TenantId y la RLS desde
 * el JWT. Solo TENANT_ADMIN (informe de arquitectura: el administrador del tenant
 * concentra política, usuarios y auditoría, DEBT-014).
 */
@RestController
class AuditQueryController implements AuditoriaApi {

	static final String REQUIRED_ROLE = "TENANT_ADMIN";

	private final AuditQueryService audit;

	AuditQueryController(AuditQueryService audit) {
		this.audit = audit;
	}

	@Override
	public ResponseEntity<PaginaAuditoria> listarAuditoria(OffsetDateTime desde, OffsetDateTime hasta, Long cursor,
			Integer tamano) {
		requireRole(REQUIRED_ROLE);
		AuditQueryService.Page page = audit.page(desde == null ? null : desde.toInstant(),
				hasta == null ? null : hasta.toInstant(), cursor, tamano == null ? 20 : tamano);

		PaginaAuditoria body = new PaginaAuditoria()
			.items(page.items().stream().map(AuditQueryController::toDto).toList());
		if (page.hasMore()) {
			body.siguienteCursor(page.items().getLast().chainSeq());
		}
		return ResponseEntity.ok(body);
	}

	private static RegistroAuditoria toDto(AuditEvent event) {
		return new RegistroAuditoria().id(event.id())
			.secuencia(event.chainSeq())
			.actor(event.actor())
			.accion(event.action())
			.tipoRecurso(event.resourceType())
			.idRecurso(event.resourceId())
			.correlationId(event.correlationId())
			.creadoEn(event.createdAt().atOffset(ZoneOffset.UTC))
			.hash(event.rowHash());
	}

	/**
	 * Autorización por rol dentro del endpoint, con su propio código de error. No se usa
	 * la cadena de Spring Security porque su AccessDeniedHandler responde siempre
	 * INVALID_TENANT_CLAIM (ver ProblemDetailAccessDeniedHandler), que aquí engañaría.
	 */
	private static void requireRole(String role) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		boolean granted = authentication != null && authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.anyMatch(("ROLE_" + role)::equals);
		if (!granted) {
			throw new ErrorResponseException(HttpStatus.FORBIDDEN,
					ErrorCode.INSUFFICIENT_ROLE.toProblemDetail("La operación requiere el rol " + role + "."), null);
		}
	}

}
