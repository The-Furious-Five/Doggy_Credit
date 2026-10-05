package com.doggycredit.assessment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.modulith.events.Externalized;

import com.doggycredit.scoring.ScoreBand;

/**
 * Evento de dominio: terminó una evaluación de crédito, con el resultado que
 * se le devolvió al cliente (D-03). Esquema publicado en
 * {@code contracts/events/CreditAssessmentCompleted.v1.schema.json}; los
 * nombres siguen al recurso {@code Evaluacion} del OpenAPI.
 */
@Externalized("doggycredit.assessment::#{tenantId()}")
public record CreditAssessmentCompleted(UUID eventId, Instant ocurridoEn, UUID tenantId, UUID assessmentId,
		UUID applicantId, AssessmentStatus estado, int score, ScoreBand banda, List<String> razones,
		String modelVersion, boolean simulado, String correlationId) {

	public static final String SCHEMA = "CreditAssessmentCompleted.v1";

}
