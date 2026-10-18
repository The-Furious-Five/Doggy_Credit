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
            assertThat(recommendations.first(tenant, "BOB", ScoreBand.B, HolderType.PERSONA,
                    need(NeedCategory.EFICIENCIA_ENERGETICA, "3000.00", "1000.00", 12)))
                .contains(ranked.getFirst());
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
