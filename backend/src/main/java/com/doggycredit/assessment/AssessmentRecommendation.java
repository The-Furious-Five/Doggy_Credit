package com.doggycredit.assessment;

import java.math.BigDecimal;
import java.util.List;

/** C-06: un producto recomendado en una evaluación. Es una recomendación, no una aprobación. */
public record AssessmentRecommendation(String productType, String currency, BigDecimal suggestedAmount,
		String reason, Integer suggestedTermMonths, List<String> reasons) {

    public AssessmentRecommendation {
        reasons = reasons == null ? null : List.copyOf(reasons);
    }
}
