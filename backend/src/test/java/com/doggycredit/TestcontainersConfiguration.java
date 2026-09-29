package com.doggycredit;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// Misma versión que infra/compose.yaml (PostgreSQL 17, decisión de arquitectura).
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
	}

}
