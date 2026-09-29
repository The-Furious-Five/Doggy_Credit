package com.doggycredit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
}
