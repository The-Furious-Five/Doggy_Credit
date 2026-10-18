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

    /** C-06: todos los productos elegibles, del más adecuado al menos, con su razón legible. */
    List<Recommendation> rank(UUID tenantId, String currency, ScoreBand band,
            HolderType holderType, CreditNeed need);

    /** El más adecuado de {@link #rank}. */
    default Optional<Recommendation> first(UUID tenantId, String currency, ScoreBand band,
            HolderType holderType, CreditNeed need) {
        return rank(tenantId, currency, band, holderType, need).stream().findFirst();
    }

    record Recommendation(String productType, BigDecimal amount, String reason) { }
}
