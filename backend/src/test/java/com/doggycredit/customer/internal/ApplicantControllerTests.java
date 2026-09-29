package com.doggycredit.customer.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.shared.TenantContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApplicantControllerTests {

	private static final String TENANT = "33333333-3333-4333-8333-333333333333";
	private static final String OTRO_TENANT = "55555555-5555-4555-8555-555555555555";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void altaNormalizaCifraYAseguraUnicidadPorTenant() throws Exception {
		String body = """
				{"documento":" 12.345.678-k ","actividad":"Comercio","antiguedadActividadMeses":36,
				 "ingresosMensuales":"4500.00","moneda":"BOB","destinoCredito":"Capital de trabajo"}
				""";
		String response = mvc.perform(post("/solicitantes")
				.with(jwt().jwt(token -> token.subject("analista-3").claim("tenant_id", TENANT)))
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.documento").value("12345678-K"))
			.andExpect(jsonPath("$.actividad").value("Comercio"))
			.andExpect(jsonPath("$.ingresosMensuales").value("4500.00"))
			.andReturn().getResponse().getContentAsString();

		UUID id = UUID.fromString(response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
		// SPIKE-1 (RLS): lectura directa fuera de una petición HTTP; sin tenant
		// en contexto la política devolvería cero filas.
		Map<String, Object> row;
		TenantContext.set(UUID.fromString(TENANT));
		try {
			row = jdbc.queryForMap("""
					select document_ref, document_ciphertext from customer.applicants where id = ?
					""", id);
		}
		finally {
			TenantContext.clear();
		}
		assertThat((String) row.get("document_ref")).hasSize(64).doesNotContain("12345678");
		assertThat((String) row.get("document_ciphertext")).doesNotContain("12345678-K");

		mvc.perform(post("/solicitantes")
				.with(jwt().jwt(token -> token.subject("analista-3").claim("tenant_id", TENANT)))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body.replace("12.345.678-k", "12345678-K")))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("APPLICANT_ALREADY_EXISTS"));

		// La unicidad es por tenant: otra institución puede registrar al mismo titular.
		mvc.perform(post("/solicitantes")
				.with(jwt().jwt(token -> token.subject("analista-otro").claim("tenant_id", OTRO_TENANT)))
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated());
	}

}
