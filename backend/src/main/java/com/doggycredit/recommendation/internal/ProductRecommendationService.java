package com.doggycredit.recommendation.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.doggycredit.recommendation.CreditNeeds.CreditNeed;
import com.doggycredit.recommendation.CreditNeeds.HolderType;
import com.doggycredit.recommendation.NeedCategory;
import com.doggycredit.recommendation.ProductRecommendations;
import com.doggycredit.scoring.ScoreBand;

@Service
class ProductRecommendationService implements ProductRecommendations {

    private final JdbcTemplate jdbc;
    private final EligibilityEngine eligibility;

    ProductRecommendationService(JdbcTemplate jdbc, EligibilityEngine eligibility) {
        this.jdbc = jdbc;
        this.eligibility = eligibility;
    }

    @Override
    public List<Recommendation> rank(UUID tenantId, String currency, ScoreBand band,
            HolderType holderType, CreditNeed need) {
        if (need.suggestedAmount().signum() <= 0) {
            return List.of();
        }
        var catalog = jdbc.query("""
                SELECT id, product_type, min_amount, max_amount, min_term_months,
                       max_term_months, annual_rate, min_band, active
                FROM recommendation.products WHERE tenant_id = ? AND currency = ? AND active
                ORDER BY product_type
                """, (rs, row) -> new CreditProduct(rs.getObject("id", UUID.class),
                CreditProduct.Type.valueOf(rs.getString("product_type")), rs.getBigDecimal("min_amount"),
                rs.getBigDecimal("max_amount"), rs.getInt("min_term_months"), rs.getInt("max_term_months"),
                rs.getBigDecimal("annual_rate"), ScoreBand.valueOf(rs.getString("min_band")), rs.getBoolean("active")),
                tenantId, currency);
        // CA-04: ajustar la capacidad a la tasa y al máximo de cada producto,
        // conservando el ranking del motor C-03 y sin recomendar debajo del mínimo.
        var request = new EligibilityEngine.Request(band, need.suggestedAmount(), need.suggestedTermMonths(),
                holderType == HolderType.PYME ? EligibilityEngine.HolderType.EMPRESA : EligibilityEngine.HolderType.PERSONA,
                need.category() == NeedCategory.EFICIENCIA_ENERGETICA);
        var ranking = eligibility.evaluate(request, catalog).stream().map(EligibilityEngine.Evaluation::product).toList();
        return catalog.stream().map(product -> {
            BigDecimal affordable = CreditNeedAnalyzer.presentValue(need.maxInstallment(),
                    product.annualRate().doubleValue() / 100, need.suggestedTermMonths());
            BigDecimal amount = need.suggestedAmount().min(product.maxAmount()).min(affordable)
                    .setScale(2, java.math.RoundingMode.DOWN);
            return new Candidate(product, amount);
        }).map(candidate -> new Assessed(candidate, eligibility.evaluate(new EligibilityEngine.Request(request.band(),
                candidate.amount(), request.termMonths(), request.holderType(), request.greenDestination()),
                java.util.List.of(candidate.product())).getFirst()))
            .filter(assessed -> assessed.evaluation().eligible())
            .sorted(java.util.Comparator.comparingInt(assessed -> ranking.indexOf(assessed.candidate().product())))
            .map(assessed -> new Recommendation(assessed.candidate().product().type().name(),
                    assessed.candidate().amount(), assessed.evaluation().reason()))
            .toList();
    }

    private record Candidate(CreditProduct product, BigDecimal amount) { }

    private record Assessed(Candidate candidate, EligibilityEngine.Evaluation evaluation) { }
}
