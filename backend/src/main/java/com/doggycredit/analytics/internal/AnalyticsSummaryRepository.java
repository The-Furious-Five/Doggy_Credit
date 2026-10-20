package com.doggycredit.analytics.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

/** Solo consume vistas publicadas; predicado de tenant además de RLS. */
@Repository
class AnalyticsSummaryRepository {

    private final JdbcTemplate jdbc;

    AnalyticsSummaryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<Basic> basic(UUID tenant, Instant from, Instant until) {
        return jdbc.query("""
                SELECT grouping(need_category) AS total, need_category, count(*) AS evaluations,
                       avg(score)::double precision AS average_score,
                       count(recommended_product_type) AS recommended
                FROM assessment.analytics_evaluations_v1
                WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
                GROUP BY GROUPING SETS ((), (need_category))
                ORDER BY grouping(need_category) DESC, need_category
                """, (rs, row) -> new Basic(rs.getInt("total") == 1, rs.getString("need_category"),
                rs.getLong("evaluations"), rs.getObject("average_score", Double.class), rs.getLong("recommended")),
                tenant, Timestamp.from(from), Timestamp.from(until));
    }

    int activeIntegrations(UUID tenant) {
        return jdbc.queryForObject("SELECT active_count FROM integration.analytics_active_v1 WHERE tenant_id = ?",
                Integer.class, tenant);
    }

    List<Premium> premium(UUID tenant, Instant from, Instant until) {
        return jdbc.query("""
                SELECT grouping(recommended_product_type) AS total, recommended_product_type,
                       count(*) AS evaluations,
                       count(*) FILTER (WHERE status = 'PREAPROBADO') AS preapproved,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY decision_time_ms) AS median_ms,
                       coalesce(sum(recommended_amount), 0.00) AS amount
                FROM assessment.analytics_evaluations_v1
                WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
                GROUP BY GROUPING SETS ((), (recommended_product_type))
                ORDER BY grouping(recommended_product_type) DESC, recommended_product_type
                """, (rs, row) -> new Premium(rs.getInt("total") == 1, rs.getString("recommended_product_type"),
                rs.getLong("evaluations"), rs.getLong("preapproved"), rs.getObject("median_ms", Double.class),
                rs.getBigDecimal("amount").toPlainString()), tenant, Timestamp.from(from), Timestamp.from(until));
    }

    /**
     * C-14 (LOS-130): evaluaciones con decisión por banda. La base de bandas y override excluye
     * FALLIDA y PENDIENTE (decisión del 19/10, ver runbook analytics-e02.md).
     */
    Map<String, Long> bands(UUID tenant, Instant from, Instant until) {
        Map<String, Long> bands = new HashMap<>();
        jdbc.query("""
                SELECT band, count(*) AS evaluations
                FROM assessment.analytics_evaluations_v1
                WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
                  AND status NOT IN ('FALLIDA', 'PENDIENTE')
                GROUP BY band
                """, (RowCallbackHandler) rs -> bands.put(rs.getString("band"), rs.getLong("evaluations")),
                tenant, Timestamp.from(from), Timestamp.from(until));
        return bands;
    }

    /** C-14: casos resueltos por una persona, contados por la fecha de la evaluación. */
    long overrides(UUID tenant, Instant from, Instant until) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM assessment.analytics_resolutions_v1
                WHERE tenant_id = ? AND assessment_created_at >= ? AND assessment_created_at < ?
                  AND assessment_status NOT IN ('FALLIDA', 'PENDIENTE')
                """, Long.class, tenant, Timestamp.from(from), Timestamp.from(until));
    }

    /**
     * C-14: por tipo del primer producto recomendado, cuántas evaluaciones lo recomendaron y cuántas
     * lo aceptaron: quedaron PREAPROBADO o se aprobaron en la bandeja con un producto del mismo tipo.
     * Una PREAPROBADO nunca pasa por la bandeja, así que las dos partes no se solapan.
     */
    List<Acceptance> acceptance(UUID tenant, Instant from, Instant until) {
        return jdbc.query("""
                SELECT e.recommended_product_type AS product_type, e.recommended,
                       e.preapproved + coalesce(r.approved, 0) AS accepted
                FROM (SELECT recommended_product_type, count(*) AS recommended,
                             count(*) FILTER (WHERE status = 'PREAPROBADO') AS preapproved
                      FROM assessment.analytics_evaluations_v1
                      WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
                        AND recommended_product_type IS NOT NULL
                      GROUP BY recommended_product_type) e
                LEFT JOIN (SELECT recommended_product_type, count(*) AS approved
                           FROM assessment.analytics_resolutions_v1
                           WHERE tenant_id = ? AND assessment_created_at >= ? AND assessment_created_at < ?
                             AND decision = 'APROBADO' AND product_type = recommended_product_type
                           GROUP BY recommended_product_type) r
                  ON r.recommended_product_type = e.recommended_product_type
                """, (rs, row) -> new Acceptance(rs.getString("product_type"), rs.getLong("recommended"),
                rs.getLong("accepted")), tenant, Timestamp.from(from), Timestamp.from(until),
                tenant, Timestamp.from(from), Timestamp.from(until));
    }

    record Basic(boolean total, String category, long evaluations, Double averageScore, long recommended) { }
    record Acceptance(String productType, long recommended, long accepted) { }
    record Premium(boolean total, String productType, long evaluations, long preapproved, Double medianMs, String amount) { }
}
