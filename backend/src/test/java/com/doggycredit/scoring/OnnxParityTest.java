package com.doggycredit.scoring;

import com.doggycredit.scoring.internal.OnnxScoringAdapter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * SPIKE-3 — criterio de salida: la probabilidad que devuelve el modelo
 * ONNX dentro de la JVM debe coincidir con la que calculó scikit-learn en
 * Python al entrenarlo, con una tolerancia de 1e-6, sobre un golden set de
 * 20 perfiles fijos (ver ml/training/train.py y ml/model/golden_set.json,
 * copiado a src/test/resources).
 * <p>
 * Si este test falla después de reentrenar el modelo, es señal de que el
 * artefacto ONNX en {@code backend/src/main/resources/models/} está
 * desactualizado respecto al de {@code ml/model/}: hay que volver a
 * copiarlo (no se genera automáticamente todavía — eso es una mejora de
 * CI para un sprint posterior).
 */
class OnnxParityTest {

	private static final double TOLERANCIA = 1e-6;

	private static OnnxScoringAdapter adapter;

	@BeforeAll
	static void cargarModelo() {
		adapter = new OnnxScoringAdapter("v1");
	}

	@AfterAll
	static void cerrarModelo() {
		adapter.close();
	}

	@TestFactory
	Stream<DynamicTest> laProbabilidadDeOnnxCoincideConLaDeSklearn() throws IOException {
		List<Map<String, Object>> goldenSet = leerGoldenSet();
		assertEquals(20, goldenSet.size(), "el golden set debe tener 20 perfiles fijos");

		return goldenSet.stream().map(perfil -> {
			int id = (Integer) perfil.get("id");
			return DynamicTest.dynamicTest("perfil #" + id, () -> {
				float[] features = extraerFeatures(perfil);
				double esperado = ((Number) perfil.get("expectedPd")).doubleValue();

				ScoringResult resultado = adapter.score(features);

				assertEquals(esperado, resultado.probabilityOfDefault(), TOLERANCIA,
						() -> "perfil #" + id + ": probabilidad de default (Java/ONNX vs. Python/sklearn)");
				assertEquals("v1", resultado.modelVersion());
			});
		});
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> leerGoldenSet() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		try (InputStream in = OnnxParityTest.class.getClassLoader()
				.getResourceAsStream("golden_set.json")) {
			assertNotNull(in, "classpath:golden_set.json");
			return mapper.readValue(in, new TypeReference<List<Map<String, Object>>>() {
			});
		}
	}

	@SuppressWarnings("unchecked")
	private static float[] extraerFeatures(Map<String, Object> perfil) {
		List<Number> ordenadas = (List<Number>) perfil.get("featuresOrdered");
		float[] out = new float[ordenadas.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = ordenadas.get(i).floatValue();
		}
		return out;
	}
}
