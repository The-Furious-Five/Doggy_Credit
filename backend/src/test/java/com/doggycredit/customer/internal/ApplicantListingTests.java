package com.doggycredit.customer.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.consent.Source;
import com.doggycredit.customer.FinancialProfiles;
import com.doggycredit.customer.SourceResult;
import com.doggycredit.shared.TenantContext;

/**
 * B-08: listado paginado con filtro por segmento, búsqueda por documento vía
 * HMAC y detalle con Perfil 360. SQL y parámetros enlazados al máximo detalle
 * (lo que se activa para depurar) para probar que el documento buscado nunca
 * aparece en el log: la consulta lleva solo su HMAC. Spring web queda en su nivel
 * por defecto; en TRACE vuelca argumentos y cuerpos de cualquier endpoint.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "logging.level.org.hibernate.SQL=DEBUG", "logging.level.org.hibernate.orm.jdbc.bind=TRACE" })
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ApplicantListingTests {

	private static final String TENANT = "a0a0a0a0-0000-4000-8000-0000000000b8";
	private static final String OTRO_TENANT = "b0b0b0b0-0000-4000-8000-0000000000b8";

	@Autowired
	MockMvc mvc;

	@Autowired
	FinancialProfiles profiles;

	@Test
	void listaFiltraBuscaPorHmacYMuestraElPerfil(CapturedOutput output) throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		String persona = register(TENANT, "CI-B08-1-" + suffix, null);
		register(TENANT, "CI-B08-2-" + suffix, "EMPRENDEDOR");
		String pyme = register(TENANT, "NIT-B08-3-" + suffix, "PYME");
		register(OTRO_TENANT, "CI-B08-1-" + suffix, null);

		mvc.perform(get("/solicitantes").param("tamano", "2").with(as(TENANT)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(3))
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].id").value(pyme));
		mvc.perform(get("/solicitantes").param("tamano", "2").param("pagina", "2").with(as(TENANT)))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].id").value(persona))
			.andExpect(jsonPath("$.items[0].segmento").value("PERSONA"));
		mvc.perform(get("/solicitantes").param("segmento", "PYME").with(as(TENANT)))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].segmento").value("PYME"));

		// Búsqueda: documento sin normalizar en la cabecera; nunca en la URL ni en los logs.
		String buscado = "CI-B08-1-" + suffix;
		int before = output.getAll().length();
		mvc.perform(get("/solicitantes").header("X-Documento", " ci-b08-1-" + suffix.toLowerCase() + " ").with(as(TENANT)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].id").value(persona));
		String searchLog = output.getAll().substring(before);
		assertThat(searchLog).contains("select").doesNotContainIgnoringCase(buscado);

		mvc.perform(get("/solicitantes/{id}", persona).with(as(TENANT)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.documento").value(buscado))
			.andExpect(jsonPath("$.perfil").doesNotExist());
		TenantContext.set(UUID.fromString(TENANT));
		try {
			profiles.consolidate(UUID.fromString(TENANT), UUID.fromString(persona),
					Map.of(Source.BURO, SourceResult.of(Optional.empty()), Source.BANCO, SourceResult.unavailable()));
		}
		finally {
			TenantContext.clear();
		}
		mvc.perform(get("/solicitantes/{id}", persona).with(as(TENANT)))
			.andExpect(jsonPath("$.perfil.ingresoMensual.amount").value("4500.00"))
			.andExpect(jsonPath("$.perfil.origenIngreso").value("DECLARADO"))
			.andExpect(jsonPath("$.perfil.deudaVigente.amount").value("0.00"))
			.andExpect(jsonPath("$.perfil.fuentes[0].estado").value("SIN_REGISTRO"))
			.andExpect(jsonPath("$.perfil.fuentes[1].estado").value("NO_DISPONIBLE"))
			.andExpect(jsonPath("$.perfil.fuentes[2].estado").value("NO_CONSULTADA"));

		mvc.perform(get("/solicitantes/{id}", persona).with(as(OTRO_TENANT)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("UNKNOWN_APPLICANT"));
		mvc.perform(get("/solicitantes").with(as(OTRO_TENANT)))
			.andExpect(jsonPath("$.total").value(1));
	}

	private String register(String tenant, String document, String segment) throws Exception {
		String body = """
				{"documento":"%s","actividad":"Comercio","antiguedadActividadMeses":36,
				 "ingresosMensuales":"4500.00","moneda":"BOB","destinoCredito":"Capital"%s}
				""".formatted(document, segment == null ? "" : ",\"segmento\":\"" + segment + "\"");
		String json = mvc.perform(post("/solicitantes").with(as(tenant))
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		Thread.sleep(5); // created_at distinto: el orden del listado es por fecha de alta
		return json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
	}

	private static RequestPostProcessor as(String tenant) {
		return jwt().jwt(token -> token.subject("analista-b08").claim("tenant_id", tenant));
	}

}
