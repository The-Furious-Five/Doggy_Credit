package com.doggycredit.consent.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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

	@Autowired
	PlatformTransactionManager transactionManager;

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
		Map<String, Object> row = inTenant(TENANT, () -> jdbc.queryForMap("""
				select tenant_id, otorgado_por, payload_sha256, expira_en - otorgado_en = interval '90 days' as vigencia_ok
				from consent.consents where id = ?""", id));
		assertThat(row.get("tenant_id")).isEqualTo(UUID.fromString(TENANT));
		assertThat(row.get("otorgado_por")).isEqualTo("analista-1");
		assertThat((String) row.get("payload_sha256")).hasSize(64);
		assertThat(row.get("vigencia_ok")).isEqualTo(true);
	}

	@Test
	void rlsFallaCerradoSinTenantYEnAislamientoConcurrente() throws Exception {
		assertThat(jdbc.queryForObject("select count(*) from consent.consents", Long.class)).isZero();
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("""
				insert into consent.consents (id, tenant_id, titular_documento, fuentes, finalidad,
				vigencia_dias, otorgado_en, expira_en, estado, otorgado_por, payload_sha256)
				values (?, ?::uuid, 'TEST', ARRAY['BURO'], 'prueba', 1, now(), now() + interval '1 day',
				'ACTIVO', 'test', repeat('0', 64))""", UUID.randomUUID(), "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))
			.isInstanceOf(org.springframework.dao.DataAccessException.class);
		String tenantA = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
		String tenantB = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
		AtomicInteger filasCruzadas = new AtomicInteger();
		try (var pool = Executors.newFixedThreadPool(2)) {
			var a = pool.submit(() -> insertarConcurrentemente(tenantA, tenantB, filasCruzadas));
			var b = pool.submit(() -> insertarConcurrentemente(tenantB, tenantA, filasCruzadas));
			a.get();
			b.get();
		}
		assertThat(filasCruzadas).hasValue(0);
	}

	private void insertarConcurrentemente(String tenant, String otroTenant, AtomicInteger filasCruzadas) {
		for (int i = 0; i < 50; i++) {
			inTenant(tenant, () -> {
				jdbc.update("""
						insert into consent.consents (id, tenant_id, titular_documento, fuentes, finalidad,
						vigencia_dias, otorgado_en, expira_en, estado, otorgado_por, payload_sha256)
						values (?, ?::uuid, 'TEST', ARRAY['BURO'], 'prueba', 1, now(), now() + interval '1 day',
						'ACTIVO', 'test', repeat('0', 64))""", UUID.randomUUID(), tenant);
				Long visible = jdbc.queryForObject("select count(*) from consent.consents where tenant_id = ?::uuid",
					Long.class, otroTenant);
				if (visible != null && visible != 0) filasCruzadas.addAndGet(visible.intValue());
				return null;
			});
		}
	}

	private <T> T inTenant(String tenant, java.util.concurrent.Callable<T> work) {
		return new TransactionTemplate(transactionManager).execute(status -> {
			jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, tenant);
			try { return work.call(); }
			catch (RuntimeException e) { throw e; }
			catch (Exception e) { throw new IllegalStateException(e); }
		});
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
