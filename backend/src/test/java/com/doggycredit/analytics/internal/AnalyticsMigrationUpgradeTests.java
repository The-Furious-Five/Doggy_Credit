package com.doggycredit.analytics.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Regresión del merge: actualizar una base de main sin perder sus datos ni su índice. */
@Testcontainers
class AnalyticsMigrationUpgradeTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    private static final String APP_PASSWORD = UUID.randomUUID().toString();

    @Test
    void actualizaElEsquemaPublicadoEnMainYConservaEvaluacionesPoliticasYRls() throws Exception {
        // Reproducir el esquema de origin/main antes de integrar E-02.
        migration("2026.10.06.1100").migrate();
        var admin = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword()));
        UUID tenant = UUID.randomUUID(), assessment = UUID.randomUUID(), policy = UUID.randomUUID();
        admin.update("""
                INSERT INTO assessment.credit_assessments (id, tenant_id, applicant_id, consent_id,
                  idempotency_key, request_hash, status, score, band, reasons, model_version, correlation_id, created_at,
                  requested_amount)
                VALUES (?, ?, ?, ?, 'upgrade-main', 'x', 'PREAPROBADO', 800, 'A', '{A,B,C}'::text[], 'basic-v1',
                  'upgrade-main', now(), 3000)
                """, assessment, tenant, UUID.randomUUID(), UUID.randomUUID());
        admin.update("""
                INSERT INTO assessment.policies (id, tenant_id, code, name, condition_text, effect, active)
                VALUES (?, ?, 'MONTO_ALTO', 'Revisión por monto alto', 'monto > 200000', 'REVISION_MANUAL', false)
                """, policy, tenant);
        String indexBefore = admin.queryForObject("SELECT pg_get_indexdef('assessment.assessments_tenant_created_idx'::regclass)", String.class);

        var upgraded = migration("latest");
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        upgraded.validate();

        var row = admin.queryForMap("""
                SELECT status, score, requested_amount, need_category, recommended_product_type, decision_time_ms
                FROM assessment.credit_assessments WHERE id = ?
                """, assessment);
        assertThat(row.get("status")).isEqualTo("PREAPROBADO");
        assertThat(row.get("score")).isEqualTo(800);
        assertThat((java.math.BigDecimal) row.get("requested_amount")).isEqualByComparingTo("3000.00");
        assertThat(row.get("need_category")).isNull();
        assertThat(row.get("recommended_product_type")).isNull();
        assertThat(row.get("decision_time_ms")).isNull();
        assertThat(admin.queryForObject("SELECT active FROM assessment.policies WHERE id = ?", Boolean.class, policy)).isFalse();
        assertThat(admin.queryForObject("SELECT pg_get_indexdef('assessment.assessments_tenant_created_idx'::regclass)", String.class))
                .isEqualTo(indexBefore);

        // Login real del rol restringido: comprobar RLS sobre la vista sin WHERE tenant_id.
        try (var app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "doggycredit_app", APP_PASSWORD)) {
            for (String context : new String[] { tenant.toString(), UUID.randomUUID().toString(), "" }) {
                try (var setting = app.prepareStatement("SELECT set_config('app.tenant_id', ?, false)")) {
                    setting.setString(1, context);
                    setting.execute();
                }
                try (var query = app.createStatement(); var result = query.executeQuery("SELECT count(*) FROM assessment.analytics_evaluations_v1")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getLong(1)).isEqualTo(context.equals(tenant.toString()) ? 1 : 0);
                }
            }
        }
    }

    private Flyway migration(String target) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").placeholders(Map.of("appDbPassword", APP_PASSWORD))
                .target(target).load();
    }
}
