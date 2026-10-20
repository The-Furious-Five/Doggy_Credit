package com.doggycredit.audit.internal;

import java.util.Objects;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.doggycredit.audit.AccessSubject;
import com.doggycredit.audit.AuditTrail;

@Service
class DatabaseAuditTrail implements AuditTrail {

	private final AuditEventRepository events;

	DatabaseAuditTrail(AuditEventRepository events) {
		this.events = events;
	}

	@Override
	public void record(UUID tenantId, String action, String resourceType, UUID resourceId) {
		record(tenantId, action, resourceType, resourceId, AccessSubject.NONE);
	}

	@Override
	public void record(UUID tenantId, String action, String resourceType, UUID resourceId, AccessSubject subject) {
		Objects.requireNonNull(tenantId, "tenantId");
		Objects.requireNonNull(subject, "subject");
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String actor = authentication == null ? "system" : authentication.getName();
		String correlationId = Objects.requireNonNullElse(MDC.get("correlationId"), "unavailable");
		events.save(new AuditEvent(tenantId, actor, action, resourceType, resourceId, correlationId, subject));
	}

}
