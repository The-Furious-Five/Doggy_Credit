package com.doggycredit.assessment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.doggycredit.TestTenants;
import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.shared.TenantContext;

/**
 * C-05: assessment.policies contra PostgreSQL real (RLS y permisos del rol de la app)
 * y GET/PUT /politicas (solo TENANT_ADMIN, aislado por tenant).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PolicyTests {

	private static final String INSUFFICIENT_PRIVILEGE = "42501";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PostgreSQLContainer postgres;

	private UUID tenant;
	private UUID otherTenant;

	@BeforeEach
	void tenantsWithRules() throws SQLException {
		tenant = UUID.randomUUID();
		otherTenant = UUID.randomUUID();
		for (UUID id : new UUID[] { tenant, otherTenant }) {
			TestTenants.register(postgres, id.toString(), "FREE");
			seedRules(id);
		}
	}

	// --- tabla: RLS y permisos ---------------------------------------------------

	@Test
	void cadaTenantVeSoloSusReglas() {
		Long visibles = asTenant(tenant, () -> jdbc.queryForObject(
				"SELECT count(*) FROM assessment.policies WHERE tenant_id IN (?, ?)", Long.class, tenant, otherTenant));
		assertThat(visibles).isEqualTo(4);
	}

	@Test
	void sinTenantFijadoNoSeVeNingunaRegla() {
		TenantContext.clear();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM assessment.policies", Long.class)).isZero();
	}

	@Test
	void unTenantNoPuedePausarLasReglasDeOtro() {
		Integer actualizadas = asTenant(tenant, () -> jdbc.update(
				"UPDATE assessment.policies SET active = false WHERE tenant_id = ?", otherTenant));
		assertThat(actualizadas).isZero();
	}

	@Test
	void laAppNoPuedeCrearNiBorrarReglas() {
		assertSqlState(INSUFFICIENT_PRIVILEGE, () -> asTenant(tenant, () -> jdbc.update("""
				INSERT INTO assessment.policies (id, tenant_id, code, name, condition_text, effect)
				VALUES (?, ?, 'MONTO_ALTO', 'x', 'x', 'REVISION_MANUAL')
				""", UUID.randomUUID(), tenant)));
		assertSqlState(INSUFFICIENT_PRIVILEGE, () -> asTenant(tenant, () -> jdbc.update(
				"DELETE FROM assessment.policies")));
	}

	@Test
	void ningunaReglaPuedeTenerComoEfectoUnRechazo() {
		assertThatThrownBy(() -> {
			try (Connection admin = adminConnection(); PreparedStatement ps = admin.prepareStatement("""
					UPDATE assessment.policies SET effect = 'RECHAZADO' WHERE tenant_id = ?""")) {
				ps.setObject(1, tenant);
				ps.executeUpdate();
			}
		}).isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"));
	}

	@Test
	void laSemillaCargaLasCuatroReglasParaCadaTenantDelRegistro() throws SQLException {
		try (Connection admin = adminConnection(); var st = admin.createStatement();
				var rs = st.executeQuery("""
						SELECT (SELECT count(*) FROM tenancy.tenants WHERE nombre NOT LIKE 'Tenant de prueba%') AS tenants,
						       (SELECT count(*) FROM tenancy.tenants t
						         WHERE t.nombre NOT LIKE 'Tenant de prueba%'
						           AND (SELECT count(*) FROM assessment.policies p WHERE p.tenant_id = t.id) <> 4)
						         AS incompletos
						""")) {
			rs.next();
			assertThat(rs.getLong("tenants")).isPositive();
			assertThat(rs.getLong("incompletos")).isZero();
		}
	}

	// --- endpoints ---------------------------------------------------------------

	@Test
	void elAdministradorVeLasReglasEnOrden() throws Exception {
		list(tenant, "TENANT_ADMIN")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(4))
			.andExpect(jsonPath("$[0].codigo").value("PREAPROBACION_SCORE"))
			.andExpect(jsonPath("$[0].resultado").value("PREAPROBADO"))
			.andExpect(jsonPath("$[0].activa").value(true))
			.andExpect(jsonPath("$[3].codigo").value("DATOS_INSUFICIENTES"))
			.andExpect(jsonPath("$[3].resultado").value("SOLICITAR_DATOS"))
			.andExpect(jsonPath("$[3].activa").value(false));
	}

	@Test
	void quienNoEsAdministradorRecibe403ConSuCodigo() throws Exception {
		list(tenant, "ANALISTA")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
		update(tenant, "ANALISTA", ruleId(tenant, "MONTO_ALTO"), false)
			.andExpect(status().isForbidden());
	}

	@Test
	void elAdministradorPausaYReactivaUnaReglaYQuedaAuditado() throws Exception {
		UUID rule = ruleId(tenant, "MONTO_ALTO");

		update(tenant, "TENANT_ADMIN", rule, false)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(rule.toString()))
			.andExpect(jsonPath("$.activa").value(false));
		assertThat(asTenant(tenant, () -> jdbc.queryForObject(
				"SELECT active FROM assessment.policies WHERE id = ?", Boolean.class, rule))).isFalse();
		update(tenant, "TENANT_ADMIN", rule, true).andExpect(jsonPath("$.activa").value(true));

		assertThat(asTenant(tenant, () -> jdbc.queryForList(
				"SELECT action FROM audit.access_log WHERE resource_id = ? ORDER BY created_at", String.class, rule)))
			.containsExactly("POLICY_PAUSED", "POLICY_ACTIVATED");
	}

	@Test
	void unTenantNoPuedeVerNiCambiarLasReglasDeOtro() throws Exception {
		UUID ruleOfTenant = ruleId(tenant, "MONTO_ALTO");
		update(otherTenant, "TENANT_ADMIN", ruleOfTenant, false)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"));
		assertThat(asTenant(tenant, () -> jdbc.queryForObject(
				"SELECT active FROM assessment.policies WHERE id = ?", Boolean.class, ruleOfTenant)))
			.isTrue();
	}

	// --- utilidades --------------------------------------------------------------

	private ResultActions list(UUID tenantId, String role) throws Exception {
		return mvc.perform(get("/politicas").with(token(tenantId, role)));
	}

	private ResultActions update(UUID tenantId, String role, UUID rule, boolean active) throws Exception {
		return mvc.perform(put("/politicas/{id}", rule).with(token(tenantId, role))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"activa\":" + active + "}"));
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor token(UUID tenantId, String role) {
		return jwt().jwt(t -> t.subject("usuario-" + role).claim("tenant_id", tenantId.toString()))
			.authorities(new SimpleGrantedAuthority("ROLE_" + role));
	}

	private UUID ruleId(UUID tenantId, String code) {
		return asTenant(tenantId, () -> jdbc.queryForObject(
				"SELECT id FROM assessment.policies WHERE code = ?", UUID.class, code));
	}

	/** Las mismas 4 reglas que la semilla de Flyway, como superusuario (la app no tiene INSERT). */
	private void seedRules(UUID tenantId) throws SQLException {
		try (Connection admin = adminConnection(); PreparedStatement ps = admin.prepareStatement("""
				INSERT INTO assessment.policies (id, tenant_id, code, name, condition_text, effect, active)
				VALUES (gen_random_uuid(), ?, 'PREAPROBACION_SCORE', 'Preaprobación por score', 'score ≥ 650', 'PREAPROBADO', true),
				       (gen_random_uuid(), ?, 'MONTO_ALTO', 'Revisión por monto alto', 'monto > 200.000', 'REVISION_MANUAL', true),
				       (gen_random_uuid(), ?, 'BANDA_BAJA', 'Revisión de bandas bajas', 'banda D o E', 'REVISION_MANUAL', true),
				       (gen_random_uuid(), ?, 'DATOS_INSUFICIENTES', 'Datos insuficientes', 'fuentes < 2', 'SOLICITAR_DATOS', false)
				""")) {
			for (int i = 1; i <= 4; i++) {
				ps.setObject(i, tenantId);
			}
			ps.executeUpdate();
		}
	}

	private Connection adminConnection() throws SQLException {
		return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
	}

	private static <T> T asTenant(UUID tenantId, Supplier<T> work) {
		TenantContext.set(tenantId);
		try {
			return work.get();
		}
		finally {
			TenantContext.clear();
		}
	}

	private static void assertSqlState(String expected, Runnable action) {
		assertThatThrownBy(action::run)
			.isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(e.getMostSpecificCause())
				.isInstanceOfSatisfying(SQLException.class, cause -> assertThat(cause.getSQLState()).isEqualTo(expected)));
	}

}
