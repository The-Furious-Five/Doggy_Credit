package com.doggycredit.scoring.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import com.doggycredit.scoring.BasicScoreResult;
import com.doggycredit.scoring.ScoreBand;
import com.doggycredit.scoring.ScoreReason;
import com.doggycredit.scoring.ScorecardInput;

class DataDrivenBasicScorecardTest {

	private final DataDrivenBasicScorecard scorecard = new DataDrivenBasicScorecard();

	@Test
	void puntuaDeCeroAMilConBandasYRazonesOrdenadas() {
		BasicScoreResult result = scorecard.score(new ScorecardInput(OptionalInt.of(120), 2, 2,
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
		BasicScoreResult result = scorecard.score(new ScorecardInput(OptionalInt.of(0), 48, 60,
				new BigDecimal("3000"), new BigDecimal("2000"), 12, true));

		assertThat(result.score()).isEqualTo(850);
		assertThat(result.band()).isEqualTo(ScoreBand.A);
		assertThat(result.reasons()).extracting(ScoreReason::code)
			.contains("SIN_MORA_12M", "ACTIVIDAD_MAYOR_24_MESES", "CON_REGISTRO_BURO");
	}

	@Test
	void conElBuroCaidoLaMoraEsDesconocidaYNuncaSeAsumeSinMora() {
		// Demo, punto 6 (guía §13.3): con el buró apagado nada se inventa.
		// Mismo titular "perfecto", pero sin informe del buró.
		BasicScoreResult result = scorecard.score(new ScorecardInput(OptionalInt.empty(), 48, 0,
				new BigDecimal("3000"), new BigDecimal("2000"), 12, false));

		assertThat(result.reasons()).extracting(ScoreReason::code)
			.doesNotContain("SIN_MORA_12M")
			.first().isEqualTo("MORA_DESCONOCIDA_BURO_NO_DISPONIBLE");
		assertThat(result.reasons().get(0).pointImpact()).isEqualTo(-350);
		// 850 − 350 (mora desconocida) − 100 (historial) − 60 (sin registro) = 340
		assertThat(result.score()).isEqualTo(340);
		assertThat(result.band()).isEqualTo(ScoreBand.D);
	}

	@Test
	void aIgualImpactoLasRazonesSiguenElOrdenDelYamlYSonEstables() {
		ScorecardInput perfecto = new ScorecardInput(OptionalInt.of(0), 48, 60,
				new BigDecimal("3000"), new BigDecimal("2000"), 12, true);

		BasicScoreResult primera = scorecard.score(perfecto);
		BasicScoreResult segunda = scorecard.score(perfecto);

		assertThat(primera.reasons()).extracting(ScoreReason::code).containsExactly(
				"SIN_MORA_12M", "ACTIVIDAD_MAYOR_24_MESES", "HISTORIAL_MAYOR_24_MESES",
				"MONTO_HASTA_3_INGRESOS", "PLAZO_HASTA_12_MESES", "CON_REGISTRO_BURO");
		assertThat(segunda.reasons()).isEqualTo(primera.reasons());
	}

}
