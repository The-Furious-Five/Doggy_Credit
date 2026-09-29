package com.doggycredit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * RF-SEG-01.1: meta-test que recorre el 100% de las rutas registradas y
 * verifica que cada ruta privada sin token responda 401. Las rutas públicas
 * (actuator/health) se excluyen explícitamente.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MetaTest401Test {

	/** Rutas explícitamente públicas en IdentitySecurityConfiguration. */
	private static final Set<String> RUTAS_PUBLICAS = Set.of("/actuator/health");

	@Autowired
	MockMvc mvc;

	/**
	 * Actuator registra su propio {@code controllerEndpointHandlerMapping};
	 * sin el calificador, Spring encuentra dos beans y falla al arrancar.
	 */
	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

	@TestFactory
	List<DynamicTest> todasLasRutasPrivadasResponden401SinToken() {
		Map<RequestMappingInfo, HandlerMethod> mappings = handlerMapping.getHandlerMethods();
		List<DynamicTest> tests = new ArrayList<>();

		for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.entrySet()) {
			RequestMappingInfo info = entry.getKey();
			Set<String> patterns = info.getPatternValues();
			if (patterns.isEmpty()) {
				continue;
			}

			for (String pattern : patterns) {
				if (RUTAS_PUBLICAS.contains(pattern) || pattern.startsWith("/actuator/")) {
					continue;
				}
				// Ignorar rutas de test que no son de producción
				if (pattern.startsWith("/test/")) {
					continue;
				}

				Set<org.springframework.web.bind.annotation.RequestMethod> methods =
						info.getMethodsCondition().getMethods();
				if (methods.isEmpty()) {
					methods = Set.of(org.springframework.web.bind.annotation.RequestMethod.GET);
				}

				for (org.springframework.web.bind.annotation.RequestMethod m : methods) {
					String displayName = m.name() + " " + pattern + " → 401 sin token";
					HttpMethod httpMethod = HttpMethod.valueOf(m.name());
					tests.add(DynamicTest.dynamicTest(displayName, () ->
							mvc.perform(request(httpMethod, resolvePath(pattern))
									.contentType("application/json")
									.content("{}"))
								.andExpect(status().isUnauthorized())
								// RF-API-02.1: el 401 también sigue RFC 9457, con
								// correlationId — antes lo saltaba (sendError salta
								// ProblemDetailAdvice); este meta-test solo veía el
								// código de estado y no lo detectaba.
								.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
								.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
								.andExpect(jsonPath("$.correlationId").isNotEmpty())));
				}
			}
		}

		// Garantiza que el meta-test encontró al menos una ruta para verificar
		if (tests.isEmpty()) {
			tests.add(DynamicTest.dynamicTest("al menos una ruta privada registrada", () -> {
				throw new AssertionError("No se encontraron rutas privadas para verificar");
			}));
		}

		return tests;
	}

	/** Reemplaza parámetros de path con valores UUID de prueba. */
	private static String resolvePath(String pattern) {
		return pattern.replaceAll("\\{[^}]+}", "00000000-0000-0000-0000-000000000000");
	}

}
