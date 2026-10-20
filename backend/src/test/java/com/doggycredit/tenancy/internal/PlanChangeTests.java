package com.doggycredit.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.doggycredit.TestTenants;
import com.doggycredit.TestcontainersConfiguration;

/**
 * A-11 / A-12 (RF-PLN-04 / RF-PLN-05) contra Postgres real, con RLS: subir de plan en
 * el momento, bajar al cierre del período con 30 días de gracia, roles y aislamiento.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlanChangeTests {

	private static final Instant PERIOD_END = YearMonth.now(ZoneOffset.UTC).plusMonths(1).atDay(1)
		.atStartOfDay(ZoneOffset.UTC).toInstant();

	@Autowired
	MockMvc mvc;

	@Autowired
	PlanChangeService planChanges;

	@Autowired
	PostgreSQLContainer postgres;

	/** Tenants nuevos en cada prueba: ninguna depende del plan que dejó otra. */
	private UUID pro;
	private UUID otherPro;
	private UUID free;

	@BeforeEach
	void tenants() {
		pro = register("PRO");
		otherPro = register("PRO");
		free = register("FREE");
	}

	// --- A-11: subir en el momento ---------------------------------------------------

	@Test
	void elOperadorSubeAProYLaMismaSesionDelTenantYaTieneLasFuncionesPro() throws Exception {
		// El mismo token antes y después: el plan no viaja en él.
		RequestPostProcessor session = as(free, "TENANT_ADMIN");
		connect(session, "BANCO").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));

		changePlan(free, "PRO").andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("PRO"));

		connect(session, "BANCO").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONECTADO"));
		mvc.perform(get("/tenant/actual/plan").with(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.plan").value("PRO"));
		assertThat(audited(free)).contains("PLAN_UPGRADED");
		assertThat(history(free)).as("D-14: el historial guarda desde qué plan y hacia cuál").containsExactly("FREE>PRO");
	}

	@Test
	void subirCancelaLaBajadaProgramada() throws Exception {
		schedule(pro, "FREE").andExpect(status().isOk());

		changePlan(pro, "ENTERPRISE").andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("ENTERPRISE"));

		state(pro).andExpect(jsonPath("$.plan").value("ENTERPRISE"))
			.andExpect(jsonPath("$.bajadaProgramada").doesNotExist());
		assertThat(planChanges.applyDue(PERIOD_END)).as("ya no hay nada que aplicar al cierre").isZero();
	}

	@Test
	void elOperadorCambiaElTenantDeLaRutaYNoElSuyo() throws Exception {
		// El token del operador pertenece a otro tenant (otherPro), como el operador sembrado.
		changePlan(pro, "ENTERPRISE").andExpect(status().isOk());

		state(pro).andExpect(jsonPath("$.plan").value("ENTERPRISE"));
		state(otherPro).andExpect(jsonPath("$.plan").value("PRO"));
		assertThat(audited(pro)).contains("PLAN_UPGRADED");
		assertThat(audited(otherPro)).as("la bitácora del tenant del operador no registra el cambio ajeno")
			.doesNotContain("PLAN_UPGRADED");
	}

	// --- A-12: bajar al cierre del período, con gracia --------------------------------

	@Test
	void programarLaBajadaNoCambiaNadaHoyYAvisaLoQueSeDesactivara() throws Exception {
		RequestPostProcessor admin = as(pro, "TENANT_ADMIN");
		connect(admin, "BANCO").andExpect(status().isOk());
		connect(admin, "WALLET").andExpect(status().isOk());

		schedule(pro, "FREE").andExpect(status().isOk())
			.andExpect(jsonPath("$.plan").value("PRO"))
			.andExpect(jsonPath("$.periodo.fin").value(PERIOD_END.toString()))
			.andExpect(jsonPath("$.bajadaProgramada.planDestino").value("FREE"))
			.andExpect(jsonPath("$.bajadaProgramada.efectivaEn").value(PERIOD_END.toString()))
			.andExpect(jsonPath("$.bajadaProgramada.integracionesADesactivar", contains("BANCO", "WALLET")))
			.andExpect(jsonPath("$.bajadaProgramada.solicitadaPor").value("admin.demo"))
			.andExpect(jsonPath("$.gracia").doesNotExist());

		// Hasta el cierre del período el tenant sigue en Pro con todo activo.
		assertThat(planChanges.applyDue(PERIOD_END.minusSeconds(1))).isZero();
		state(pro).andExpect(jsonPath("$.plan").value("PRO"));
		assertThat(integrations(pro)).containsExactly("BANCO:true", "WALLET:true");
		assertThat(audited(pro)).contains("PLAN_DOWNGRADE_SCHEDULED").doesNotContain("PLAN_DOWNGRADED");
	}

	@Test
	void alCierreDelPeriodoBajaConTreintaDiasDeGraciaYDesactivaSinBorrar() throws Exception {
		RequestPostProcessor admin = as(pro, "TENANT_ADMIN");
		connect(admin, "BANCO").andExpect(status().isOk());
		connect(admin, "WALLET").andExpect(status().isOk());
		schedule(pro, "FREE").andExpect(status().isOk());

		assertThat(planChanges.applyDue(PERIOD_END)).isEqualTo(1);

		Instant graceEnd = PERIOD_END.plus(Duration.ofDays(30));
		state(pro).andExpect(jsonPath("$.plan").value("FREE"))
			.andExpect(jsonPath("$.bajadaProgramada").doesNotExist())
			.andExpect(jsonPath("$.gracia.planAnterior").value("PRO"))
			.andExpect(jsonPath("$.gracia.desde").value(PERIOD_END.toString()))
			.andExpect(jsonPath("$.gracia.hasta").value(graceEnd.toString()))
			.andExpect(jsonPath("$.gracia.integracionesDesactivadas", contains("BANCO", "WALLET")));
		// Desactivadas, no eliminadas: las filas siguen ahí y el buró sigue activo.
		assertThat(integrations(pro)).containsExactly("BANCO:false", "WALLET:false");
		mvc.perform(get("/integraciones").with(admin))
			.andExpect(jsonPath("$[0].estado").value("CONECTADO"))
			.andExpect(jsonPath("$[1].estado").value("REQUIERE_PRO"));
		assertThat(audited(pro)).contains("PLAN_DOWNGRADED", "INTEGRATION_DISABLED");
		assertThat(planChanges.applyDue(graceEnd)).as("la bajada se aplica una sola vez").isZero();
		assertThat(history(pro)).containsExactly("PRO>FREE");
	}

	@Test
	void subirDuranteLaGraciaRecuperaLoDesactivado() throws Exception {
		connect(as(pro, "TENANT_ADMIN"), "BANCO").andExpect(status().isOk());
		schedule(pro, "FREE").andExpect(status().isOk());
		planChanges.applyDue(PERIOD_END);
		assertThat(integrations(pro)).containsExactly("BANCO:false");

		changePlan(pro, "PRO").andExpect(status().isOk());

		state(pro).andExpect(jsonPath("$.plan").value("PRO")).andExpect(jsonPath("$.gracia").doesNotExist());
		assertThat(integrations(pro)).containsExactly("BANCO:true");
	}

	@Test
	void elOperadorBajaEnElMomentoYTambienAbreLaGracia() throws Exception {
		connect(as(pro, "TENANT_ADMIN"), "WALLET").andExpect(status().isOk());

		changePlan(pro, "FREE").andExpect(status().isOk()).andExpect(jsonPath("$.plan").value("FREE"));

		state(pro).andExpect(jsonPath("$.gracia.planAnterior").value("PRO"))
			.andExpect(jsonPath("$.gracia.integracionesDesactivadas", contains("WALLET")));
		assertThat(integrations(pro)).containsExactly("WALLET:false");
	}

	@Test
	void elDestinoDebeSerInferiorAlPlanActual() throws Exception {
		schedule(free, "FREE").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PLAN_CHANGE_NOT_ALLOWED"));
		schedule(pro, "ENTERPRISE").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PLAN_CHANGE_NOT_ALLOWED"));
		mvc.perform(put("/tenant/actual/plan/bajada").with(as(pro, "TENANT_ADMIN"))
			.contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
		state(pro).andExpect(jsonPath("$.bajadaProgramada").doesNotExist());
	}

	@Test
	void cancelarEsIdempotenteYNoCortaUnaGraciaYaIniciada() throws Exception {
		RequestPostProcessor admin = as(pro, "TENANT_ADMIN");
		schedule(pro, "FREE").andExpect(status().isOk());

		for (int i = 0; i < 2; i++) {
			mvc.perform(delete("/tenant/actual/plan/bajada").with(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.plan").value("PRO"))
				.andExpect(jsonPath("$.bajadaProgramada").doesNotExist());
		}
		assertThat(audited(pro).stream().filter("PLAN_DOWNGRADE_CANCELLED"::equals)).hasSize(1);
		assertThat(planChanges.applyDue(PERIOD_END)).isZero();

		changePlan(pro, "FREE").andExpect(status().isOk());
		mvc.perform(delete("/tenant/actual/plan/bajada").with(admin)).andExpect(status().isOk())
			.andExpect(jsonPath("$.gracia.planAnterior").value("PRO"));
	}

	// --- Roles ------------------------------------------------------------------------

	@Test
	void soloElAdministradorDelTenantProgramaOCancelaLaBajada() throws Exception {
		for (String role : List.of("ANALYST", "API_CLIENT", "PLATFORM_ADMIN")) {
			mvc.perform(put("/tenant/actual/plan/bajada").with(as(pro, role))
				.contentType(MediaType.APPLICATION_JSON).content("{\"planDestino\":\"FREE\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
			mvc.perform(delete("/tenant/actual/plan/bajada").with(as(pro, role)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
		}
		// Consultar el plan sí es de cualquier rol del tenant.
		mvc.perform(get("/tenant/actual/plan").with(as(pro, "ANALYST"))).andExpect(status().isOk())
			.andExpect(jsonPath("$.bajadaProgramada").doesNotExist());
		mvc.perform(get("/tenant/actual/plan")).andExpect(status().isUnauthorized());
		mvc.perform(put("/tenant/actual/plan/bajada").contentType(MediaType.APPLICATION_JSON)
			.content("{\"planDestino\":\"FREE\"}")).andExpect(status().isUnauthorized());
	}

	@Test
	void soloElOperadorCambiaElPlanEnElMomento() throws Exception {
		for (String role : List.of("TENANT_ADMIN", "ANALYST")) {
			mvc.perform(patch("/admin/tenants/{id}", free).with(as(free, role))
				.contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PRO\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
		}
		state(free).andExpect(jsonPath("$.plan").value("FREE"));
		assertThat(audited(free)).doesNotContain("PLAN_UPGRADED");
	}

	// --- Aislamiento entre tenants ------------------------------------------------------

	@Test
	void laBajadaDeUnTenantNoTocaElPlanNiLasIntegracionesDeOtro() throws Exception {
		connect(as(pro, "TENANT_ADMIN"), "BANCO").andExpect(status().isOk());
		connect(as(otherPro, "TENANT_ADMIN"), "BANCO").andExpect(status().isOk());

		schedule(pro, "FREE").andExpect(status().isOk());
		// Programada: el otro tenant no la ve.
		state(otherPro).andExpect(jsonPath("$.bajadaProgramada").doesNotExist());

		assertThat(planChanges.applyDue(PERIOD_END)).isEqualTo(1);

		state(pro).andExpect(jsonPath("$.plan").value("FREE"));
		assertThat(integrations(pro)).containsExactly("BANCO:false");
		state(otherPro).andExpect(jsonPath("$.plan").value("PRO"))
			.andExpect(jsonPath("$.gracia").doesNotExist());
		assertThat(integrations(otherPro)).containsExactly("BANCO:true");
		mvc.perform(get("/integraciones").with(as(otherPro, "TENANT_ADMIN")))
			.andExpect(jsonPath("$[1].estado").value("CONECTADO"));
		assertThat(audited(otherPro)).doesNotContain("PLAN_DOWNGRADED", "PLAN_DOWNGRADE_SCHEDULED",
				"INTEGRATION_DISABLED");
	}

	@Test
	void elTenantSoloProgramaSuPropiaBajadaAunqueNombreAOtroEnElCuerpo() throws Exception {
		mvc.perform(put("/tenant/actual/plan/bajada").with(as(pro, "TENANT_ADMIN"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"planDestino\":\"FREE\",\"tenantId\":\"" + otherPro + "\",\"id\":\"" + otherPro + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.bajadaProgramada.planDestino").value("FREE"));

		state(otherPro).andExpect(jsonPath("$.bajadaProgramada").doesNotExist());
		mvc.perform(get("/tenant/actual/plan").with(as(otherPro, "TENANT_ADMIN")))
			.andExpect(jsonPath("$.bajadaProgramada.integracionesADesactivar").doesNotExist())
			.andExpect(jsonPath("$.gracia.integracionesDesactivadas").doesNotExist());
		mvc.perform(delete("/tenant/actual/plan/bajada").with(as(otherPro, "TENANT_ADMIN")))
			.andExpect(status().isOk());
		state(pro).andExpect(jsonPath("$.bajadaProgramada.planDestino").value("FREE"))
			.andExpect(jsonPath("$.bajadaProgramada.integracionesADesactivar", empty()));
	}

	// --- Apoyo --------------------------------------------------------------------------

	private UUID register(String plan) {
		UUID id = UUID.randomUUID();
		TestTenants.register(postgres, id.toString(), plan);
		return id;
	}

	private ResultActions state(UUID tenant) throws Exception {
		return mvc.perform(get("/tenant/actual/plan").with(as(tenant, "ANALYST"))).andExpect(status().isOk());
	}

	private ResultActions schedule(UUID tenant, String planDestino) throws Exception {
		return mvc.perform(put("/tenant/actual/plan/bajada").with(as(tenant, "TENANT_ADMIN"))
			.contentType(MediaType.APPLICATION_JSON).content("{\"planDestino\":\"" + planDestino + "\"}"));
	}

	/** El operador pertenece a otro tenant que el que cambia, como el operador sembrado. */
	private ResultActions changePlan(UUID tenant, String plan) throws Exception {
		return mvc.perform(patch("/admin/tenants/{id}", tenant).with(as(otherPro, "PLATFORM_ADMIN"))
			.contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"" + plan + "\"}"));
	}

	private ResultActions connect(RequestPostProcessor session, String fuente) throws Exception {
		return mvc.perform(put("/integraciones/{fuente}", fuente).with(session)
			.contentType(MediaType.APPLICATION_JSON).content("{\"activa\":true}"));
	}

	private static RequestPostProcessor as(UUID tenant, String role) {
		return jwt().jwt(token -> token.subject("user-" + role).claim("tenant_id", tenant.toString())
				.claim("preferred_username", "admin.demo"))
			.authorities(new SimpleGrantedAuthority("ROLE_" + role));
	}

	/** Filas de integración del tenant, leídas como administrador de la base (sin RLS). */
	private List<String> integrations(UUID tenant) {
		return query("select source || ':' || active from integration.tenant_integrations"
				+ " where tenant_id = ? order by source", tenant);
	}

	private List<String> audited(UUID tenant) {
		return query("select action from audit.access_log where tenant_id = ? order by chain_seq", tenant);
	}

	private List<String> history(UUID tenant) {
		return query("select plan_anterior || '>' || plan_nuevo from tenancy.plan_changes where tenant_id = ?"
				+ " order by cambiado_en", tenant);
	}

	private List<String> query(String sql, UUID tenant) {
		try (Connection admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword()); PreparedStatement ps = admin.prepareStatement(sql)) {
			ps.setObject(1, tenant);
			List<String> values = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					values.add(rs.getString(1));
				}
			}
			return values;
		}
		catch (SQLException exception) {
			throw new IllegalStateException(exception);
		}
	}

}
