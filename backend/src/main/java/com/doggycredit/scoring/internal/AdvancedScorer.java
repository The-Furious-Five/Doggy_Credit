package com.doggycredit.scoring.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.doggycredit.scoring.BasicScoreResult;
import com.doggycredit.scoring.ScoreBand;
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

	/** RF-SCO-03: entre 3 y 4 razones, ordenadas por impacto. */
	static final int MAX_REASONS = 4;

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
		return new BasicScoreResult(score, band(score), reasons(vector), "scoring-" + parameters.modelVersion());
	}

	/**
	 * Contribución exacta de cada variable al logit, c_i = β_i·(x_i − x̄_i)/s_i,
	 * llevada a puntos del score (una contribución positiva sube la PD y resta
	 * puntos). Una variable imputada aporta 0.
	 */
	List<ScoreReason> reasons(float[] vector) {
		List<ScoreReason> all = new ArrayList<>();
		List<ModelParameters.Feature> order = parameters.features();
		for (int i = 0; i < order.size(); i++) {
			ModelParameters.Feature feature = order.get(i);
			double contribution = feature.coefficient() * (vector[i] - feature.mean()) / feature.scale();
			int points = (int) Math.round(-contribution * POINTS_TO_DOUBLE_ODDS / Math.log(2));
			all.add(new ScoreReason(feature.name().toUpperCase(Locale.ROOT), points));
		}
		all.sort(Comparator.comparingInt((ScoreReason reason) -> Math.abs(reason.pointImpact())).reversed());
		return List.copyOf(all.subList(0, Math.min(MAX_REASONS, all.size())));
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
		double score = BASE_SCORE + POINTS_TO_DOUBLE_ODDS / Math.log(2) * Math.log(odds / BASE_ODDS);
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
