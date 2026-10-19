package com.doggycredit.audit;

import java.util.UUID;

/** Puerto público para registrar acciones relevantes en la bitácora append-only. */
public interface AuditTrail {

	void record(UUID tenantId, String action, String resourceType, UUID resourceId);

	/**
	 * C-11 (RF-CON-03): igual, pero indicando el titular afectado y el consentimiento usado,
	 * para el reporte de accesos. Sin sujeto, la acción no aparece al filtrar por titular.
	 * <p>
	 * Es default para que AuditTrail siga siendo una interfaz funcional (los tests la implementan
	 * con una lambda). La implementación real (DatabaseAuditTrail) lo sobrescribe y guarda el sujeto.
	 */
	default void record(UUID tenantId, String action, String resourceType, UUID resourceId, AccessSubject subject) {
		record(tenantId, action, resourceType, resourceId);
	}

}
