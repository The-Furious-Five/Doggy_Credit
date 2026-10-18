package com.doggycredit.recommendation.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.recommendation.CreditNeeds.CreditNeed;
import com.doggycredit.recommendation.CreditNeeds.HolderType;
import com.doggycredit.recommendation.NeedCategory;
import com.doggycredit.recommendation.ProductRecommendations;
import com.doggycredit.recommendation.ProductRecommendations.Recommendation;
import com.doggycredit.recommendation.ProductEligibility;
import com.doggycredit.shared.TenantContext;
import com.doggycredit.scoring.ScoreBand;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProductRecommendationServiceTests {

    @Autowired ProductRecommendations recommendations;
    @Autowired PostgreSQLContainer postgres;

    @Test
    void ajustaElMontoAlCatalogoYRespetaElRankingVerde() throws Exception {
        UUID tenant = UUID.randomUUID();
        product(tenant, "MICROCREDITO", true, "BOB");
        product(tenant, "VERDE", true, "BOB");
        asTenant(tenant, () -> {
            var capital = recommendations.first(tenant, "BOB", ScoreBand.B, HolderType.PERSONA,
                    need(NeedCategory.CAPITAL_TRABAJO, "15000.00", "1000.00", 12)).orElseThrow();
            assertThat(capital.productType()).isEqualTo("MICROCREDITO");
            assertThat(capital.amount()).isEqualByComparingTo("10000.00");
            var green = recommendations.first(tenant, "BOB", ScoreBand.B, HolderType.PERSONA,
                    need(NeedCategory.EFICIENCIA_ENERGETICA, "3000.00", "1000.00", 12)).orElseThrow();
            assertThat(green.productType()).isEqualTo("VERDE");
            assertThat(green.amount()).isEqualByComparingTo("3000.00");
        });
    }

    @Test
    void rankDevuelveTodasLasElegiblesEnOrdenConSuRazon() throws Exception {
        // C-06: con destino verde aplican VERDE y MICROCREDITO; el verde va primero.
        UUID tenant = UUID.randomUUID();
        product(tenant, "MICROCREDITO", true, "BOB");
        product(tenant, "VERDE", true, "BOB");
        asTenant(tenant, () -> {
            var ranked = recommendations.rank(tenant, "BOB", ScoreBand.B, HolderType.PERSONA,
                    need(NeedCategory.EFICIENCIA_ENERGETICA, "3000.00", "1000.00", 12));
            assertThat(ranked).extracting(Recommendation::productType).containsExactly("VERDE", "MICROCREDITO");
            assertThat(ranked).allSatisfy(r -> assertThat(r.reason()).isNotBlank());
            assertThat(ranked).allSatisfy(r -> {
                assertThat(r.termMonths()).isBetween(6, 36);
                assertThat(r.reasons()).hasSizeGreaterThanOrEqualTo(2).doesNotHaveDuplicates();
            });
            assertThat(recommendations.first(tenant, "BOB", ScoreBand.B, HolderType.PERSONA,
                    need(NeedCategory.EFICIENCIA_ENERGETICA, "3000.00", "1000.00", 12)))
                .contains(ranked.getFirst());
        });
    }

    @Test
    void pymeRequiereEmpresaYLosTresProductosExplicanSusSeisCriterios() throws Exception {
        UUID tenant = UUID.randomUUID();
        for (String type : List.of("MICROCREDITO", "VERDE", "PYME")) product(tenant, type, true, "BOB");
        asTenant(tenant, () -> {
            var person = recommendations.assess(tenant, "BOB", ScoreBand.B, HolderType.PERSONA,
                    need(NeedCategory.EFICIENCIA_ENERGETICA, "3000", "1000", 12), true);
            assertThat(person.eligibility()).hasSize(3).allSatisfy(p -> {
                assertThat(p.criteria()).hasSize(6);
                assertThat(p.eligible()).isEqualTo(p.criteria().stream().allMatch(ProductEligibility.Criterion::met));
                assertThat(p.criteria()).allSatisfy(c -> assertThat(c.detail()).isNotBlank());
            });
            assertThat(person.recommendations()).extracting(Recommendation::productType)
                    .containsExactly("VERDE", "MICROCREDITO");
            var company = recommendations.assess(tenant, "BOB", ScoreBand.B, HolderType.PYME,
                    need(NeedCategory.CAPITAL_TRABAJO, "3000", "1000", 12), true);
            assertThat(company.recommendations()).extracting(Recommendation::productType).containsExactly("PYME");
            assertThat(company.eligibility().stream().filter(p -> p.productType().equals("MICROCREDITO"))
                    .findFirst().orElseThrow().criteria()).anyMatch(c -> c.code() == ProductEligibility.Code.TIPO_TITULAR && !c.met());
        });
    }

    @Test
    void cadaCuotaSugeridaRespetaLaCapacidadALaTasaRealDelProducto() throws Exception {
        UUID tenant = UUID.randomUUID();
        product(tenant, "MICROCREDITO", true, "BOB");
        asTenant(tenant, () -> {
            var ranked = recommendations.rank(tenant, "BOB", ScoreBand.B, HolderType.PERSONA,
                    need(NeedCategory.CAPITAL_TRABAJO, "15000", "100", 12));
            assertThat(ranked).hasSize(1).allSatisfy(r -> {
                assertThat(r.amount()).isBetween(new BigDecimal("1000"), new BigDecimal("10000"));
                double rate = 0.115 / 12;
                double payment = r.amount().doubleValue() * rate / (1 - Math.pow(1 + rate, -r.termMonths()));
                assertThat(payment).isLessThanOrEqualTo(100.0);
                assertThat(r.reasons()).hasSizeGreaterThanOrEqualTo(2);
            });
        });
    }

    @Test
    void laCapacidadCeroODesconocidaExplicaElCatalogoPeroNoRecomienda() throws Exception {
        UUID tenant = UUID.randomUUID();
        product(tenant, "MICROCREDITO", true, "BOB");
        asTenant(tenant, () -> {
            for (boolean known : List.of(true, false)) {
                var result = recommendations.assess(tenant, "BOB", ScoreBand.B, HolderType.PERSONA,
                        need(NeedCategory.CAPITAL_TRABAJO, "0", "0", 12), known);
                assertThat(result.eligibility()).hasSize(1).noneMatch(ProductEligibility::eligible);
                assertThat(result.recommendations()).isEmpty();
                assertThat(result.eligibility().getFirst().criteria())
                        .anyMatch(c -> c.code() == ProductEligibility.Code.CAPACIDAD_PAGO && !c.met());
            }
        });
    }

    @Test
    void noRecomiendaSinCapacidadBandaPlazoOMinimoElegibles() throws Exception {
        UUID tenant = UUID.randomUUID();
        product(tenant, "MICROCREDITO", true, "BOB");
        asTenant(tenant, () -> {
            assertThat(recommendations.first(tenant, "BOB", ScoreBand.B, HolderType.PERSONA,
                    need(NeedCategory.CAPITAL_TRABAJO, "0.00", "0.00", 12))).isEmpty();
            assertThat(recommendations.first(tenant, "BOB", ScoreBand.E, HolderType.PERSONA,
                    need(NeedCategory.CAPITAL_TRABAJO, "3000.00", "1000.00", 12))).isEmpty();
            assertThat(recommendations.first(tenant, "BOB", ScoreBand.B, HolderType.PERSONA,
                    need(NeedCategory.CAPITAL_TRABAJO, "3000.00", "1000.00", 60))).isEmpty();
            assertThat(recommendations.first(tenant, "BOB", ScoreBand.B, HolderType.PERSONA,
                    need(NeedCategory.CAPITAL_TRABAJO, "500.00", "1000.00", 12))).isEmpty();
        });
    }

    @Test
    void soloLeeProductosActivosDeLaMonedaYDelTenantActual() throws Exception {
        UUID tenant = UUID.randomUUID(), other = UUID.randomUUID();
        product(tenant, "MICROCREDITO", false, "BOB");
        product(tenant, "VERDE", true, "USD");
        product(other, "MICROCREDITO", true, "BOB");
        asTenant(tenant, () -> {
            var need = need(NeedCategory.CAPITAL_TRABAJO, "3000.00", "1000.00", 12);
            assertThat(recommendations.first(tenant, "BOB", ScoreBand.B, HolderType.PERSONA, need)).isEmpty();
            assertThat(recommendations.first(other, "BOB", ScoreBand.B, HolderType.PERSONA, need)).isEmpty();
            var eligibility = recommendations.assess(tenant, "BOB", ScoreBand.B, HolderType.PERSONA, need, true).eligibility();
            assertThat(eligibility).hasSize(1).allSatisfy(p -> {
                assertThat(p.productType()).isEqualTo("VERDE");
                assertThat(p.currency()).isEqualTo("USD");
                assertThat(p.eligible()).isFalse();
                assertThat(p.criteria()).anyMatch(c -> c.code() == ProductEligibility.Code.RANGO_MONTO && !c.met());
            });
        });
    }

    private CreditNeed need(NeedCategory category, String amount, String installment, int term) {
        return new CreditNeed(category, new BigDecimal("5000"), true,
                new BigDecimal(installment), new BigDecimal(amount), term, List.of());
    }

    private void product(UUID tenant, String type, boolean active, String currency) throws Exception {
        // La aplicación no tiene INSERT sobre el catálogo: mismo rol que producción.
        try (var admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = admin.prepareStatement("""
                INSERT INTO recommendation.products (id, tenant_id, product_type, currency, min_amount,
                  max_amount, min_term_months, max_term_months, annual_rate, min_band, active)
                VALUES (?, ?, ?, ?, 1000, 10000, 6, 36, 11.5, 'C', ?)
                """)) {
            statement.setObject(1, UUID.randomUUID()); statement.setObject(2, tenant);
            statement.setString(3, type); statement.setString(4, currency); statement.setBoolean(5, active);
            statement.executeUpdate();
        }
    }

    private void asTenant(UUID tenant, Runnable work) {
        TenantContext.set(tenant);
        try { work.run(); } finally { TenantContext.clear(); }
    }
}
