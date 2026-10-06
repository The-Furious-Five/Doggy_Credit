package com.doggycredit.recommendation.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.shared.TenantContext;

/** C-06 (LOS-86): GET y PUT de productos — rol TENANT_ADMIN, aislamiento por tenant y validación. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTests {

	private static final String CAMBIOS = """
			{"activo":false,"montoMinimo":"2000","montoMaximo":"30000","plazoMinimoMeses":6,"plazoMaximoMeses":36}
			""";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PostgreSQLContainer postgres;

	@Test
	void elAdministradorVeElCatalogoDeSuTenant() throws Exception {
		UUID tenant = UUID.randomUUID();
		insertarProducto(tenant, "MICROCREDITO", "1000.00", "20000.00", 3, 24, "C", true);
		insertarProducto(tenant, "PYME", "50000.00", "700000.00", 6, 84, "B", false);

		mvc.perform(como(get("/productos"), tenant, "TENANT_ADMIN"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].tipo").value("MICROCREDITO"))
			.andExpect(jsonPath("$[0].montoMinimo").value("1000.00"))
			.andExpect(jsonPath("$[0].tasaAnual").value("11.50"))
			.andExpect(jsonPath("$[1].tipo").value("PYME"))
			.andExpect(jsonPath("$[1].activo").value(false));
	}

	@Test
	void unAnalistaNoPuedeVerNiCambiarElCatalogo() throws Exception {
		UUID tenant = UUID.randomUUID();
		UUID micro = insertarProducto(tenant, "MICROCREDITO", "1000.00", "20000.00", 3, 24, "C", true);

		mvc.perform(como(get("/productos"), tenant, "ANALYST"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
		mvc.perform(como(put("/productos/{id}", micro), tenant, "ANALYST")
				.contentType(MediaType.APPLICATION_JSON).content(CAMBIOS))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));

		assertThat(activo(tenant, micro)).isTrue();
	}

	@Test
	void elAdministradorDesactivaYCambiaLosRangosYQuedaAuditado() throws Exception {
		UUID tenant = UUID.randomUUID();
		UUID micro = insertarProducto(tenant, "MICROCREDITO", "1000.00", "20000.00", 3, 24, "C", true);

		mvc.perform(como(put("/productos/{id}", micro), tenant, "TENANT_ADMIN")
				.contentType(MediaType.APPLICATION_JSON).content(CAMBIOS))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.activo").value(false))
			.andExpect(jsonPath("$.montoMaximo").value("30000.00"))
			.andExpect(jsonPath("$.plazoMinimoMeses").value(6))
			// La tasa y la banda no se tocan.
			.andExpect(jsonPath("$.tasaAnual").value("11.50"))
			.andExpect(jsonPath("$.bandaMinima").value("C"));

		assertThat(activo(tenant, micro)).isFalse();
		Long auditados = comoTenant(tenant, () -> jdbc.queryForObject(
				"SELECT count(*) FROM audit.access_log WHERE action = 'PRODUCT_UPDATED' AND resource_id = ?",
				Long.class, micro));
		assertThat(auditados).isEqualTo(1);
	}

	@Test
	void rangosInvalidosResponden400() throws Exception {
		UUID tenant = UUID.randomUUID();
		UUID micro = insertarProducto(tenant, "MICROCREDITO", "1000.00", "20000.00", 3, 24, "C", true);

		mvc.perform(como(put("/productos/{id}", micro), tenant, "TENANT_ADMIN")
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"activo":true,"montoMinimo":"30000","montoMaximo":"2000","plazoMinimoMeses":6,"plazoMaximoMeses":36}
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void unTenantNoVeNiCambiaElCatalogoDeOtro() throws Exception {
		UUID tenantA = UUID.randomUUID();
		UUID tenantB = UUID.randomUUID();
		UUID productoDeB = insertarProducto(tenantB, "PYME", "50000.00", "700000.00", 6, 84, "B", true);

		mvc.perform(como(get("/productos"), tenantA, "TENANT_ADMIN"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
		mvc.perform(como(put("/productos/{id}", productoDeB), tenantA, "TENANT_ADMIN")
				.contentType(MediaType.APPLICATION_JSON).content(CAMBIOS))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

		assertThat(activo(tenantB, productoDeB)).isTrue();
	}

	// --- utilidades ------------------------------------------------------------

	private static MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder request, UUID tenant, String rol) {
		return request.with(jwt().jwt(token -> token.subject("usuario-" + rol).claim("tenant_id", tenant.toString()))
			.authorities(new SimpleGrantedAuthority("ROLE_" + rol)));
	}

	private Boolean activo(UUID tenant, UUID producto) {
		return comoTenant(tenant, () -> jdbc.queryForObject(
				"SELECT active FROM recommendation.products WHERE id = ?", Boolean.class, producto));
	}

	private static <T> T comoTenant(UUID tenant, java.util.function.Supplier<T> work) {
		TenantContext.set(tenant);
		try {
			return work.get();
		}
		finally {
			TenantContext.clear();
		}
	}

	/** Como superusuario del contenedor (la app no tiene INSERT sobre el catálogo). */
	private UUID insertarProducto(UUID tenant, String tipo, String min, String max, int plazoMin, int plazoMax,
			String banda, boolean activo) throws SQLException {
		UUID id = UUID.randomUUID();
		try (Connection admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword()); PreparedStatement ps = admin.prepareStatement("""
						INSERT INTO recommendation.products
						    (id, tenant_id, product_type, min_amount, max_amount, min_term_months,
						     max_term_months, annual_rate, min_band, active)
						VALUES (?, ?, ?, ?, ?, ?, ?, 11.50, ?, ?)
						""")) {
			ps.setObject(1, id);
			ps.setObject(2, tenant);
			ps.setString(3, tipo);
			ps.setBigDecimal(4, new BigDecimal(min));
			ps.setBigDecimal(5, new BigDecimal(max));
			ps.setInt(6, plazoMin);
			ps.setInt(7, plazoMax);
			ps.setString(8, banda);
			ps.setBoolean(9, activo);
			ps.executeUpdate();
		}
		return id;
	}

}
