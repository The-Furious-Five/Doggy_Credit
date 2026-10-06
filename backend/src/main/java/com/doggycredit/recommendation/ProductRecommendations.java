package com.doggycredit.recommendation;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.doggycredit.recommendation.CreditNeeds.CreditNeed;
import com.doggycredit.recommendation.CreditNeeds.HolderType;
import com.doggycredit.scoring.ScoreBand;

/** API pública de C-03: primer producto elegible del catálogo actual del tenant. */
public interface ProductRecommendations {

    Optional<Recommendation> first(UUID tenantId, String currency, ScoreBand band,
            HolderType holderType, CreditNeed need);

    record Recommendation(String productType, BigDecimal amount) { }
}
