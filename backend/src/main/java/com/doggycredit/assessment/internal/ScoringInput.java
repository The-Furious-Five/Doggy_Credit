package com.doggycredit.assessment.internal;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.OptionalInt;

import com.doggycredit.scoring.ScorecardInput;
import com.doggycredit.scoring.ScoringFeatures;

import tools.jackson.databind.json.JsonMapper;

/**
 * C-12 (LOS-113), RF-SCO-04: el vector de variables con el que se calculó un score, tal
 * como se guarda en assessment.credit_assessments.scoring_input. Lleva las dos entradas
 * (scorecard básico y modelo avanzado) porque la versión guardada decide cuál se usó.
 *
 * <p>Los datos del buró que faltaron van en {@code null}, nunca en 0: «vacío» no es «sin
 * mora» (RN-15). Los montos van como texto para no perder la escala al ir y volver.
 */
record ScoringInput(String format, Integer maxDaysPastDue12m, int activitySeniorityMonths,
		Integer financialSystemSeniorityMonths, String requestedAmount, String monthlyIncome, int termMonths,
		Boolean bureauRecordFound, ScoringFeatures features) {

	/** Versión de este formato: si cambia, las evaluaciones viejas siguen leyéndose con la suya. */
	static final String FORMAT = "scoring-input-v1";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	static ScoringInput of(ScorecardInput basic, ScoringFeatures features) {
		return new ScoringInput(FORMAT, boxed(basic.maxDaysPastDue12m()), basic.activitySeniorityMonths(),
				boxed(basic.financialSystemSeniorityMonths()), basic.requestedAmount().toPlainString(),
				basic.monthlyIncome().toPlainString(), basic.termMonths(), basic.bureauRecordFound().orElse(null),
				features);
	}

	static ScoringInput fromJson(String json) {
		ScoringInput input = JSON.readValue(json, ScoringInput.class);
		if (!FORMAT.equals(input.format())) {
			throw new IllegalStateException("Formato de scoring_input desconocido: " + input.format());
		}
		return input;
	}

	ScorecardInput basicInput() {
		return new ScorecardInput(unboxed(maxDaysPastDue12m), activitySeniorityMonths,
				unboxed(financialSystemSeniorityMonths), new BigDecimal(requestedAmount), new BigDecimal(monthlyIncome),
				termMonths, Optional.ofNullable(bureauRecordFound));
	}

	String toJson() {
		return JSON.writeValueAsString(this);
	}

	/** SHA-256 en hexadecimal de los bytes exactos que se guardan. */
	static String sha256(String json) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("La JVM no tiene SHA-256", e);
		}
	}

	private static Integer boxed(OptionalInt value) {
		return value.isPresent() ? value.getAsInt() : null;
	}

	private static OptionalInt unboxed(Integer value) {
		return value == null ? OptionalInt.empty() : OptionalInt.of(value);
	}

}
