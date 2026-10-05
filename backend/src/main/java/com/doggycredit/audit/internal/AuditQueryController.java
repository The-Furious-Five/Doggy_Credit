package com.doggycredit.audit.internal;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.doggycredit.shared.RoleGuard;
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
		RoleGuard.require(REQUIRED_ROLE);
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

}
