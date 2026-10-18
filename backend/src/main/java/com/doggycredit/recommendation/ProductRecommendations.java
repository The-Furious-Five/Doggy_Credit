package com.doggycredit.recommendation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.doggycredit.recommendation.CreditNeeds.CreditNeed;
import com.doggycredit.recommendation.CreditNeeds.HolderType;
import com.doggycredit.scoring.ScoreBand;

/** API pública de C-03: productos elegibles del catálogo actual del tenant. */
public interface ProductRecommendations {

    /** E-16: evalúa una vez el catálogo activo; capacidad desconocida nunca califica. */
    CatalogAssessment assess(UUID tenantId, String currency, ScoreBand band,
            HolderType holderType, CreditNeed need, boolean capacityKnown);

    /** C-06: todos los productos elegibles, del más adecuado al menos. */
    default List<Recommendation> rank(UUID tenantId, String currency, ScoreBand band,
            HolderType holderType, CreditNeed need) {
        return assess(tenantId, currency, band, holderType, need, true).recommendations();
    }

    /** El más adecuado de {@link #rank}. */
    default Optional<Recommendation> first(UUID tenantId, String currency, ScoreBand band,
            HolderType holderType, CreditNeed need) {
        return rank(tenantId, currency, band, holderType, need).stream().findFirst();
    }

    record CatalogAssessment(List<ProductEligibility> eligibility, List<Recommendation> recommendations) {
        public CatalogAssessment {
            eligibility = List.copyOf(eligibility);
            recommendations = List.copyOf(recommendations);
        }
    }

    record Recommendation(String productType, BigDecimal amount, String reason, int termMonths,
            List<String> reasons) {
        public Recommendation {
            reasons = List.copyOf(reasons);
        }
    }
}
