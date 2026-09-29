package com.doggycredit.assessment;

import java.util.List;
import java.util.UUID;

import com.doggycredit.scoring.ScoreBand;

public record AssessmentResult(UUID id, AssessmentStatus status, int score, ScoreBand band,
		List<String> reasons, String modelVersion, boolean simulated, String correlationId) {
}
