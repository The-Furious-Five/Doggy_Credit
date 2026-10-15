package com.doggycredit.audit;

import java.util.UUID;

/** Puerto público para registrar acciones relevantes en la bitácora append-only. */
public interface AuditTrail {

	void record(UUID tenantId, String action, String resourceType, UUID resourceId);

	/**
	 * C-11 (RF-CON-03): igual, pero indicando el titular afectado y el consentimiento usado,
	 * para el reporte de accesos. Sin sujeto, la acción no aparece al filtrar por titular.
	 */
	void record(UUID tenantId, String action, String resourceType, UUID resourceId, AccessSubject subject);

}
