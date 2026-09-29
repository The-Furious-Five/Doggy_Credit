package com.doggycredit.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * RF-SEG-03.1 (LOS-57): prueba de manipulación de audit.access_log contra un
 * PostgreSQL real. Se usa JDBC directo (no JPA) porque lo que se prueba son
 * las defensas de la base de datos: permisos, RLS y triggers.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccessLogAppendOnlyTests {

	/** Código SQLSTATE de "permiso denegado", tanto del GRANT como del trigger. */
	private static final String INSUFFICIENT_PRIVILEGE = "42501";

	@Autowired
	PostgreSQLContainer postgres;

	@Test
	void laAppInsertaYLeeFilasDeSuPropioTenant() throws SQLException {
		UUID tenant = UUID.randomUUID();

		try (Connection app = appConnection(tenant)) {
			insert(app, tenant);
			assertThat(countVisible(app)).isEqualTo(1);
			app.commit();
		}
	}

	@Test
	void laAppNoPuedeHacerUpdate() throws SQLException {
		UUID tenant = UUID.randomUUID();

		try (Connection app = appConnection(tenant)) {
			insert(app, tenant);
			assertRejected(app, "UPDATE audit.access_log SET action = 'borrado'");
		}
	}

	@Test
	void laAppNoPuedeHacerDelete() throws SQLException {
		UUID tenant = UUID.randomUUID();

		try (Connection app = appConnection(tenant)) {
			insert(app, tenant);
			assertRejected(app, "DELETE FROM audit.access_log");
		}
	}

	@Test
	void laAppNoPuedeHacerTruncate() throws SQLException {
		try (Connection app = appConnection(UUID.randomUUID())) {
			assertRejected(app, "TRUNCATE audit.access_log");
		}
	}

	@Test
	void unRolElevadoTampocoPuedeModificarPorqueLoFrenaElTrigger() throws SQLException {
		// El superusuario se salta los permisos y RLS: solo el trigger lo detiene.
		try (Connection admin = adminConnection()) {
			admin.setAutoCommit(false);
			insert(admin, UUID.randomUUID());
			admin.commit();

			assertRejectedByTrigger(admin, "UPDATE audit.access_log SET action = 'borrado'");
			assertRejectedByTrigger(admin, "DELETE FROM audit.access_log");
			assertRejectedByTrigger(admin, "TRUNCATE audit.access_log");
		}
	}

	@Test
	void unTenantNoVeLasFilasDeOtro() throws SQLException {
		UUID tenantA = UUID.randomUUID();
		UUID tenantB = UUID.randomUUID();

		try (Connection appB = appConnection(tenantB)) {
			insert(appB, tenantB);
			appB.commit();
		}

		try (Connection appA = appConnection(tenantA)) {
			insert(appA, tenantA);
			assertThat(countVisible(appA)).isEqualTo(1);
			assertThat(countForTenant(appA, tenantB)).isZero();
			appA.commit();
		}
	}

	@Test
	void unTenantNoPuedeInsertarAuditoriaANombreDeOtro() throws SQLException {
		UUID tenantA = UUID.randomUUID();

		try (Connection appA = appConnection(tenantA)) {
			assertThatThrownBy(() -> insert(appA, UUID.randomUUID()))
					.isInstanceOfSatisfying(SQLException.class,
							e -> assertThat(e.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
		}
	}

	@Test
	void sinTenantFijadoNoSeVeNiSeInsertaNada() throws SQLException {
		// Fallo cerrado: una conexión que olvidó fijar app.tenant_id.
		try (Connection app = restrictedConnection()) {
			assertThat(countVisible(app)).isZero();
			assertThatThrownBy(() -> insert(app, UUID.randomUUID()))
					.isInstanceOfSatisfying(SQLException.class,
							e -> assertThat(e.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
		}
	}

	// --- utilidades ------------------------------------------------------------

	/**
	 * Conexión con el mismo rol efectivo que la app: se entra como el usuario
	 * del contenedor y se baja a doggycredit_app con SET ROLE, igual que
	 * TenantAwareDataSource (SPIKE-1). Sin tenant fijado.
	 */
	private Connection restrictedConnection() throws SQLException {
		Connection connection = adminConnection();
		connection.setAutoCommit(false);
		try (Statement st = connection.createStatement()) {
			st.execute("SET ROLE doggycredit_app");
		}
		return connection;
	}

	/** Como {@link #restrictedConnection()}, con el tenant fijado solo para esta transacción. */
	private Connection appConnection(UUID tenant) throws SQLException {
		Connection connection = restrictedConnection();
		try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
			ps.setString(1, tenant.toString());
			ps.execute();
		}
		return connection;
	}

	/** Conexión con el superusuario del contenedor (el "rol elevado" de la guía). */
	private Connection adminConnection() throws SQLException {
		return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
	}

	private static void insert(Connection connection, UUID tenant) throws SQLException {
		String sql = "INSERT INTO audit.access_log (id, tenant_id, actor, action, resource_type, correlation_id)"
				+ " VALUES (?, ?, ?, ?, ?, ?)";
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setObject(1, UUID.randomUUID());
			ps.setObject(2, tenant);
			ps.setString(3, "usr_test");
			ps.setString(4, "APPLICANT_READ");
			ps.setString(5, "APPLICANT");
			ps.setString(6, UUID.randomUUID().toString());
			ps.executeUpdate();
		}
	}

	private static long countVisible(Connection connection) throws SQLException {
		try (Statement st = connection.createStatement();
				ResultSet rs = st.executeQuery("SELECT count(*) FROM audit.access_log")) {
			rs.next();
			return rs.getLong(1);
		}
	}

	private static long countForTenant(Connection connection, UUID tenant) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("SELECT count(*) FROM audit.access_log WHERE tenant_id = ?")) {
			ps.setObject(1, tenant);
			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getLong(1);
			}
		}
	}

	private static void assertRejected(Connection connection, String sql) {
		assertThatThrownBy(() -> {
			try (Statement st = connection.createStatement()) {
				st.execute(sql);
			}
		}).isInstanceOfSatisfying(SQLException.class,
				e -> assertThat(e.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
	}

	private static void assertRejectedByTrigger(Connection connection, String sql) throws SQLException {
		assertThatThrownBy(() -> {
			try (Statement st = connection.createStatement()) {
				st.execute(sql);
			}
		}).isInstanceOfSatisfying(SQLException.class,
				e -> assertThat(e.getMessage()).contains("es de solo inserción"));
		connection.rollback();
	}
}
