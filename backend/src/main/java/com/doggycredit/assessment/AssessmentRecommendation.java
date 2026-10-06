package com.doggycredit.assessment;

import java.math.BigDecimal;

/** C-06: un producto recomendado en una evaluación. Es una recomendación, no una aprobación. */
public record AssessmentRecommendation(String productType, String currency, BigDecimal suggestedAmount,
		String reason) {
}
