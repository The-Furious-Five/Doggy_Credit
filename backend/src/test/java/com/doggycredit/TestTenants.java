package com.doggycredit;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Registra en {@code tenancy.tenants} el tenant inventado de una prueba. La
 * evaluación exige un tenant registrado (cuota por plan), y la app solo puede
 * leer esa tabla: se escribe con la conexión de administrador del contenedor.
 */
public final class TestTenants {

	private TestTenants() {
	}

	public static void register(PostgreSQLContainer postgres, String tenantId, String plan) {
		String sql = """
				INSERT INTO tenancy.tenants (id, nombre, nit, tipo, plan, pais, moneda)
				VALUES (?::uuid, ?, ?, 'IMF', ?, 'BO', 'BOB')
				ON CONFLICT (id) DO NOTHING
				""";
		try (Connection admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword()); PreparedStatement ps = admin.prepareStatement(sql)) {
			ps.setString(1, tenantId);
			ps.setString(2, "Tenant de prueba " + tenantId.substring(0, 8));
			ps.setString(3, tenantId.replace("-", "").substring(0, 20));
			ps.setString(4, plan);
			ps.executeUpdate();
		}
		catch (SQLException exception) {
			throw new IllegalStateException("No se pudo registrar el tenant de prueba " + tenantId, exception);
		}
	}

}
