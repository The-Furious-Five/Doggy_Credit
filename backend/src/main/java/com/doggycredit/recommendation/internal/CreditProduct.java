package com.doggycredit.recommendation.internal;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import com.doggycredit.scoring.ScoreBand;

/** Un producto del catálogo del tenant (tabla recommendation.products, C-02). */
record CreditProduct(UUID id, Type type, BigDecimal minAmount, BigDecimal maxAmount, int minTermMonths,
		int maxTermMonths, BigDecimal annualRate, ScoreBand minBand, boolean active) {

	enum Type {
		MICROCREDITO("Microcrédito"), VERDE("Crédito ecológico"), PYME("Crédito PYME");

		private final String displayName;

		Type(String displayName) {
			this.displayName = displayName;
		}

		String displayName() {
			return displayName;
		}
	}

	CreditProduct {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(minAmount, "minAmount");
		Objects.requireNonNull(maxAmount, "maxAmount");
		Objects.requireNonNull(minBand, "minBand");
	}

}
