package com.doggycredit.audit;

import java.util.UUID;

/** Puerto público para registrar acciones relevantes en la bitácora append-only. */
public interface AuditTrail {

	void record(UUID tenantId, String action, String resourceType, UUID resourceId);

}
