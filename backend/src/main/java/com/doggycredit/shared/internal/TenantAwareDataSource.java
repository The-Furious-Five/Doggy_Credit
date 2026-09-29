package com.doggycredit.shared.internal;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import com.doggycredit.shared.TenantContext;

/**
 * SPIKE-1: capa 2 de aislamiento (PostgreSQL). En cada conexión que sale del
 * pool, hace dos cosas antes de entregarla:
 * <ol>
 * <li>{@code SET ROLE} {@value #APP_ROLE}: el rol con el que arranca la
 * conexión (p. ej. {@code doggy_app}) es superusuario —lo crea así la
 * imagen oficial de PostgreSQL para cualquier {@code POSTGRES_USER}—, y
 * PostgreSQL exime a los superusuarios de Row-Level Security de forma
 * incondicional, incluso con {@code FORCE ROW LEVEL SECURITY} (comprobado:
 * la prueba de concurrencia de RLS pasaba en falso hasta agregar esto).
 * {@code SET ROLE} a un rol sin superusuario (creado por la migración
 * V2026_09_29_1401) hace que el resto de la sesión sí quede sujeto a RLS.
 * Flyway nunca pasa por aquí: usa {@code flywayDataSource}, una conexión
 * aparte que no envuelve esta clase, porque migrar sí necesita privilegios
 * de administrador (crear el rol restringido, entre otras cosas).</li>
 * <li>fija la variable de sesión {@code app.tenant_id}, que las políticas
 * de RLS leen con {@code current_setting}.</li>
 * </ol>
 * Ambas se rehacen en cada {@link #getConnection()}, no solo al crear la
 * conexión física: con HikariCP una misma conexión se reutiliza para
 * peticiones de tenants distintos a lo largo de su vida.
 * <p>
 * Sin tenant en {@link TenantContext} se hace {@code RESET} (no
 * {@code SET ''}): {@code current_setting(..., true)} devuelve entonces
 * {@code NULL}, y una política de RLS que compare {@code tenant_id = NULL}
 * no es nunca verdadera —cero filas en SELECT, y la política falla en
 * INSERT/UPDATE—, sin arriesgar un error de conversión de tipo.
 */
final class TenantAwareDataSource extends DelegatingDataSource {

    static final String APP_ROLE = "doggycredit_app";

    TenantAwareDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return withTenantApplied(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return withTenantApplied(super.getConnection(username, password));
    }

    private static Connection withTenantApplied(Connection connection) throws SQLException {
        UUID tenant = TenantContext.currentOrNone();
        // UUID#toString() es siempre el formato fijo 8-4-4-4-12 en hex: no hay
        // forma de inyectar SQL con este valor, por eso se concatena sin bind.
        // Un solo execute = una sola ida y vuelta por préstamo del pool.
        String sql = "SET ROLE " + APP_ROLE + "; "
                + (tenant != null ? "SET app.tenant_id = '" + tenant + "'" : "RESET app.tenant_id");
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException | RuntimeException e) {
            // La conexión ya salió del pool: sin cerrarla aquí se fuga, y con
            // suficientes fallos HikariCP se queda sin conexiones.
            connection.close();
            throw e;
        }
        return connection;
    }
}
