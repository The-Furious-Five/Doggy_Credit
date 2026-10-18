package com.doggycredit.recommendation.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.doggycredit.recommendation.CreditNeeds.CreditNeed;
import com.doggycredit.recommendation.CreditNeeds.HolderType;
import com.doggycredit.recommendation.NeedCategory;
import com.doggycredit.recommendation.ProductRecommendations;
import com.doggycredit.recommendation.ProductEligibility;
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
    public CatalogAssessment assess(UUID tenantId, String currency, ScoreBand band,
            HolderType holderType, CreditNeed need, boolean capacityKnown) {
        var catalog = jdbc.query("""
                SELECT id, product_type, min_amount, max_amount, min_term_months,
                       max_term_months, annual_rate, min_band, active, currency
                FROM recommendation.products WHERE tenant_id = ? AND active
                ORDER BY product_type
                """, (rs, row) -> new CreditProduct(rs.getObject("id", UUID.class),
                CreditProduct.Type.valueOf(rs.getString("product_type")), rs.getBigDecimal("min_amount"),
                rs.getBigDecimal("max_amount"), rs.getInt("min_term_months"), rs.getInt("max_term_months"),
                rs.getBigDecimal("annual_rate"), ScoreBand.valueOf(rs.getString("min_band")), rs.getBoolean("active"),
                rs.getString("currency")), tenantId);
        // CA-04: ajustar la capacidad a la tasa y al máximo de cada producto,
        // conservando el ranking del motor C-03 y sin recomendar debajo del mínimo.
        var request = new EligibilityEngine.Request(band, need.suggestedAmount(), need.suggestedTermMonths(),
                holderType == HolderType.PYME ? EligibilityEngine.HolderType.EMPRESA : EligibilityEngine.HolderType.PERSONA,
                need.category() == NeedCategory.EFICIENCIA_ENERGETICA, currency,
                capacityKnown ? need.maxInstallment() : null);
        var assessed = catalog.stream().map(product -> {
            BigDecimal amount = need.suggestedAmount().min(product.maxAmount()).max(BigDecimal.ZERO);
            if (capacityKnown && currency.equals(product.currency()) && request.termMonths() > 0) {
                amount = amount.min(LoanPayments.affordableAmount(need.maxInstallment(), product.annualRate(), request.termMonths()));
            }
            amount = amount.setScale(2, RoundingMode.DOWN);
            return new Candidate(product, amount);
        }).map(candidate -> new Assessed(candidate, eligibility.evaluate(new EligibilityEngine.Request(request.band(),
                candidate.amount(), request.termMonths(), request.holderType(), request.greenDestination(), currency,
                request.maxInstallment()),
                java.util.List.of(candidate.product())).getFirst()))
            .sorted(Comparator.comparing(Assessed::evaluation, EligibilityEngine.byFit(request)))
            .toList();
        List<ProductEligibility> criteria = assessed.stream().map(a -> new ProductEligibility(a.candidate().product().id(),
                a.candidate().product().type().name(), a.candidate().product().currency(), a.evaluation().eligible(),
                a.evaluation().criteria().stream().map(c -> new ProductEligibility.Criterion(code(c.code()),
                        c.met(), c.description())).toList())).toList();
        List<Recommendation> ranked = assessed.stream().filter(a -> a.evaluation().eligible())
                .map(a -> new Recommendation(a.candidate().product().type().name(), a.candidate().amount(),
                        a.evaluation().reason(), request.termMonths(), a.evaluation().criteria().stream()
                        .filter(c -> c.code() == EligibilityEngine.Criterion.Code.BAND
                                || c.code() == EligibilityEngine.Criterion.Code.DESTINATION
                                || c.code() == EligibilityEngine.Criterion.Code.PAYMENT_CAPACITY)
                        .map(EligibilityEngine.Criterion::description).toList())).toList();
        return new CatalogAssessment(criteria, ranked);
    }

    private static ProductEligibility.Code code(EligibilityEngine.Criterion.Code code) {
        return switch (code) {
            case BAND -> ProductEligibility.Code.BANDA_MINIMA;
            case AMOUNT -> ProductEligibility.Code.RANGO_MONTO;
            case TERM -> ProductEligibility.Code.RANGO_PLAZO;
            case HOLDER_TYPE -> ProductEligibility.Code.TIPO_TITULAR;
            case DESTINATION -> ProductEligibility.Code.DESTINO;
            case PAYMENT_CAPACITY -> ProductEligibility.Code.CAPACIDAD_PAGO;
        };
    }

    private record Candidate(CreditProduct product, BigDecimal amount) { }

    private record Assessed(Candidate candidate, EligibilityEngine.Evaluation evaluation) { }
}
