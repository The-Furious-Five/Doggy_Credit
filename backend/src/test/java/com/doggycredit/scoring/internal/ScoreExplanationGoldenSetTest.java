package com.doggycredit.scoring.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.doggycredit.scoring.BasicScoreResult;
import com.doggycredit.scoring.ScoreFactor;
import com.doggycredit.scoring.ScoreReason;
import com.doggycredit.scoring.ScoringFeatures;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * D-05 — criterio de "Lista cuando": explicabilidad del score avanzado sobre
 * los 20 perfiles fijos del golden set (ml/model/golden_set.json).
 */
class ScoreExplanationGoldenSetTest {

	private static OnnxScoringAdapter model;
	private static ModelParameters parameters;
	private static AdvancedScorer scorer;

	@BeforeAll
	static void load() {
		model = new OnnxScoringAdapter("v2");
		parameters = ModelParameters.load("v2");
		scorer = new AdvancedScorer(model, parameters);
	}

	@AfterAll
	static void close() {
		model.close();
	}

	@Test
	void cadaVariableDelModeloPerteneceAUnSoloFactor() {
		List<String> grouped = Arrays.stream(ScoreFactor.values()).flatMap(f -> f.features().stream()).toList();
		List<String> modelFeatures = parameters.features().stream().map(ModelParameters.Feature::name).toList();
		assertThat(grouped).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(modelFeatures);
	}

	@Test
	void lasRazonesSonLosCuatroFactoresYExplicanElScore() throws IOException {
		for (Map<String, Object> profile : goldenSet()) {
			BasicScoreResult result = scorer.score(features(profile, Map.of()));

			assertThat(result.reasons()).extracting(ScoreReason::code)
				.containsExactlyInAnyOrder("HISTORIAL_PAGOS", "CAPACIDAD_PAGO", "ESTABILIDAD_INGRESOS", "USO_CREDITO");
			assertThat(result.reasons()).isSortedAccordingTo(java.util.Comparator.comparingInt(
					(ScoreReason r) -> Math.abs(r.pointImpact())).reversed());

			// Las razones suman la distancia al score de un titular promedio (±1 por redondeo de cada factor).
			int explained = result.reasons().stream().mapToInt(ScoreReason::pointImpact).sum();
			assertThat(scorer.scoreAtMeans() + explained)
				.as("perfil #%s", profile.get("id"))
				.isCloseTo(result.score(), within(3.0));
		}
	}

	@Test
	void cadaFactorSeMueveEnLaDireccionDeSusVariables() throws IOException {
		Map<String, Object> profile = goldenSet().getFirst();
		int base = points(scorer.score(features(profile, Map.of())), "HISTORIAL_PAGOS");
		int withArrears = points(scorer.score(features(profile, Map.of("mora_max_12m_dias", 120.0))),
				"HISTORIAL_PAGOS");
		assertThat(withArrears).isLessThan(base);

		int capacity = points(scorer.score(features(profile, Map.of("dti", 0.1))), "CAPACIDAD_PAGO");
		int overIndebted = points(scorer.score(features(profile, Map.of("dti", 1.2))), "CAPACIDAD_PAGO");
		assertThat(overIndebted).isLessThan(capacity);

		int lowIncome = points(scorer.score(features(profile, Map.of("ingreso_promedio_6m_bob", 1000.0))),
				"ESTABILIDAD_INGRESOS");
		int highIncome = points(scorer.score(features(profile, Map.of("ingreso_promedio_6m_bob", 15000.0))),
				"ESTABILIDAD_INGRESOS");
		assertThat(highIncome).isGreaterThan(lowIncome);
	}

	@Test
	void unTitularSinDatosNoTieneFactoresAFavorNiEnContra() {
		BasicScoreResult result = scorer.score(new ScoringFeatures(null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null));
		assertThat(result.reasons()).allSatisfy(reason -> assertThat(reason.pointImpact()).isZero());
	}

	private static int points(BasicScoreResult result, String factor) {
		return result.reasons().stream().filter(r -> r.code().equals(factor)).findFirst().orElseThrow().pointImpact();
	}

	@SuppressWarnings("unchecked")
	private static ScoringFeatures features(Map<String, Object> profile, Map<String, Double> overrides) {
		Map<String, Double> v = new HashMap<>();
		((Map<String, Number>) profile.get("features")).forEach((name, value) -> v.put(name, value.doubleValue()));
		v.putAll(overrides);
		return new ScoringFeatures(v.get("dti"), v.get("mora_max_12m_dias"), v.get("num_acreedores"),
				v.get("peor_calificacion_24m"), v.get("antiguedad_sistema_financiero_meses"),
				v.get("ingreso_promedio_6m_bob"), v.get("ingreso_volatilidad_6m"), v.get("actividad_wallet_90d"),
				v.get("es_pyme"), v.get("ventas_pyme_promedio_bob"), v.get("saldo_promedio_cuentas_3m_bob"),
				v.get("ratio_egreso_ingreso_6m"), v.get("meses_flujo_negativo_6m"), v.get("saldo_promedio_wallet_bob"),
				v.get("antiguedad_wallet_meses"));
	}

	private static List<Map<String, Object>> goldenSet() throws IOException {
		try (InputStream in = ScoreExplanationGoldenSetTest.class.getClassLoader()
			.getResourceAsStream("golden_set.json")) {
			return JsonMapper.builder().build().readValue(in, new TypeReference<List<Map<String, Object>>>() {
			});
		}
	}

}
