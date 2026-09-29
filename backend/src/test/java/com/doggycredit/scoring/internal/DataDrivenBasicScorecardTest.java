package com.doggycredit.scoring.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.doggycredit.scoring.BasicScoreResult;
import com.doggycredit.scoring.ScoreBand;
import com.doggycredit.scoring.ScoreReason;
import com.doggycredit.scoring.ScorecardInput;

class DataDrivenBasicScorecardTest {

	private final DataDrivenBasicScorecard scorecard = new DataDrivenBasicScorecard();

	@Test
	void puntuaDeCeroAMilConBandasYRazonesOrdenadas() {
		BasicScoreResult result = scorecard.score(new ScorecardInput(120, 2, 2,
				new BigDecimal("20000"), new BigDecimal("1000"), 60, false));

		assertThat(result.score()).isBetween(0, 1000).isZero();
		assertThat(result.band()).isEqualTo(ScoreBand.E);
		assertThat(result.modelVersion()).isEqualTo("basic-v1");
		assertThat(result.reasons()).hasSizeGreaterThanOrEqualTo(3);
		assertThat(result.reasons().stream().map(reason -> Math.abs(reason.pointImpact())).toList())
			.isSortedAccordingTo((left, right) -> Integer.compare(right, left));
	}

	@Test
	void funcionaSinConstruirNiInvocarElModeloOnnx() {
		BasicScoreResult result = scorecard.score(new ScorecardInput(0, 48, 60,
				new BigDecimal("3000"), new BigDecimal("2000"), 12, true));

		assertThat(result.score()).isEqualTo(850);
		assertThat(result.band()).isEqualTo(ScoreBand.A);
		assertThat(result.reasons()).extracting(ScoreReason::code)
			.contains("SIN_MORA_12M", "ACTIVIDAD_MAYOR_24_MESES", "CON_REGISTRO_BURO");
	}

}
