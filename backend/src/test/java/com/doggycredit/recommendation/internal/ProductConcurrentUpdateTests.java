package com.doggycredit.recommendation.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.shared.TenantContext;

/**
 * Dos administradores editan el mismo producto a la vez: el segundo guardado choca con
 * {@code @Version} contra PostgreSQL real. ConcurrentUpdateAdvice lo traduce a 409.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProductConcurrentUpdateTests {

	@Autowired
	ProductRepository products;

	@Autowired
	TransactionTemplate transactions;

	@Autowired
	PostgreSQLContainer postgres;

	@Test
	void siOtroGuardaPrimeroElSegundoGuardadoChocaConLaVersion() throws SQLException {
		UUID tenant = UUID.randomUUID();
		UUID product = insertProduct(tenant);
		TenantContext.set(tenant);
		try {
			assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
				Product loaded = products.findById(product).orElseThrow();
				// Mientras tanto, otra persona guarda el mismo producto (sube la versión).
				bumpVersion(product);
				loaded.update(false, new BigDecimal("1000"), new BigDecimal("20000"), 3, 24);
			})).isInstanceOf(ObjectOptimisticLockingFailureException.class);
		}
		finally {
			TenantContext.clear();
		}
	}

	private Connection admin() throws SQLException {
		return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
	}

	private void bumpVersion(UUID product) {
		try (Connection admin = admin(); PreparedStatement ps = admin.prepareStatement(
				"UPDATE recommendation.products SET version = version + 1 WHERE id = ?")) {
			ps.setObject(1, product);
			ps.executeUpdate();
		}
		catch (SQLException exception) {
			throw new IllegalStateException(exception);
		}
	}

	/** Como superusuario del contenedor (la app no tiene INSERT sobre el catálogo). */
	private UUID insertProduct(UUID tenant) throws SQLException {
		UUID id = UUID.randomUUID();
		try (Connection admin = admin(); PreparedStatement ps = admin.prepareStatement("""
				INSERT INTO recommendation.products
				    (id, tenant_id, product_type, min_amount, max_amount, min_term_months,
				     max_term_months, annual_rate, min_band, active)
				VALUES (?, ?, 'MICROCREDITO', 1000, 20000, 3, 24, 11.50, 'C', true)
				""")) {
			ps.setObject(1, id);
			ps.setObject(2, tenant);
			ps.executeUpdate();
		}
		return id;
	}

}
