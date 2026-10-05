package com.doggycredit.scoring.internal;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.doggycredit.scoring.ScoringModel;
import com.doggycredit.scoring.ScoringResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Map;

/**
 * SPIKE-3: adaptador que ejecuta el modelo de scoring (regresión logística
 * exportada a ONNX desde {@code ml/training/train.py}) dentro de la propia
 * JVM, sin un segundo proceso (ADR-011).
 * <p>
 * El artefacto se carga una sola vez, al construir el adaptador, desde
 * {@code classpath:models/scoring-<versión>.onnx} (hoy {@code v2}). El
 * nombre del tensor de salida de probabilidades ({@code probabilities}) y
 * el orden de las columnas de entrada están fijados por {@code skl2onnx}
 * con {@code zipmap=False} —
 * ver {@code ml/training/train.py}. Si el modelo cambia de forma
 * incompatible, este adaptador debe actualizarse junto con el contrato de
 * {@code ml/model/model_card.json}.
 */
public final class OnnxScoringAdapter implements ScoringModel, AutoCloseable {

	private static final String OUTPUT_PROBABILITIES = "probabilities";

	private final String modelVersion;
	private final OrtEnvironment environment;
	private final OrtSession session;

	public OnnxScoringAdapter(String modelVersion) {
		this.modelVersion = modelVersion;
		this.environment = OrtEnvironment.getEnvironment();
		String classpathModel = "models/scoring-" + modelVersion + ".onnx";
		try {
			byte[] modelBytes = readClasspathResource(classpathModel);
			this.session = environment.createSession(modelBytes, new OrtSession.SessionOptions());
		} catch (OrtException e) {
			throw new IllegalStateException("No se pudo cargar el modelo ONNX " + classpathModel, e);
		}
	}

	@Override
	public ScoringResult score(float[] featuresOrdered) {
		try (OnnxTensor input = OnnxTensor.createTensor(
				environment, new float[][] { featuresOrdered })) {

			String inputName = session.getInputNames().iterator().next();
			Map<String, OnnxTensor> inputs = Collections.singletonMap(inputName, input);

			try (OrtSession.Result result = session.run(inputs)) {
				OnnxValue probabilities = result.get(OUTPUT_PROBABILITIES)
						.orElseThrow(() -> new IllegalStateException(
								"El modelo ONNX no tiene la salida '" + OUTPUT_PROBABILITIES + "'"));
				// forma [1,2]: columna 0 = P(no default), columna 1 = P(default)
				float[][] proba = (float[][]) probabilities.getValue();
				float probabilityOfDefault = proba[0][1];
				return new ScoringResult(probabilityOfDefault, modelVersion);
			}
		} catch (OrtException e) {
			throw new IllegalStateException("Fallo al ejecutar el modelo ONNX", e);
		}
	}

	@Override
	public String modelVersion() {
		return modelVersion;
	}

	@Override
	public void close() {
		try {
			session.close();
		} catch (OrtException e) {
			throw new IllegalStateException("Fallo al cerrar la sesión ONNX", e);
		}
	}

	private static byte[] readClasspathResource(String path) {
		try (InputStream in = OnnxScoringAdapter.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("No se encontró el recurso classpath:" + path);
			}
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
