package com.doggycredit.assessment.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.doggycredit.assessment.AssessmentRecommendation;

/**
 * C-06: recomendaciones guardadas de cada evaluación (assessment.assessment_recommendations,
 * con RLS). Filtra por tenant además de la RLS (defensa en profundidad).
 */
@Repository
class AssessmentRecommendationStore {

	private final JdbcTemplate jdbc;

	AssessmentRecommendationStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	void save(UUID tenantId, UUID assessmentId, List<AssessmentRecommendation> recommendations) {
		for (int i = 0; i < recommendations.size(); i++) {
			AssessmentRecommendation recommendation = recommendations.get(i);
			jdbc.update("""
					INSERT INTO assessment.assessment_recommendations
					    (id, tenant_id, assessment_id, position, product_type, currency, suggested_amount, reason,
					     suggested_term_months, reasons)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", UUID.randomUUID(), tenantId, assessmentId, i + 1, recommendation.productType(),
					recommendation.currency(), recommendation.suggestedAmount(), recommendation.reason(),
					recommendation.suggestedTermMonths(), recommendation.reasons() == null ? null
                        : recommendation.reasons().toArray(String[]::new));
		}
	}

	List<AssessmentRecommendation> load(UUID tenantId, UUID assessmentId) {
		return jdbc.query("""
				SELECT product_type, currency, suggested_amount, reason, suggested_term_months, reasons
				FROM assessment.assessment_recommendations
				WHERE tenant_id = ? AND assessment_id = ?
				ORDER BY position
				""", (rs, row) -> new AssessmentRecommendation(rs.getString("product_type"), rs.getString("currency"),
				rs.getBigDecimal("suggested_amount"), rs.getString("reason"),
                rs.getObject("suggested_term_months", Integer.class), reasons(rs)), tenantId, assessmentId);
	}

    private static List<String> reasons(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Array array = rs.getArray("reasons");
        if (array == null) return null;
        try {
            return List.of((String[]) array.getArray());
        } finally {
            array.free();
        }
    }

}
