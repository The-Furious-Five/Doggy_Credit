package com.doggycredit.assessment;

import java.util.UUID;

public interface AssessmentOperations {

	AssessmentResult create(AssessmentCommand command);

	AssessmentResult get(UUID tenantId, UUID assessmentId);

}
