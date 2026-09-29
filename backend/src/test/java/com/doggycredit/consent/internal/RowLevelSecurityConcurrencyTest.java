package com.doggycredit.consent.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.doggycredit.TestcontainersConfiguration;

/**
 * SPIKE-1: prueba de concurrencia de la Definition of Done ("2 tenants × 50
 * hilos sin ninguna fila cruzada; sin app.tenant_id fijado, 0 filas
 * devueltas y el INSERT falla"). Dos tenants reales (seed/tenants.yaml,
 * V2026_09_28_1100) hacen 50 altas concurrentes cada uno a través de la API
 * real —no directo al repositorio— para ejercitar las dos capas juntas:
 * TenantClaimFilter puebla TenantContext, de ahí toman el discriminador
 * {@code @TenantId} de Hibernate y la variable de sesión que lee la
 * política de RLS de V2026_09_29_1400.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RowLevelSecurityConcurrencyTest {

	private static final String TENANT_A = "9c7e7c1a-0000-4000-8000-000000000001"; // Progreso Andino
	private static final String TENANT_B = "9c7e7c1a-0000-4000-8000-000000000002"; // Fintech Vecina
	private static final int HILOS_POR_TENANT = 50;

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void cienHilosDosTenantsSinFilasCruzadas() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2 * HILOS_POR_TENANT);
		try {
			List<Callable<Void>> tareas = IntStream.range(0, HILOS_POR_TENANT)
					.boxed()
					.flatMap(i -> java.util.stream.Stream.of(
							altaConsentimiento(TENANT_A, "CI-A-" + i),
							altaConsentimiento(TENANT_B, "CI-B-" + i)))
					.toList();

			List<Future<Void>> resultados = pool.invokeAll(tareas, 60, TimeUnit.SECONDS);
			for (Future<Void> resultado : resultados) {
				resultado.get(); // relanza cualquier fallo/excepción del hilo
			}
		} finally {
			pool.shutdown();
		}

		// Filtrado por el prefijo de documento de esta prueba: la base de datos
		// de Testcontainers se comparte entre los métodos de esta clase, así
		// que un conteo sin filtrar arrastraría filas de otras pruebas.
		assertThat(contarConsentimientosVisiblesPara(TENANT_A, "CI-A-%")).isEqualTo(HILOS_POR_TENANT);
		assertThat(contarConsentimientosVisiblesPara(TENANT_B, "CI-B-%")).isEqualTo(HILOS_POR_TENANT);
		assertThat(todosLosTenantIdVisiblesSon(TENANT_A, "CI-A-%")).isTrue();
		assertThat(todosLosTenantIdVisiblesSon(TENANT_B, "CI-B-%")).isTrue();

		// La aserción que de verdad prueba RLS: las filas del otro tenant existen
		// (se acaban de crear 50) pero son invisibles desde la sesión de cada uno.
		// Las cuatro de arriba pasarían igual sin RLS.
		assertThat(contarConsentimientosVisiblesPara(TENANT_A, "CI-B-%")).isZero();
		assertThat(contarConsentimientosVisiblesPara(TENANT_B, "CI-A-%")).isZero();
	}

	@Test
	void sinAppTenantIdFijadoCeroFilasDevueltas() throws Exception {
		altaConsentimiento(TENANT_A, "CI-FAIL-CLOSED-SELECT").call();

		Integer total = jdbc.execute((ConnectionCallback<Integer>) con -> {
			try (var reset = con.createStatement()) {
				reset.execute("RESET app.tenant_id");
			}
			try (var st = con.createStatement();
					var rs = st.executeQuery("SELECT count(*) FROM consent.consents")) {
				rs.next();
				return rs.getInt(1);
			}
		});

		assertThat(total).isZero();
	}

	@Test
	void sinAppTenantIdFijadoElInsertFalla() {
		Throwable thrown = catchThrowable(() -> jdbc.execute((ConnectionCallback<Void>) con -> {
			try (var reset = con.createStatement()) {
				reset.execute("RESET app.tenant_id");
			}
			try (var insert = con.createStatement()) {
				insert.execute("""
						INSERT INTO consent.consents
						  (id, tenant_id, titular_documento, fuentes, finalidad,
						   vigencia_dias, otorgado_en, expira_en, estado, otorgado_por, payload_sha256)
						VALUES
						  (gen_random_uuid(), '%s', 'CI-FAIL-CLOSED-INSERT', ARRAY['BURO'],
						   'Prueba de fallo cerrado', 90, now(), now() + interval '90 days',
						   'ACTIVO', 'test', repeat('0', 64))
						""".formatted(TENANT_A));
			}
			return null;
		}));

		assertThat(thrown).isInstanceOf(Exception.class).hasRootCauseInstanceOf(SQLException.class);
		assertThat(thrown.getCause()).isNotNull();
		assertThat(thrown.getCause().getMessage()).contains("row-level security");
	}

	private Callable<Void> altaConsentimiento(String tenant, String documento) {
		String body = """
				{"titularDocumento":"%s","fuentes":["BURO"],"finalidad":"Prueba de concurrencia RLS"}
				""".formatted(documento);
		return () -> {
			mvc.perform(post("/consentimientos")
					.with(jwt().jwt(j -> j.subject("analista-concurrencia").claim("tenant_id", tenant)))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isCreated());
			return null;
		};
	}

	private int contarConsentimientosVisiblesPara(String tenant, String documentoLike) {
		return jdbc.execute((ConnectionCallback<Integer>) con -> {
			try (var set = con.createStatement()) {
				set.execute("SET app.tenant_id = '" + tenant + "'");
			}
			try (var ps = con.prepareStatement(
					"SELECT count(*) FROM consent.consents WHERE titular_documento LIKE ?")) {
				ps.setString(1, documentoLike);
				try (var rs = ps.executeQuery()) {
					rs.next();
					return rs.getInt(1);
				}
			}
		});
	}

	private boolean todosLosTenantIdVisiblesSon(String tenant, String documentoLike) {
		return jdbc.execute((ConnectionCallback<Boolean>) con -> {
			try (var set = con.createStatement()) {
				set.execute("SET app.tenant_id = '" + tenant + "'");
			}
			try (var ps = con.prepareStatement(
					"SELECT DISTINCT tenant_id::text FROM consent.consents WHERE titular_documento LIKE ?")) {
				ps.setString(1, documentoLike);
				try (var rs = ps.executeQuery()) {
					List<String> vistos = new java.util.ArrayList<>();
					while (rs.next()) {
						vistos.add(rs.getString(1));
					}
					return vistos.equals(List.of(tenant));
				}
			}
		});
	}
}
