package com.doggycredit.scoring.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

import tools.jackson.databind.json.JsonMapper;

/**
 * Media, escala y coeficiente estandarizado de cada variable, en el orden de
 * entrada del modelo. Lo escribe {@code ml/training/train.py} como
 * {@code scoring-<versión>.params.json}; el CI exige que sea el mismo archivo
 * que el de {@code ml/model/} (D-07).
 */
record ModelParameters(String modelVersion, String featureSetVersion, List<Feature> features, double intercept) {

	record Feature(String name, double mean, double scale, double coefficient) {
	}

	static ModelParameters load(String modelVersion) {
		String path = "models/scoring-" + modelVersion + ".params.json";
		try (InputStream in = ModelParameters.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("No se encontró el recurso classpath:" + path);
			}
			ModelParameters parameters = JsonMapper.builder().build().readValue(in, ModelParameters.class);
			if (!modelVersion.equals(parameters.modelVersion())) {
				throw new IllegalStateException(path + " es de la versión " + parameters.modelVersion());
			}
			return parameters;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
