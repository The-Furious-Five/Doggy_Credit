package com.doggycredit.scoring.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.doggycredit.scoring.BasicScoreResult;
import com.doggycredit.scoring.ScoreBand;
import com.doggycredit.scoring.ScoringFeatures;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** D-02: vector, imputación neutra y escala del score avanzado, con el modelo real. */
class AdvancedScorerTest {

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
	void elVectorSigueElOrdenDelModeloYReproduceElGoldenSet() throws IOException {
		for (Map<String, Object> profile : goldenSet()) {
			@SuppressWarnings("unchecked")
			Map<String, Number> byName = (Map<String, Number>) profile.get("features");
			@SuppressWarnings("unchecked")
			List<Number> ordered = (List<Number>) profile.get("featuresOrdered");

			float[] vector = scorer.vector(features(byName));
			for (int i = 0; i < vector.length; i++) {
				assertThat(vector[i]).isEqualTo(ordered.get(i).floatValue());
			}
			double expectedPd = ((Number) profile.get("expectedPd")).doubleValue();
			BasicScoreResult result = scorer.score(features(byName));
			assertThat(result.score()).isEqualTo(AdvancedScorer.scoreFromProbability(
					model.score(vector).probabilityOfDefault()));
			assertThat((double) model.score(vector).probabilityOfDefault()).isCloseTo(expectedPd, within(1e-6));
			assertThat(result.modelVersion()).isEqualTo("scoring-v2");
		}
	}

	@Test
	void sinDatosCadaVariableQuedaEnSuMediaYElScoreEsElDelInterceptoSolo() {
		ScoringFeatures empty = new ScoringFeatures(null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null);
		float[] vector = scorer.vector(empty);
		for (int i = 0; i < vector.length; i++) {
			assertThat(vector[i]).isEqualTo((float) parameters.features().get(i).mean());
		}
		double pdIntercept = 1 / (1 + Math.exp(-parameters.intercept()));
		assertThat((double) model.score(vector).probabilityOfDefault()).isCloseTo(pdIntercept, within(1e-5));
	}

	@Test
	void lasRazonesSumanLoQueSeAlejaDelScoreBaseYVanOrdenadasPorImpacto() throws IOException {
		for (Map<String, Object> profile : goldenSet()) {
			@SuppressWarnings("unchecked")
			Map<String, Number> byName = (Map<String, Number>) profile.get("features");
			BasicScoreResult result = scorer.score(features(byName));
			assertThat(result.reasons()).hasSize(4);
			assertThat(result.reasons()).isSortedAccordingTo(java.util.Comparator.comparingInt(
					(com.doggycredit.scoring.ScoreReason r) -> Math.abs(r.pointImpact())).reversed());

			// Con las 15 contribuciones, intercepto + Σ c_i reconstruye el score del modelo.
			float[] vector = scorer.vector(features(byName));
			double logit = parameters.intercept();
			for (int i = 0; i < vector.length; i++) {
				ModelParameters.Feature f = parameters.features().get(i);
				logit += f.coefficient() * (vector[i] - f.mean()) / f.scale();
			}
			double pd = 1 / (1 + Math.exp(-logit));
			assertThat(AdvancedScorer.scoreFromProbability(pd)).isCloseTo(result.score(), within(1));
		}
	}

	@Test
	void laEscalaEsMonotonaYRespetaLasBandas() {
		assertThat(AdvancedScorer.scoreFromProbability(0.20)).isEqualTo(600);
		assertThat(AdvancedScorer.scoreFromProbability(0.50)).isEqualTo(500);
		assertThat(AdvancedScorer.scoreFromProbability(0.01)).isGreaterThan(AdvancedScorer.scoreFromProbability(0.05));
		assertThat(AdvancedScorer.scoreFromProbability(0.0)).isBetween(0, 1000);
		assertThat(AdvancedScorer.scoreFromProbability(1.0)).isBetween(0, 1000);
		assertThat(AdvancedScorer.band(750)).isEqualTo(ScoreBand.A);
		assertThat(AdvancedScorer.band(749)).isEqualTo(ScoreBand.B);
		assertThat(AdvancedScorer.band(600)).isEqualTo(ScoreBand.B);
		assertThat(AdvancedScorer.band(450)).isEqualTo(ScoreBand.C);
		assertThat(AdvancedScorer.band(300)).isEqualTo(ScoreBand.D);
		assertThat(AdvancedScorer.band(299)).isEqualTo(ScoreBand.E);
	}

	private static ScoringFeatures features(Map<String, Number> v) {
		return new ScoringFeatures(d(v, "dti"), d(v, "mora_max_12m_dias"), d(v, "num_acreedores"),
				d(v, "peor_calificacion_24m"), d(v, "antiguedad_sistema_financiero_meses"),
				d(v, "ingreso_promedio_6m_bob"), d(v, "ingreso_volatilidad_6m"), d(v, "actividad_wallet_90d"),
				d(v, "es_pyme"), d(v, "ventas_pyme_promedio_bob"), d(v, "saldo_promedio_cuentas_3m_bob"),
				d(v, "ratio_egreso_ingreso_6m"), d(v, "meses_flujo_negativo_6m"), d(v, "saldo_promedio_wallet_bob"),
				d(v, "antiguedad_wallet_meses"));
	}

	private static Double d(Map<String, Number> values, String name) {
		return values.get(name).doubleValue();
	}

	private static List<Map<String, Object>> goldenSet() throws IOException {
		try (InputStream in = AdvancedScorerTest.class.getClassLoader().getResourceAsStream("golden_set.json")) {
			return JsonMapper.builder().build().readValue(in, new TypeReference<List<Map<String, Object>>>() {
			});
		}
	}

}
