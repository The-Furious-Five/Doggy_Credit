package com.doggycredit.analytics.internal;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * D-14 / ADR-029: agregados entre tenants solo a través de las funciones de lectura publicadas.
 * Las tablas siguen con RLS forzado; consultarlas directamente aquí devolvería solo el tenant del operador.
 */
@Repository
class OperatorMetricsRepository {

    private final JdbcTemplate jdbc;

    OperatorMetricsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<TenantEvaluations> evaluations(Instant from, Instant until) {
        return jdbc.query("SELECT tenant_id, evaluaciones FROM assessment.operator_evaluations(?::timestamptz, ?::timestamptz)",
                (rs, row) -> new TenantEvaluations(rs.getObject("tenant_id", UUID.class), rs.getLong("evaluaciones")),
                from.atOffset(ZoneOffset.UTC), until.atOffset(ZoneOffset.UTC));
    }

    List<PlanChanges> planChanges(Instant from, Instant until) {
        return jdbc.query("SELECT plan_anterior, plan_nuevo, cambios FROM tenancy.operator_plan_changes(?::timestamptz, ?::timestamptz)",
                (rs, row) -> new PlanChanges(rs.getString("plan_anterior"), rs.getString("plan_nuevo"), rs.getLong("cambios")),
                from.atOffset(ZoneOffset.UTC), until.atOffset(ZoneOffset.UTC));
    }

    record TenantEvaluations(UUID tenant, long evaluations) { }
    record PlanChanges(String from, String to, long count) { }
}
