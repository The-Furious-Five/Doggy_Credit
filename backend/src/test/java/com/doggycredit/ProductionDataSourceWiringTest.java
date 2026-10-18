package com.doggycredit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Arranca el contexto como en producción: conexión solo por
 * {@code spring.datasource.*}, sin {@code @ServiceConnection}. El resto de
 * las pruebas usa {@link TestcontainersConfiguration}, cuyo
 * {@code JdbcConnectionDetails} puede ocultar que la app no arranca fuera
 * de los tests (Spring Boot solo crea el suyo si no hay otro DataSource).
 */
@SpringBootTest
class ProductionDataSourceWiringTest {

	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

	static {
		// Sin stop() manual: el contexto de Spring queda en caché con el pool
		// abierto; Testcontainers (Ryuk) apaga el contenedor al terminar la JVM.
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("doggycredit.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("doggycredit.datasource.username", () -> "doggycredit_app");
		registry.add("doggycredit.datasource.password", () -> "dev_only_app_role");
	}

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void arrancaConPropiedadesYLaAppCorreComoRolRestringido() {
		Map<String, Object> row = jdbc.queryForMap("""
				select current_user,
				       (select rolsuper from pg_roles where rolname = current_user) as rolsuper,
				       (select rolbypassrls from pg_roles where rolname = current_user) as rolbypassrls
				""");
		assertThat(row.get("current_user")).isEqualTo("doggycredit_app");
		assertThat(row.get("rolsuper")).isEqualTo(false);
		assertThat(row.get("rolbypassrls")).isEqualTo(false);
	}

	/**
	 * A-05 (LOS-67, DEBT-018): la app entra con su propio LOGIN, así que RESET ROLE la deja
	 * en doggycredit_app y no en un superusuario; tampoco puede asumir el rol de Flyway.
	 */
	@Test
	void resetRoleNoDevuelvePrivilegiosNiSaltaRls() {
		jdbc.execute((ConnectionCallback<Void>) connection -> {
			try (var statement = connection.createStatement()) {
				statement.execute("RESET ROLE");
				try (var rs = statement.executeQuery("""
						select session_user, current_user,
						       (select rolsuper or rolbypassrls from pg_roles where rolname = current_user) as privileged,
						       (select count(*) from consent.consents) as visible
						""")) {
					rs.next();
					assertThat(rs.getString("session_user")).isEqualTo("doggycredit_app");
					assertThat(rs.getString("current_user")).isEqualTo("doggycredit_app");
					assertThat(rs.getBoolean("privileged")).isFalse();
					assertThat(rs.getLong("visible")).as("sin app.tenant_id, RLS no deja ver filas").isZero();
				}
				assertThatThrownBy(() -> statement.execute("SET ROLE \"" + POSTGRES.getUsername() + "\""))
					.hasMessageContaining("permission denied");
			}
			return null;
		});
	}
}
