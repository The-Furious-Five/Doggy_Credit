package com.doggycredit.analytics.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
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

    record Basic(boolean total, String category, long evaluations, Double averageScore, long recommended) { }
    record Premium(boolean total, String productType, long evaluations, long preapproved, Double medianMs, String amount) { }
}
