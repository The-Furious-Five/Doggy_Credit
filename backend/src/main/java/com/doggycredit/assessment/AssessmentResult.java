package com.doggycredit.assessment;

import java.util.List;
import java.util.UUID;

import com.doggycredit.scoring.ScoreBand;

/**
 * @param recommendations C-06: productos recomendados en orden; {@code null} si no corresponden
 * (plan sin la función o resultado PARCIAL); vacía si se evaluaron y ninguno aplica.
 */
public record AssessmentResult(UUID id, AssessmentStatus status, int score, ScoreBand band,
		List<String> reasons, String modelVersion, boolean simulated, String correlationId,
		List<AssessmentRecommendation> recommendations) {
}
