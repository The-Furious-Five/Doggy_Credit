package com.doggycredit.assessment;

import java.time.Instant;
import java.util.UUID;

public interface AssessmentOperations {

	AssessmentResult create(AssessmentCommand command);

	AssessmentResult get(UUID tenantId, UUID assessmentId);

	/**
	 * C-09: evaluaciones del tenant, de la más reciente a la más antigua.
	 *
	 * @param applicantId solo las de este solicitante; null para todas.
	 * @param from solo las creadas en o después de este instante; null sin límite.
	 * @param page página desde 1.
	 */
	AssessmentPage list(UUID tenantId, UUID applicantId, Instant from, int page, int size);

}
