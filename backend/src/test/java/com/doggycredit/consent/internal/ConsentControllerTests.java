package com.doggycredit.consent.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.consent.ConsentRequiredException;
import com.doggycredit.consent.Source;

@Import({ TestcontainersConfiguration.class, ConsentControllerTests.ThrowingController.class })
@SpringBootTest
@AutoConfigureMockMvc
class ConsentControllerTests {

	private static final String TENANT = "11111111-1111-1111-1111-111111111111";

	private static final String BODY = """
			{"titularDocumento":" 12.345.678-k ","fuentes":["BURO","BANCO"],"finalidad":"Evaluación de crédito"}
			""";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	/** Solo para probar cómo se renderiza CONSENT_REQUIRED por HTTP. */
	@TestConfiguration
	@RestController
	static class ThrowingController {

		@GetMapping("/test/consent-required")
		void fail() {
			throw new ConsentRequiredException(Source.BURO);
		}

	}

	@Test
	void creaConsentimientoConEvidencia() throws Exception {
		String response = mvc.perform(post("/consentimientos")
				.with(jwt().jwt(j -> j.subject("analista-1").claim("tenant_id", TENANT)))
				.contentType(MediaType.APPLICATION_JSON)
				.content(BODY))
			.andExpect(status().isCreated())
			.andExpect(header().string("X-Simulated", "true"))
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.titularDocumento").value("12345678-K"))
			.andExpect(jsonPath("$.fuentes[0]").value("BANCO"))
			.andExpect(jsonPath("$.fuentes[1]").value("BURO"))
			.andExpect(jsonPath("$.finalidad").value("Evaluación de crédito"))
			.andExpect(jsonPath("$.vigenciaDias").value(90))
			.andExpect(jsonPath("$.estado").value("ACTIVO"))
			.andExpect(jsonPath("$.creadoEn").isNotEmpty())
			.andReturn()
			.getResponse()
			.getContentAsString();

		UUID id = UUID.fromString(response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
		// SPIKE-1: fuera de una petición autenticada no hay tenant en contexto,
		// y RLS (V2026_09_29_1400) devuelve cero filas sin él. Se fija a mano,
		// en la MISMA conexión que la lectura (ConnectionCallback: el pool
		// podría entregar una conexión distinta a dos llamadas sueltas de
		// JdbcTemplate, y TenantAwareDataSource le haría RESET a esa otra).
		Map<String, Object> row = jdbc.execute((ConnectionCallback<Map<String, Object>>) con -> {
			try (var set = con.createStatement()) {
				set.execute("SET app.tenant_id = '" + TENANT + "'");
			}
			try (var ps = con.prepareStatement("""
					select tenant_id, otorgado_por, payload_sha256, expira_en - otorgado_en = interval '90 days' as vigencia_ok
					from consent.consents where id = ?""")) {
				ps.setObject(1, id);
				try (ResultSet rs = ps.executeQuery()) {
					rs.next();
					return Map.of(
							"tenant_id", rs.getObject("tenant_id"),
							"otorgado_por", rs.getString("otorgado_por"),
							"payload_sha256", rs.getString("payload_sha256"),
							"vigencia_ok", rs.getBoolean("vigencia_ok"));
				}
			}
		});
		assertThat(row.get("tenant_id")).isEqualTo(UUID.fromString(TENANT));
		assertThat(row.get("otorgado_por")).isEqualTo("analista-1");
		assertThat((String) row.get("payload_sha256")).hasSize(64);
		assertThat(row.get("vigencia_ok")).isEqualTo(true);
	}

	@Test
	void sinTokenResponde401() throws Exception {
		mvc.perform(post("/consentimientos").contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void sinClaimTenantIdSeRechaza() throws Exception {
		mvc.perform(post("/consentimientos").with(jwt().jwt(j -> j.subject("analista-1")))
				.contentType(MediaType.APPLICATION_JSON)
				.content(BODY))
			.andExpect(status().isForbidden());
	}

	@Test
	void cuerpoInvalidoResponde400() throws Exception {
		mvc.perform(post("/consentimientos")
				.with(jwt().jwt(j -> j.subject("analista-1").claim("tenant_id", TENANT)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"titularDocumento":"123","fuentes":[],"finalidad":" ","vigenciaDias":0}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void consentRequiredSeRenderizaComoProblemJson() throws Exception {
		mvc.perform(get("/test/consent-required").with(jwt().jwt(j -> j.claim("tenant_id", TENANT))))
			.andExpect(status().is(422))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.type").value("https://docs.doggycredit.example/errors/consent-required"))
			.andExpect(jsonPath("$.title").value("Consentimiento requerido"))
			.andExpect(jsonPath("$.status").value(422))
			.andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"))
			.andExpect(jsonPath("$.detail").value("No hay consentimiento vigente para la fuente BURO."));
	}

}
