package com.doggycredit.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.shared.TenantContext;

/**
 * C-02 (LOS-82): recommendation.products contra un PostgreSQL real.
 * <p>
 * Las lecturas y cambios pasan por el {@code JdbcTemplate} de la app, que hace
 * {@code SET ROLE doggycredit_app} y fija {@code app.tenant_id} desde
 * {@link TenantContext}: se prueban los mismos permisos y la misma RLS que usará
 * el endpoint de C-06. Los datos se preparan con el superusuario del contenedor,
 * porque la app no tiene INSERT sobre el catálogo (lo carga la semilla).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProductCatalogTableTests {

	private static final String INSUFFICIENT_PRIVILEGE = "42501";

	private static final String UNIQUE_VIOLATION = "23505";

	private static final String CHECK_VIOLATION = "23514";

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PostgreSQLContainer postgres;

	@Test
	void cadaTenantVeSoloSuCatalogo() throws SQLException {
		UUID tenantA = UUID.randomUUID();
		UUID tenantB = UUID.randomUUID();
		insertProduct(tenantA, "MICROCREDITO", "1000", "20000");
		insertProduct(tenantB, "MICROCREDITO", "1000", "20000");

		Long visibles = asTenant(tenantA, () -> jdbc.queryForObject(
				"SELECT count(*) FROM recommendation.products WHERE tenant_id IN (?, ?)", Long.class, tenantA, tenantB));

		assertThat(visibles).isEqualTo(1);
	}

	@Test
	void sinTenantFijadoNoSeVeNingunProducto() throws SQLException {
		insertProduct(UUID.randomUUID(), "PYME", "50000", "700000");

		TenantContext.clear();
		Long visibles = jdbc.queryForObject("SELECT count(*) FROM recommendation.products", Long.class);

		assertThat(visibles).isZero();
	}

	@Test
	void elTenantPuedeDesactivarSuProducto() throws SQLException {
		UUID tenant = UUID.randomUUID();
		UUID product = insertProduct(tenant, "VERDE", "10000", "150000");

		Integer actualizadas = asTenant(tenant, () -> jdbc.update(
				"UPDATE recommendation.products SET active = false WHERE id = ?", product));
		Boolean activo = asTenant(tenant, () -> jdbc.queryForObject(
				"SELECT active FROM recommendation.products WHERE id = ?", Boolean.class, product));

		assertThat(actualizadas).isEqualTo(1);
		assertThat(activo).isFalse();
	}

	@Test
	void unTenantNoPuedeModificarElCatalogoDeOtro() throws SQLException {
		UUID tenantA = UUID.randomUUID();
		UUID productoDeB = insertProduct(UUID.randomUUID(), "VERDE", "10000", "150000");

		Integer actualizadas = asTenant(tenantA, () -> jdbc.update(
				"UPDATE recommendation.products SET active = false WHERE id = ?", productoDeB));

		assertThat(actualizadas).isZero();
	}

	@Test
	void laAppNoPuedeCrearNiBorrarProductos() {
		UUID tenant = UUID.randomUUID();

		assertSqlState(INSUFFICIENT_PRIVILEGE, () -> asTenant(tenant, () -> jdbc.update("""
				INSERT INTO recommendation.products
				    (id, tenant_id, product_type, min_amount, max_amount, min_term_months,
				     max_term_months, annual_rate, min_band)
				VALUES (?, ?, 'PYME', 50000, 700000, 6, 84, 10, 'B')
				""", UUID.randomUUID(), tenant)));
		assertSqlState(INSUFFICIENT_PRIVILEGE, () -> asTenant(tenant, () -> jdbc.update(
				"DELETE FROM recommendation.products")));
	}

	@Test
	void unSoloProductoDeCadaTipoPorTenant() throws SQLException {
		UUID tenant = UUID.randomUUID();
		insertProduct(tenant, "MICROCREDITO", "1000", "20000");

		assertThatThrownBy(() -> insertProduct(tenant, "MICROCREDITO", "2000", "30000"))
			.isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(UNIQUE_VIOLATION));
	}

	@Test
	void elMontoMaximoNoPuedeSerMenorQueElMinimo() {
		assertThatThrownBy(() -> insertProduct(UUID.randomUUID(), "PYME", "700000", "50000"))
			.isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(CHECK_VIOLATION));
	}

	// --- utilidades ------------------------------------------------------------

	private static <T> T asTenant(UUID tenant, Supplier<T> work) {
		TenantContext.set(tenant);
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

	/** Inserta como superusuario del contenedor (se salta RLS, como la semilla de Flyway). */
	private UUID insertProduct(UUID tenant, String type, String minAmount, String maxAmount) throws SQLException {
		UUID id = UUID.randomUUID();
		String sql = """
				INSERT INTO recommendation.products
				    (id, tenant_id, product_type, min_amount, max_amount, min_term_months,
				     max_term_months, annual_rate, min_band)
				VALUES (?, ?, ?, ?, ?, 3, 24, 10, 'C')
				""";
		try (Connection admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword()); PreparedStatement ps = admin.prepareStatement(sql)) {
			ps.setObject(1, id);
			ps.setObject(2, tenant);
			ps.setString(3, type);
			ps.setBigDecimal(4, new BigDecimal(minAmount));
			ps.setBigDecimal(5, new BigDecimal(maxAmount));
			ps.executeUpdate();
		}
		return id;
	}

}
