package com.doggycredit.assessment.internal;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.audit.AuditTrail;
import com.doggycredit.shared.ErrorCode;

/** C-05: lectura y activación/pausa de las reglas de decisión del tenant. */
@Service
class PolicyService {

	private final PolicyRepository policies;
	private final AuditTrail audit;

	PolicyService(PolicyRepository policies, AuditTrail audit) {
		this.policies = policies;
		this.audit = audit;
	}

	/** En el orden en que se presentan las reglas (el de {@link PolicyRule}). */
	@Transactional(readOnly = true)
	List<Policy> list(UUID tenantId) {
		return policies.findByTenantIdOrderByCode(tenantId).stream()
			.sorted(Comparator.comparing(Policy::rule))
			.toList();
	}

	@Transactional
	Policy setActive(UUID tenantId, UUID policyId, boolean active) {
		Policy policy = policies.findByIdAndTenantId(policyId, tenantId)
			.orElseThrow(() -> new ErrorResponseException(HttpStatus.NOT_FOUND,
					ErrorCode.POLICY_NOT_FOUND.toProblemDetail("La regla no existe o pertenece a otro tenant."), null));
		if (policy.active() != active) {
			policy.setActive(active);
			audit.record(tenantId, active ? "POLICY_ACTIVATED" : "POLICY_PAUSED", "POLICY", policy.id());
		}
		return policy;
	}

}
