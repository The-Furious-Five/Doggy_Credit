package com.doggycredit.assessment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.doggycredit.scoring.ScoreBand;

/** C-09: una evaluación en un listado. {@code requestedAmount} y {@code termMonths} pueden faltar. */
public record AssessmentSummary(UUID id, UUID applicantId, AssessmentStatus status, int score, ScoreBand band,
		BigDecimal requestedAmount, Integer termMonths, Instant createdAt) {
}
