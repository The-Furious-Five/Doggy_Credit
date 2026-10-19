package com.doggycredit.analytics.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.doggycredit.TestTenants;
import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.shared.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;

/** E-02: SQL/RLS reales, contrato HTTP, entitlements del catálogo y latencia. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AnalyticsControllerTests {

    private static final Logger LOG = LoggerFactory.getLogger(AnalyticsControllerTests.class);
    private static final Instant DATE = Instant.parse("2026-10-15T12:00:00Z");
    private static final String INSERT = """
            INSERT INTO assessment.credit_assessments (id, tenant_id, applicant_id, consent_id,
              idempotency_key, request_hash, status, score, band, reasons, model_version, correlation_id,
              created_at, need_category, recommended_product_type, recommended_amount, decision_time_ms)
            VALUES (?, ?, ?, ?, ?, 'demo', ?, ?, 'B', '{A,B,C}'::text[], 'scoring-v2', 'e02-test', ?, ?, ?, ?, ?)
            """;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PostgreSQLContainer postgres;
    @LocalServerPort int port;
    @MockitoBean JwtDecoder decoder;

    @BeforeEach
    void decodeOnlyTestTokens() {
        when(decoder.decode(anyString())).thenAnswer(invocation -> Jwt.withTokenValue(invocation.getArgument(0))
                .header("alg", "RS256").subject("analista-demo")
                .claim("tenant_id", invocation.getArgument(0)).build());
    }

    @ParameterizedTest
    @ValueSource(strings = { "FREE", "PRO", "ENTERPRISE" })
    void entregaLosKpisComunesYProtegeLosAvanzadosPorPlan(String plan) throws Exception {
        UUID tenant = tenant(plan);
        insert(tenant, DATE, 800, "PREAPROBADO", "CAPITAL_TRABAJO", "MICROCREDITO", "3000.00", 100L);
        insert(tenant, DATE, 600, "REVISION_MANUAL", "CAPITAL_TRABAJO", "PYME", "9000.00", 300L);
        insert(tenant, DATE, 400, "PARCIAL", "EFICIENCIA_ENERGETICA", null, null, 500L);
        insert(tenant, DATE, 1000, "PREAPROBADO", null, null, null, null);
        integration(tenant, "BANCO", true);
        integration(tenant, "WALLET", false);
        model(tenant, "scoring-v2", "2026-10-12T04:00:00Z", 0.31);
        model(tenant, "scoring-v2", "2026-10-05T04:00:00Z", 0.05);
        var response = request(tenant).andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value(plan))
                .andExpect(jsonPath("$.evaluaciones").value(4))
                .andExpect(jsonPath("$.scorePromedio").value(700))
                .andExpect(jsonPath("$.porcentajeConRecomendacion").value(50))
                .andExpect(jsonPath("$.integracionesActivas").value(2))
                .andExpect(jsonPath("$.necesidades[0].categoria").value("CAPITAL_TRABAJO"))
                .andExpect(jsonPath("$.necesidades[0].cantidad").value(2))
                .andExpect(jsonPath("$.necesidades[0].porcentaje").value(50));
        if ("FREE".equals(plan)) {
            response.andExpect(jsonPath("$.analiticaAvanzada").value(false))
                    .andExpect(jsonPath("$.pro").doesNotExist());
            assertThat(response.andReturn().getResponse().getContentAsString())
                    .doesNotContain("conversionPorcentaje", "modelos", "recomendacionesPorProducto", "12000",
                            "distribucionBandas", "tasaOverride", "aceptacionPorProducto");
        } else {
            response.andExpect(jsonPath("$.analiticaAvanzada").value(true))
                    .andExpect(jsonPath("$.pro.conversionPorcentaje").value(50))
                    .andExpect(jsonPath("$.pro.tiempoDecisionMedianaMs").value(300))
                    .andExpect(jsonPath("$.pro.montoRecomendado").value("12000.00"))
                    .andExpect(jsonPath("$.pro.recomendacionesPorProducto.length()").value(2))
                    .andExpect(jsonPath("$.pro.recomendacionesPorProducto[0].porcentaje").value(50))
                    .andExpect(jsonPath("$.pro.modelos.length()").value(1))
                    .andExpect(jsonPath("$.pro.modelos[0].psi").value(0.31))
                    .andExpect(jsonPath("$.pro.modelos[0].hasta").value("2026-10-12T04:00:00Z"));
        }
    }

    /**
     * C-14 (LOS-130): cada cifra de los KPIs de cartera se puede calcular a mano. Base de bandas y
     * override = evaluaciones del intervalo sin FALLIDA ni PENDIENTE (7).
     */
    @Test
    void calculaBandasOverrideYAceptacionPorProducto() throws Exception {
        UUID tenant = tenant("PRO");
        // Aceptadas sin bandeja: PREAPROBADO con su producto recomendado.
        portfolio(tenant, DATE, "A", "PREAPROBADO", "MICROCREDITO");
        portfolio(tenant, DATE, "B", "PREAPROBADO", "MICROCREDITO");
        // Bandeja: aprobada con el mismo tipo (aceptada), con otro tipo (no), rechazada y una vieja sin tipo.
        resolution(tenant, portfolio(tenant, DATE, "C", "REVISION_MANUAL", "PYME"), "APROBADO", "PYME");
        resolution(tenant, portfolio(tenant, DATE, "D", "REVISION_MANUAL", "PYME"), "APROBADO", "MICROCREDITO");
        resolution(tenant, portfolio(tenant, DATE, "E", "PARCIAL", null), "RECHAZADO", null);
        resolution(tenant, portfolio(tenant, DATE, "B", "REVISION_MANUAL", "VERDE"), "APROBADO", null);
        // Pendiente en la bandeja: cuenta en la base y como recomendada, sin override.
        portfolio(tenant, DATE, "C", "REVISION_MANUAL", "VERDE");
        // Fuera de la base: FALLIDA, y una evaluación de noviembre resuelta.
        portfolio(tenant, DATE, "E", "FALLIDA", null);
        resolution(tenant, portfolio(tenant, Instant.parse("2026-11-05T12:00:00Z"), "A", "REVISION_MANUAL", "PYME"),
                "APROBADO", "PYME");

        request(tenant).andExpect(status().isOk())
                .andExpect(jsonPath("$.pro.distribucionBandas.length()").value(5))
                .andExpect(jsonPath("$.pro.distribucionBandas[*].banda").value(org.hamcrest.Matchers.contains("A", "B", "C", "D", "E")))
                .andExpect(jsonPath("$.pro.distribucionBandas[*].cantidad").value(org.hamcrest.Matchers.contains(1, 2, 2, 1, 1)))
                .andExpect(jsonPath("$.pro.distribucionBandas[*].porcentaje")
                        .value(org.hamcrest.Matchers.contains(14.29, 28.57, 28.57, 14.29, 14.29)))
                .andExpect(jsonPath("$.pro.tasaOverride.overrides").value(4))
                .andExpect(jsonPath("$.pro.tasaOverride.evaluaciones").value(7))
                .andExpect(jsonPath("$.pro.tasaOverride.porcentaje").value(57.14))
                .andExpect(jsonPath("$.pro.aceptacionPorProducto[*].tipo")
                        .value(org.hamcrest.Matchers.contains("MICROCREDITO", "VERDE", "PYME")))
                .andExpect(jsonPath("$.pro.aceptacionPorProducto[*].recomendados").value(org.hamcrest.Matchers.contains(2, 2, 2)))
                .andExpect(jsonPath("$.pro.aceptacionPorProducto[*].aceptados").value(org.hamcrest.Matchers.contains(2, 0, 1)))
                .andExpect(jsonPath("$.pro.aceptacionPorProducto[*].porcentaje")
                        .value(org.hamcrest.Matchers.contains(100.0, 0.0, 50.0)));
    }

    @Test
    void aislaTodoIncluidasVistasSinPredicadoYParametrosManipulados() throws Exception {
        UUID a = tenant("PRO"), b = tenant("PRO");
        insert(a, DATE, 800, "PREAPROBADO", "CAPITAL_TRABAJO", "MICROCREDITO", "2000.00", 100L);
        insert(b, DATE, 200, "PARCIAL", "CONSUMO_CONSOLIDACION", "PYME", "99999.00", 999L);
        integration(b, "BANCO", true);
        model(a, "modelo-a", "2026-10-12T04:00:00Z", 0.01);
        model(b, "modelo-secreto-b", "2026-10-12T04:00:00Z", 0.8);
        UUID c = tenant("PRO");
        resolution(c, portfolio(c, DATE, "D", "REVISION_MANUAL", "PYME"), "APROBADO", "PYME");
        mvc.perform(get("/analytics/resumen").param("desde", "2026-10-01").param("hasta", "2026-10-31")
                .param("tenant_id", b.toString()).param("tenantId", b.toString()).param("plan", "FREE")
                .with(jwt().jwt(token -> token.subject("a").claim("tenant_id", a.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evaluaciones").value(1))
                .andExpect(jsonPath("$.scorePromedio").value(800))
                .andExpect(jsonPath("$.integracionesActivas").value(1))
                .andExpect(jsonPath("$.pro.montoRecomendado").value("2000.00"))
                .andExpect(jsonPath("$.pro.modelos[0].version").value("modelo-a"))
                // C-14: la resolución y la banda D de C no aparecen en los KPIs de cartera de A.
                .andExpect(jsonPath("$.pro.tasaOverride.overrides").value(0))
                .andExpect(jsonPath("$.pro.tasaOverride.evaluaciones").value(1))
                .andExpect(jsonPath("$.pro.distribucionBandas[3].cantidad").value(0))
                .andExpect(jsonPath("$.pro.aceptacionPorProducto[2].recomendados").value(0));
        request(b).andExpect(jsonPath("$.scorePromedio").value(200))
                .andExpect(jsonPath("$.pro.montoRecomendado").value("99999.00"));
        request(c).andExpect(jsonPath("$.pro.tasaOverride.overrides").value(1))
                .andExpect(jsonPath("$.pro.distribucionBandas[3].cantidad").value(1));
        assertThat(asTenant(a, () -> jdbc.queryForObject("SELECT count(*) FROM assessment.analytics_resolutions_v1", Integer.class)))
                .isZero();
        assertThat(asTenant(c, () -> jdbc.queryForList("SELECT DISTINCT tenant_id FROM assessment.analytics_resolutions_v1", UUID.class)))
                .containsExactly(c);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM assessment.analytics_resolutions_v1", Integer.class)).isZero();
        assertThat(asTenant(a, () -> jdbc.queryForList("SELECT DISTINCT tenant_id FROM assessment.analytics_evaluations_v1", UUID.class)))
                .containsExactly(a);
        assertThat(asTenant(a, () -> jdbc.queryForList("SELECT tenant_id FROM integration.analytics_active_v1", UUID.class)))
                .containsExactly(a);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM assessment.analytics_evaluations_v1", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.analytics_active_v1", Integer.class)).isZero();
        UUID free = tenant("FREE");
        mvc.perform(get("/analytics/resumen").param("plan", "PRO").with(jwt().jwt(token -> token.subject("free")
                .claim("tenant_id", free.toString()).claim("plan", "PRO"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pro").doesNotExist());
    }

    @Test
    void respetaFechasInclusivasEnBolivia() throws Exception {
        UUID tenant = tenant("PRO");
        insert(tenant, Instant.parse("2026-10-01T03:59:59Z"), 100, "PARCIAL", null, null, null, null);
        insert(tenant, Instant.parse("2026-10-01T04:00:00Z"), 800, "PREAPROBADO", null, null, null, 100L);
        insert(tenant, Instant.parse("2026-11-01T03:59:59.999999Z"), 600, "REVISION_MANUAL", null, null, null, 500L);
        insert(tenant, Instant.parse("2026-11-01T04:00:00Z"), 200, "PARCIAL", null, null, null, null);
        request(tenant).andExpect(jsonPath("$.evaluaciones").value(2))
                .andExpect(jsonPath("$.scorePromedio").value(700))
                .andExpect(jsonPath("$.pro.tiempoDecisionMedianaMs").value(300));
    }

    @Test
    void vacioDevuelveCerosYMetricasDesconocidasEnNull() throws Exception {
        request(tenant("PRO")).andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluaciones").value(0))
                .andExpect(jsonPath("$.scorePromedio").isEmpty())
                .andExpect(jsonPath("$.porcentajeConRecomendacion").value(0))
                .andExpect(jsonPath("$.integracionesActivas").value(1))
                .andExpect(jsonPath("$.necesidades.length()").value(4))
                .andExpect(jsonPath("$.pro.conversionPorcentaje").value(0))
                .andExpect(jsonPath("$.pro.tiempoDecisionMedianaMs").isEmpty())
                .andExpect(jsonPath("$.pro.montoRecomendado").value("0.00"))
                .andExpect(jsonPath("$.pro.modelos").isEmpty())
                // C-14: siempre las cinco bandas y los tres tipos, en cero y sin dividir por cero.
                .andExpect(jsonPath("$.pro.distribucionBandas.length()").value(5))
                .andExpect(jsonPath("$.pro.distribucionBandas[*].cantidad").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(0))))
                .andExpect(jsonPath("$.pro.tasaOverride.overrides").value(0))
                .andExpect(jsonPath("$.pro.tasaOverride.porcentaje").value(0))
                .andExpect(jsonPath("$.pro.aceptacionPorProducto.length()").value(3))
                .andExpect(jsonPath("$.pro.aceptacionPorProducto[*].porcentaje").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(0.0))));
    }

    @Test
    void protegeLaAutenticacionYValidaElIntervalo() throws Exception {
        mvc.perform(get("/analytics/resumen")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
        mvc.perform(get("/analytics/resumen").with(jwt().jwt(token -> token.subject("sin-tenant"))))
                .andExpect(status().isForbidden());
        UUID tenant = tenant("FREE");
        for (String[] range : List.of(new String[] {"2026-10-31", "2026-10-01"}, new String[] {"invalida", "2026-10-01"})) {
            mvc.perform(get("/analytics/resumen").param("desde", range[0]).param("hasta", range[1])
                    .header("X-Correlation-Id", "e02-fechas")
                    .with(jwt().jwt(token -> token.subject("a").claim("tenant_id", tenant.toString()))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.correlationId").value("e02-fechas"));
        }
        mvc.perform(get("/analytics/resumen").with(jwt().jwt(token -> token.subject("a").claim("tenant_id", tenant.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.desde").isNotEmpty()).andExpect(jsonPath("$.hasta").isNotEmpty());
    }

    @Test
    void respondePorHttpEnMenosDeUnSegundoConLosDatosDemo() throws Exception {
        UUID tenant = tenant("PRO");
        var people = new ObjectMapper().readTree(Path.of("../seed/personas-sinteticas.json").toFile()).get("personas");
        List<Object[]> rows = new ArrayList<>();
        // 30 días por cada una de las 50 personas demo: 1500 evaluaciones.
        for (var person : people) {
            var finance = person.get("finanzas");
            boolean good = finance.get("mora_max_12m_dias").asInt() == 0;
            String product = "PYME".equals(person.get("tipo").asText()) ? "PYME" : "MICROCREDITO";
            for (int day = 1; day <= 30; day++) {
                rows.add(values(tenant, Instant.parse("2026-10-%02dT12:00:00Z".formatted(day)), good ? 800 : 400,
                        good ? "PREAPROBADO" : "REVISION_MANUAL", "CAPITAL_TRABAJO", good ? product : null,
                        good ? finance.get("ingreso_mensual").asText() : null, 250L));
            }
        }
        asTenant(tenant, () -> jdbc.batchUpdate(INSERT, rows));
        integration(tenant, "BANCO", true);
        model(tenant, "scoring-v2", "2026-10-12T04:00:00Z", 0.05);
        List<Long> times = new ArrayList<>();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            for (int i = 0; i < 10; i++) {
                long started = System.nanoTime();
                var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                        + "/analytics/resumen?desde=2026-10-01&hasta=2026-10-31"))
                        .header("Authorization", "Bearer " + tenant).timeout(Duration.ofSeconds(2)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(new ObjectMapper().readTree(response.body()).get("evaluaciones").asInt()).isEqualTo(rows.size());
                assertThat(response.headers().firstValue("cache-control")).contains("no-store");
                times.add(elapsedMs);
                assertThat(elapsedMs).as("HTTP completo, incluida la primera solicitud").isLessThan(1000);
            }
        }
        LOG.info("E-02 rendimiento: {} personas demo, {} evaluaciones, HTTP ms: {}", people.size(), rows.size(), times);
    }

    private UUID tenant(String plan) {
        UUID id = UUID.randomUUID();
        TestTenants.register(postgres, id.toString(), plan);
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions request(UUID tenant) throws Exception {
        return mvc.perform(get("/analytics/resumen").param("desde", "2026-10-01").param("hasta", "2026-10-31")
                .with(jwt().jwt(token -> token.subject("analista").claim("tenant_id", tenant.toString()))));
    }

    private Object[] values(UUID tenant, Instant at, int score, String status, String need, String product, String amount, Long duration) {
        return new Object[] { UUID.randomUUID(), tenant, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString(),
                status, score, Timestamp.from(at), need, product, amount == null ? null : new BigDecimal(amount), duration };
    }

    private void insert(UUID tenant, Instant at, int score, String status, String need, String product, String amount, Long duration) {
        asTenant(tenant, () -> jdbc.update(INSERT, values(tenant, at, score, status, need, product, amount, duration)));
    }

    /** C-14: una evaluación con banda y estado propios; devuelve su id para resolverla en la bandeja. */
    private UUID portfolio(UUID tenant, Instant at, String band, String status, String product) {
        UUID id = UUID.randomUUID();
        asTenant(tenant, () -> jdbc.update("""
                INSERT INTO assessment.credit_assessments (id, tenant_id, applicant_id, consent_id,
                  idempotency_key, request_hash, status, score, band, reasons, model_version, correlation_id,
                  created_at, recommended_product_type, recommended_amount)
                VALUES (?, ?, ?, ?, ?, 'demo', ?, 500, ?, '{A,B,C}'::text[], 'scoring-v2', 'c14-test', ?, ?, ?)
                """, id, tenant, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString(), status, band,
                Timestamp.from(at), product, product == null ? null : new BigDecimal("1000.00")));
        return id;
    }

    /** C-14: resolución de la bandeja; productType null en APROBADO simula una resolución previa a C-14. */
    private void resolution(UUID tenant, UUID assessment, String decision, String productType) {
        asTenant(tenant, () -> jdbc.update("""
                INSERT INTO assessment.manual_review_resolutions (id, tenant_id, assessment_id, decision, product_id,
                  product_type, justification, resolved_by, resolved_at)
                VALUES (?, ?, ?, ?, ?, ?, 'Resolución sintética de prueba C-14', 'analista', now())
                """, UUID.randomUUID(), tenant, assessment, decision,
                "APROBADO".equals(decision) ? UUID.randomUUID() : null, productType));
    }

    private void integration(UUID tenant, String source, boolean active) {
        asTenant(tenant, () -> jdbc.update("""
                INSERT INTO integration.tenant_integrations (id, tenant_id, source, active, updated_at, updated_by)
                VALUES (?, ?, ?, ?, now(), 'e02')
                """, UUID.randomUUID(), tenant, source, active));
    }

    private void model(UUID tenant, String version, String until, double psi) {
        asTenant(tenant, () -> jdbc.update("""
                INSERT INTO analytics.model_drift (id, tenant_id, model_version, window_start, window_end,
                  psi, actual_count, baseline_count, status, computed_at)
                VALUES (?, ?, ?, ?, ?, ?, 50, 100, 'ESTABLE', now())
                """, UUID.randomUUID(), tenant, version, Timestamp.from(Instant.parse(until).minus(Duration.ofDays(7))),
                Timestamp.from(Instant.parse(until)), psi));
    }

    private <T> T asTenant(UUID tenant, java.util.function.Supplier<T> work) {
        TenantContext.set(tenant);
        try { return work.get(); } finally { TenantContext.clear(); }
    }
}
