package com.doggycredit.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.doggycredit.TestcontainersConfiguration;

/**
 * RF-PER-01.1 (LOS-58): customer.applicant contra un PostgreSQL real, como
 * doggy_app. Prueba la tabla (RLS, permisos y unicidad del documento), no el
 * servicio: el endpoint llega cuando el contrato /solicitantes esté en main.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ApplicantTableRlsTests {

	private static final String INSUFFICIENT_PRIVILEGE = "42501";

	private static final String UNIQUE_VIOLATION = "23505";

	@Autowired
	PostgreSQLContainer postgres;

	@Test
	void laAppRegistraYLeeSolicitantesDeSuTenant() throws SQLException {
		UUID tenant = UUID.randomUUID();

		try (Connection app = appConnection(tenant)) {
			insert(app, tenant, randomDocumentRef());
			assertThat(countVisible(app)).isEqualTo(1);
			app.commit();
		}
	}

	@Test
	void elMismoDocumentoNoSeDuplicaDentroDelTenant() throws SQLException {
		UUID tenant = UUID.randomUUID();
		String documentRef = randomDocumentRef();

		try (Connection app = appConnection(tenant)) {
			insert(app, tenant, documentRef);
			assertThatThrownBy(() -> insert(app, tenant, documentRef))
					.isInstanceOfSatisfying(SQLException.class,
							e -> assertThat(e.getSQLState()).isEqualTo(UNIQUE_VIOLATION));
		}
	}

	@Test
	void elMismoDocumentoPuedeExistirEnOtroTenant() throws SQLException {
		// En la app el HMAC incluye el tenant, así que las referencias ya difieren;
		// aquí se prueba que la restricción UNIQUE incluye tenant_id.
		String documentRef = randomDocumentRef();

		for (UUID tenant : new UUID[] { UUID.randomUUID(), UUID.randomUUID() }) {
			try (Connection app = appConnection(tenant)) {
				insert(app, tenant, documentRef);
				app.commit();
			}
		}
	}

	@Test
	void unTenantNoVeLosSolicitantesDeOtro() throws SQLException {
		UUID tenantA = UUID.randomUUID();
		UUID tenantB = UUID.randomUUID();

		try (Connection appB = appConnection(tenantB)) {
			insert(appB, tenantB, randomDocumentRef());
			appB.commit();
		}

		try (Connection appA = appConnection(tenantA)) {
			assertThat(countVisible(appA)).isZero();
			appA.commit();
		}
	}

	@Test
	void unTenantNoPuedeRegistrarSolicitantesAOtroTenant() throws SQLException {
		try (Connection appA = appConnection(UUID.randomUUID())) {
			assertThatThrownBy(() -> insert(appA, UUID.randomUUID(), randomDocumentRef()))
					.isInstanceOfSatisfying(SQLException.class,
							e -> assertThat(e.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
		}
	}

	@Test
	void sinTenantFijadoNoSeVeNiSeRegistraNada() throws SQLException {
		try (Connection app = DriverManager.getConnection(postgres.getJdbcUrl(), "doggy_app", "doggy_app")) {
			app.setAutoCommit(false);
			assertThat(countVisible(app)).isZero();
			assertThatThrownBy(() -> insert(app, UUID.randomUUID(), randomDocumentRef()))
					.isInstanceOfSatisfying(SQLException.class,
							e -> assertThat(e.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
		}
	}

	@Test
	void laAppNoPuedeBorrarSolicitantes() throws SQLException {
		UUID tenant = UUID.randomUUID();

		try (Connection app = appConnection(tenant)) {
			insert(app, tenant, randomDocumentRef());
			assertThatThrownBy(() -> {
				try (Statement st = app.createStatement()) {
					st.execute("DELETE FROM customer.applicant");
				}
			}).isInstanceOfSatisfying(SQLException.class,
					e -> assertThat(e.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
		}
	}

	// --- utilidades ------------------------------------------------------------

	/** Conexión como doggy_app con el tenant fijado solo para esta transacción. */
	private Connection appConnection(UUID tenant) throws SQLException {
		Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), "doggy_app", "doggy_app");
		connection.setAutoCommit(false);
		try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
			ps.setString(1, tenant.toString());
			ps.execute();
		}
		return connection;
	}

	/** Simula el HMAC-SHA-256 en hex (64 caracteres) que calculará la aplicación. */
	private static String randomDocumentRef() {
		return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
	}

	private static void insert(Connection connection, UUID tenant, String documentRef) throws SQLException {
		String sql = """
				INSERT INTO customer.applicant
				    (id, tenant_id, document_ref, document_ciphertext, activity,
				     activity_seniority_months, monthly_income, income_currency, credit_destination)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setObject(1, UUID.randomUUID());
			ps.setObject(2, tenant);
			ps.setString(3, documentRef);
			ps.setString(4, "cifrado-de-prueba");
			ps.setString(5, "Comercio minorista");
			ps.setInt(6, 36);
			ps.setBigDecimal(7, new BigDecimal("4500.00"));
			ps.setString(8, "BOB");
			ps.setString(9, "Capital de trabajo");
			ps.executeUpdate();
		}
	}

	private static long countVisible(Connection connection) throws SQLException {
		try (Statement st = connection.createStatement();
				ResultSet rs = st.executeQuery("SELECT count(*) FROM customer.applicant")) {
			rs.next();
			return rs.getLong(1);
		}
	}
}
