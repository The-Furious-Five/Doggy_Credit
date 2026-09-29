package com.doggycredit.assessment;

import java.math.BigDecimal;
import java.util.UUID;

public record AssessmentCommand(UUID tenantId, String document, UUID consentId, String purpose,
		BigDecimal requestedAmount, int termMonths, String idempotencyKey, String correlationId) {
}
