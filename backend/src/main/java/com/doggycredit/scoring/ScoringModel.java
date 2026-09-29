package com.doggycredit.scoring;

/**
 * Puerto del modelo de scoring avanzado (RF-SCO-02). El scorecard básico
 * (Free) vive aparte, como datos versionados, y no depende de esta interfaz:
 * si el modelo avanzado falla, el plan Pro degrada al scorecard, nunca cae
 * el sistema entero.
 * <p>
 * La única implementación del Sprint 1 es {@code internal.OnnxScoringAdapter}
 * (SPIKE-3): un modelo entrenado en Python (ml/training/train.py), exportado
 * a ONNX y ejecutado dentro de la JVM con ONNX Runtime, sin un segundo
 * proceso ni un segundo lenguaje en el camino caliente (ADR-011).
 */
public interface ScoringModel {

	/**
	 * @param featuresOrdered vector en el mismo orden que {@code featureOrder}
	 *                        de {@code ml/model/model_card.json} (10
	 *                        variables del feature_set v1, sin las variables
	 *                        protegidas de RN-04).
	 */
	ScoringResult score(float[] featuresOrdered);

	String modelVersion();
}
