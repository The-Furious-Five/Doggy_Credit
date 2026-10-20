package com.doggycredit.scoring.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.doggycredit.scoring.BasicScoreResult;
import com.doggycredit.scoring.ScoreBand;
import com.doggycredit.scoring.ScoreFactor;
import com.doggycredit.scoring.ScoreReason;
import com.doggycredit.scoring.ScoringFeatures;
import com.doggycredit.scoring.ScoringModel;

/**
 * Score avanzado (plan PRO, D-02): arma el vector en el orden del modelo,
 * imputa con la media lo que falta, corre el ONNX y lleva la probabilidad de
 * incumplimiento a la escala 0–1000 de las bandas del producto.
 */
class AdvancedScorer {

	/** 600 puntos con odds 4:1 (PD 20 %, la tasa del portafolio sintético); cada 50 puntos duplican las odds. */
	static final int BASE_SCORE = 600;
	static final double BASE_ODDS = 4.0;
	static final double POINTS_TO_DOUBLE_ODDS = 50.0;
	static final double POINTS_PER_LOGIT = POINTS_TO_DOUBLE_ODDS / Math.log(2);


	private final ScoringModel model;
	private final ModelParameters parameters;

	AdvancedScorer(ScoringModel model, ModelParameters parameters) {
		this.model = model;
		this.parameters = parameters;
	}

	BasicScoreResult score(ScoringFeatures features) {
		float[] vector = vector(features);
		float probabilityOfDefault = model.score(vector).probabilityOfDefault();
		int score = scoreFromProbability(probabilityOfDefault);
		return new BasicScoreResult(score, band(score), reasons(vector), version());
	}

	/** La versión que queda en cada evaluación, ej. "scoring-v2". */
	String version() {
		return "scoring-" + parameters.modelVersion();
	}

	/**
	 * D-05: una razón por {@link ScoreFactor}, ordenadas por impacto (RF-SCO-03).
	 * Contribución exacta de cada variable al logit, c_i = β_i·(x_i − x̄_i)/s_i,
	 * llevada a puntos del score (positiva sube la PD y resta puntos) y sumada
	 * por factor. Una variable imputada aporta 0. Sumadas, las razones dan la
	 * distancia entre este score y el de un titular con todas las variables en
	 * su media ({@link #scoreAtMeans()}).
	 */
	List<ScoreReason> reasons(float[] vector) {
		Map<ScoreFactor, Double> points = new EnumMap<>(ScoreFactor.class);
		List<ModelParameters.Feature> order = parameters.features();
		for (int i = 0; i < order.size(); i++) {
			ModelParameters.Feature feature = order.get(i);
			double contribution = feature.coefficient() * (vector[i] - feature.mean()) / feature.scale();
			points.merge(ScoreFactor.of(feature.name()), -contribution * POINTS_PER_LOGIT, Double::sum);
		}
		List<ScoreReason> reasons = new ArrayList<>();
		for (ScoreFactor factor : ScoreFactor.values()) {
			reasons.add(new ScoreReason(factor.name(), (int) Math.round(points.getOrDefault(factor, 0.0))));
		}
		reasons.sort(Comparator.comparingInt((ScoreReason reason) -> Math.abs(reason.pointImpact())).reversed());
		return List.copyOf(reasons);
	}

	/** Score de un titular con todas las variables en su media (solo el intercepto), sin redondear. */
	double scoreAtMeans() {
		return BASE_SCORE + POINTS_PER_LOGIT * (-parameters.intercept() - Math.log(BASE_ODDS));
	}

	float[] vector(ScoringFeatures features) {
		Map<String, Double> values = features.byName();
		List<ModelParameters.Feature> order = parameters.features();
		if (!values.keySet().equals(order.stream().map(ModelParameters.Feature::name)
			.collect(java.util.stream.Collectors.toSet()))) {
			throw new IllegalStateException("ScoringFeatures no coincide con el feature_set "
					+ parameters.featureSetVersion() + " del modelo");
		}
		float[] vector = new float[order.size()];
		for (int i = 0; i < vector.length; i++) {
			Double value = values.get(order.get(i).name());
			vector[i] = (float) (value == null ? order.get(i).mean() : value);
		}
		return vector;
	}

	static int scoreFromProbability(double probabilityOfDefault) {
		double pd = Math.min(Math.max(probabilityOfDefault, 1e-6), 1 - 1e-6);
		double odds = (1 - pd) / pd;
		double score = BASE_SCORE + POINTS_PER_LOGIT * Math.log(odds / BASE_ODDS);
		return (int) Math.round(Math.max(0, Math.min(1000, score)));
	}

	/** Bandas del producto: A ≥ 750 · B 600–749 · C 450–599 · D 300–449 · E < 300. */
	static ScoreBand band(int score) {
		if (score >= 750) {
			return ScoreBand.A;
		}
		if (score >= 600) {
			return ScoreBand.B;
		}
		if (score >= 450) {
			return ScoreBand.C;
		}
		return score >= 300 ? ScoreBand.D : ScoreBand.E;
	}

}
