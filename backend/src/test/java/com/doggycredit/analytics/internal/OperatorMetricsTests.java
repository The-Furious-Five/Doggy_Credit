package com.doggycredit.analytics.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.ErrorResponseException;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.doggycredit.TestTenants;
import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.shared.TenantContext;

/**
 * D-14 (RF-ANA-04) contra PostgreSQL 17 real: cifras del período, roles y las garantías de ADR-029.
 * Los datos del período van en marzo de 2031, donde ninguna otra prueba escribe, así cada cifra es exacta.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OperatorMetricsTests {

    private static final String INSERT = """
            INSERT INTO assessment.credit_assessments (id, tenant_id, applicant_id, consent_id,
              idempotency_key, request_hash, status, score, band, reasons, model_version, correlation_id, created_at)
            VALUES (?, ?, ?, ?, ?, 'demo', 'PREAPROBADO', 700, 'B', '{A,B,C}'::text[], 'scoring-v2', 'd14-test', ?)
            """;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PostgreSQLContainer postgres;
    @Autowired OperatorMetricsService service;

    private UUID operator;

    @BeforeEach
    void operatorTenant() {
        operator = tenant("PRO");
    }

    @Test
    void soloElOperadorDeLaPlataformaVeLasMetricas() throws Exception {
        mvc.perform(get("/operador/metricas")).andExpect(status().isUnauthorized());
        for (String role : List.of("ANALYST", "TENANT_ADMIN", "API_CLIENT")) {
            mvc.perform(get("/operador/metricas").with(as(operator, role)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
        }
        metrics(null, null).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        assertThatThrownBy(() -> service.summarize(null, null)).as("el servicio exige el rol aunque lo llame otro código")
                .isInstanceOf(ErrorResponseException.class);
    }

    @Test
    void cuentaElPeriodoEnUtcConFechasInclusivas() throws Exception {
        UUID free = tenant("FREE"), pro = tenant("PRO"), enterprise = tenant("ENTERPRISE"), converted = tenant("PRO");
        evaluation(free, "2031-02-28T23:59:59.999999Z");
        evaluation(free, "2031-03-01T00:00:00Z");
        evaluation(free, "2031-03-20T15:00:00Z");
        evaluation(pro, "2031-03-05T10:00:00Z");
        evaluation(pro, "2031-03-05T11:00:00Z");
        evaluation(pro, "2031-03-31T23:59:59.999999Z");
        evaluation(pro, "2031-04-01T00:00:00Z");
        planChange(converted, "FREE", "PRO", "2031-03-10T12:00:00Z");
        planChange(free, "FREE", "PRO", "2031-03-31T23:59:59Z");
        planChange(free, "PRO", "FREE", "2031-03-31T23:59:59.5Z");
        planChange(pro, "PRO", "ENTERPRISE", "2031-03-15T12:00:00Z");
        planChange(enterprise, "FREE", "ENTERPRISE", "2031-03-12T00:00:00Z");
        planChange(pro, "FREE", "PRO", "2031-02-28T23:59:59Z");
        planChange(converted, "FREE", "ENTERPRISE", "2031-04-01T00:00:00Z");

        String body = metrics("2031-03-01", "2031-03-31").andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2031-03-01"))
                .andExpect(jsonPath("$.hasta").value("2031-03-31"))
                .andExpect(jsonPath("$.evaluaciones").value(5))
                .andExpect(jsonPath("$.tenantsActivados").value(2))
                .andExpect(jsonPath("$.conversionesFreeAPro").value(3))
                .andExpect(jsonPath("$.tenantsPorPlan.length()").value(3))
                .andExpect(jsonPath("$.tenantsPorPlan[0].plan").value("FREE"))
                .andExpect(jsonPath("$.tenantsPorPlan[0].evaluaciones").value(2))
                .andExpect(jsonPath("$.tenantsPorPlan[1].plan").value("PRO"))
                .andExpect(jsonPath("$.tenantsPorPlan[1].evaluaciones").value(3))
                .andExpect(jsonPath("$.tenantsPorPlan[2].plan").value("ENTERPRISE"))
                .andExpect(jsonPath("$.tenantsPorPlan[2].evaluaciones").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).as("solo agregados: ningún id, nombre ni NIT de tenant")
                .doesNotContain(free.toString(), pro.toString(), converted.toString(), "Tenant de prueba");

        metrics("2031-03-05", "2031-03-05").andExpect(jsonPath("$.evaluaciones").value(2))
                .andExpect(jsonPath("$.tenantsActivados").value(1))
                .andExpect(jsonPath("$.conversionesFreeAPro").value(0));
    }

    @Test
    void laFotoDeTenantsCoincideConElRegistro() throws Exception {
        UUID suspended = tenant("FREE");
        admin("UPDATE tenancy.tenants SET activo = false WHERE id = '" + suspended + "'");
        long total = jdbc.queryForObject("SELECT count(*) FROM tenancy.tenants", Long.class);
        long inactive = jdbc.queryForObject("SELECT count(*) FROM tenancy.tenants WHERE NOT activo", Long.class);
        long free = jdbc.queryForObject("SELECT count(*) FROM tenancy.tenants WHERE plan = 'FREE'", Long.class);
        metrics("2031-03-01", "2031-03-31").andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantsActivos").value(total - inactive))
                .andExpect(jsonPath("$.tenantsSuspendidos").value(inactive))
                .andExpect(jsonPath("$.tenantsPorPlan[0].cantidad").value(free));
        assertThat(inactive).isPositive();
    }

    @Test
    void validaElIntervaloYUsaElMesUtcPorDefecto() throws Exception {
        YearMonth month = YearMonth.now(ZoneOffset.UTC);
        metrics(null, null).andExpect(jsonPath("$.desde").value(month.atDay(1).toString()))
                .andExpect(jsonPath("$.hasta").value(month.atEndOfMonth().toString()));
        metrics(null, "2031-03-15").andExpect(jsonPath("$.desde").value("2031-03-01"));
        metrics("2031-02-10", null).andExpect(jsonPath("$.hasta").value("2031-02-28"));
        for (String[] range : List.of(new String[] {"2031-03-31", "2031-03-01"}, new String[] {"invalida", "2031-03-01"},
                new String[] {"2031-03-01", "9999-12-31"})) {
            metrics(range[0], range[1]).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void lasFuncionesDeLecturaMantienenElRlsForzadoYSoloDevuelvenConteos() {
        UUID other = tenant("FREE");
        // En 2032 y no en marzo de 2031, para no alterar las cifras exactas de la otra prueba.
        evaluation(other, "2032-01-02T12:00:00Z");
        planChange(other, "FREE", "PRO", "2032-01-02T12:00:00Z");
        scoped(operator, () -> {
            assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("doggycredit_app");
            assertThat(jdbc.queryForList("SELECT * FROM tenancy.plan_changes WHERE tenant_id = ?", other)).isEmpty();
            assertThat(jdbc.queryForList("SELECT * FROM assessment.credit_assessments WHERE tenant_id = ?", other)).isEmpty();
            assertThat(jdbc.queryForObject("""
                    SELECT evaluaciones FROM assessment.operator_evaluations('2032-01-01', '2032-02-01') WHERE tenant_id = ?
                    """, Long.class, other)).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update("UPDATE tenancy.plan_changes SET plan_nuevo = 'ENTERPRISE' WHERE tenant_id = ?",
                    other)).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO tenancy.plan_changes VALUES (gen_random_uuid(), ?, 'FREE', 'PRO', now())
                    """, other)).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.execute("SET ROLE doggycredit_metrics_reader"))
                    .isInstanceOf(DataAccessException.class);
            return null;
        });
        assertThatThrownBy(() -> admin("DELETE FROM tenancy.plan_changes WHERE tenant_id = '" + other + "'"))
                .as("solo inserción también para el administrador de la base").hasMessageContaining("solo inserción");
        assertThat(jdbc.queryForObject("""
                SELECT bool_and(relrowsecurity AND relforcerowsecurity) FROM pg_class
                 WHERE oid IN ('tenancy.plan_changes'::regclass, 'assessment.credit_assessments'::regclass)
                """, Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT NOT rolcanlogin AND NOT rolinherit AND NOT rolcreaterole AND NOT rolcreatedb
                       AND NOT rolsuper AND NOT rolbypassrls
                  FROM pg_roles WHERE rolname = 'doggycredit_metrics_reader'
                """, Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT pg_has_role(current_user, 'doggycredit_metrics_reader', 'MEMBER')",
                Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT has_table_privilege('doggycredit_metrics_reader', 'tenancy.plan_changes', 'INSERT,UPDATE,DELETE')
                    OR has_table_privilege('doggycredit_metrics_reader', 'assessment.credit_assessments', 'INSERT,UPDATE,DELETE')
                    OR has_column_privilege('doggycredit_metrics_reader', 'assessment.credit_assessments', 'score', 'SELECT')
                    OR has_schema_privilege('doggycredit_metrics_reader', 'assessment', 'CREATE')
                    OR has_schema_privilege('doggycredit_metrics_reader', 'tenancy', 'CREATE')
                """, Boolean.class)).as("solo lee las columnas de los conteos").isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT bool_and(p.prosecdef AND pg_get_userbyid(p.proowner) = 'doggycredit_metrics_reader'
                       AND 'search_path=pg_catalog' = ANY(p.proconfig)
                       AND NOT EXISTS (SELECT 1 FROM aclexplode(p.proacl) a WHERE a.grantee = 0))
                  FROM pg_proc p
                 WHERE p.oid IN ('assessment.operator_evaluations(timestamptz, timestamptz)'::regprocedure,
                                 'tenancy.operator_plan_changes(timestamptz, timestamptz)'::regprocedure)
                """, Boolean.class)).isTrue();
    }

    private ResultActions metrics(String desde, String hasta) throws Exception {
        var request = get("/operador/metricas").with(as(operator, "PLATFORM_ADMIN"));
        if (desde != null) request.param("desde", desde);
        if (hasta != null) request.param("hasta", hasta);
        return mvc.perform(request);
    }

    private static RequestPostProcessor as(UUID tenant, String role) {
        return jwt().jwt(token -> token.subject("user-" + role).claim("tenant_id", tenant.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private UUID tenant(String plan) {
        UUID id = UUID.randomUUID();
        TestTenants.register(postgres, id.toString(), plan);
        return id;
    }

    private void evaluation(UUID tenant, String at) {
        scoped(tenant, () -> jdbc.update(INSERT, UUID.randomUUID(), tenant, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID().toString(), Timestamp.from(Instant.parse(at))));
    }

    private void planChange(UUID tenant, String from, String to, String at) {
        scoped(tenant, () -> jdbc.update("INSERT INTO tenancy.plan_changes VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), tenant, from, to, Timestamp.from(Instant.parse(at))));
    }

    private void admin(String sql) {
        try (Connection admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword())) {
            admin.createStatement().execute(sql);
        }
        catch (SQLException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private <T> T scoped(UUID tenant, Supplier<T> work) {
        TenantContext.set(tenant);
        try { return work.get(); } finally { TenantContext.clear(); }
    }
}
