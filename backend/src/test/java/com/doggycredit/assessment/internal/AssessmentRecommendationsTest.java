package com.doggycredit.assessment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.doggycredit.assessment.AssessmentOperations;
import com.doggycredit.assessment.AssessmentStatus;
import com.doggycredit.billing.Feature;
import com.doggycredit.billing.PlanGate;
import com.doggycredit.scoring.BasicScoreResult;
import com.doggycredit.scoring.ScoreBand;
import com.doggycredit.shared.contract.model.EvaluacionNueva;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantRegistry;

class AssessmentRecommendationsTest {
    private final UUID tenantId = UUID.randomUUID();
    private final AssessmentOperations operations = mock(AssessmentOperations.class);
    private final TenantRegistry tenants = mock(TenantRegistry.class);
    private final PlanGate plans = mock(PlanGate.class);
    private final AssessmentController controller = new AssessmentController(operations, tenants, plans);

    @BeforeEach
    void authenticate() {
        var jwt = Jwt.withTokenValue("fixture").header("alg", "none").subject("analista")
                .claim("tenant_id", tenantId.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        plan("PRO");
        when(plans.isAvailable("PRO", Feature.RECOMENDACIONES)).thenReturn(true);
    }

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void postYGetPublicanElSnapshotPersistidoConElDecimalExacto() {
        var assessment = stored("VERDE", new BigDecimal("4123.25"));
        var result = assessment.toResult();
        when(operations.create(any())).thenReturn(result);
        when(operations.get(tenantId, result.id())).thenReturn(result);

        var post = controller.crearEvaluacion("key", new EvaluacionNueva()
                .titularDocumento("CI-DEMO-1001").consentId(UUID.randomUUID()));
        var get = controller.obtenerEvaluacion(result.id()).getBody();
        assertThat(post.getStatusCode().value()).isEqualTo(201);
        assertThat(post.getBody().getRecomendacionesDisponibles()).isTrue();
        assertThat(get.getRecomendaciones()).isEqualTo(post.getBody().getRecomendaciones());
        assertThat(get.getRecomendaciones()).singleElement().satisfies(product -> {
            assertThat(product.getTipo().getValue()).isEqualTo("VERDE");
            assertThat(product.getMontoRecomendado()).isEqualTo("4123.25");
        });
        verify(operations).get(tenantId, result.id());
        verify(tenants, org.mockito.Mockito.times(2)).requireById(TenantId.of(tenantId));
    }

    @Test
    void freeConservaElScorePeroOcultaInclusoUnSnapshotDeProductosExistente() {
        plan("FREE");
        var result = stored("MICROCREDITO", new BigDecimal("5000.00")).toResult();
        when(operations.get(tenantId, result.id())).thenReturn(result);
        var body = controller.obtenerEvaluacion(result.id()).getBody();
        assertThat(body.getScore()).isEqualTo(800);
        assertThat(body.getRecomendacionesDisponibles()).isFalse();
        assertThat(body.getRecomendaciones()).isEmpty();
        verify(plans).isAvailable("FREE", Feature.RECOMENDACIONES);
    }

    @Test
    void volverALeerAplicaElPermisoActualYNoRevelaProductosTrasUnaBajaDePlan() {
        var result = stored("PYME", new BigDecimal("10000.00")).toResult();
        when(operations.get(tenantId, result.id())).thenReturn(result);
        assertThat(controller.obtenerEvaluacion(result.id()).getBody().getRecomendaciones()).hasSize(1);
        plan("FREE");
        assertThat(controller.obtenerEvaluacion(result.id()).getBody().getRecomendaciones()).isEmpty();
    }

    @Test
    void unaEvaluacionHistoricaSinSnapshotTieneUnaListaVacia() {
        assertThat(assessment().toResult().recommendations()).isEmpty();
    }

    @Test
    void noPublicaSnapshotsIncompletos() {
        assertThat(stored(null, new BigDecimal("5000.00")).toResult().recommendations()).isEmpty();
        assertThat(stored("MICROCREDITO", null).toResult().recommendations()).isEmpty();
    }

    @Test
    void proSinProductoElegibleConservaElPermisoConUnaListaVacia() {
        var result = assessment().toResult();
        when(operations.get(tenantId, result.id())).thenReturn(result);
        var body = controller.obtenerEvaluacion(result.id()).getBody();
        assertThat(body.getRecomendacionesDisponibles()).isTrue();
        assertThat(body.getRecomendaciones()).isEmpty();
    }

    private CreditAssessment assessment() {
        return new CreditAssessment(tenantId, UUID.randomUUID(), UUID.randomUUID(), "key", "hash",
                "Capital de trabajo", new BigDecimal("5000.00"), 12, AssessmentStatus.REVISION_MANUAL,
                new BasicScoreResult(800, ScoreBand.A, List.of(), "scoring-v2"), "correlation");
    }

    private CreditAssessment stored(String type, BigDecimal amount) {
        var assessment = assessment();
        assessment.captureAnalytics(new AssessmentAnalyticsSnapshot.Snapshot("CAPITAL_TRABAJO", type, amount), 10);
        return assessment;
    }

    private void plan(String plan) {
        when(tenants.requireById(TenantId.of(tenantId))).thenReturn(
                new TenantInfo(tenantId, "Tenant", "nit", "IMF", plan, "BO", "BOB", true));
    }
}
