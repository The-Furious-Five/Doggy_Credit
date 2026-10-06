package com.doggycredit.shared.internal;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import com.doggycredit.shared.TenantContext;

/**
 * SPIKE-1 / DEBT-018 (pagada): capa 2 de aislamiento (PostgreSQL). En cada
 * conexión que sale del pool fija la variable de sesión {@code app.tenant_id},
 * que las políticas de RLS leen con {@code current_setting}.
 * <p>
 * Antes de DEBT-018 esta clase también hacía {@code SET ROLE doggycredit_app}
 * porque la app se conectaba como superusuario (el {@code POSTGRES_USER} de la
 * imagen oficial) y necesitaba bajar a un rol sin superusuario para que RLS
 * funcionara. Con DEBT-018 la app se conecta directamente como
 * {@code doggycredit_app} (rol LOGIN propio, sin superusuario), así que el
 * {@code SET ROLE} ya no es necesario.
 * <p>
 * Se rehace en cada {@link #getConnection()}, no solo al crear la conexión
 * física: con HikariCP una misma conexión se reutiliza para peticiones de
 * tenants distintos a lo largo de su vida.
 * <p>
 * Sin tenant en {@link TenantContext} se hace {@code RESET} (no
 * {@code SET ''}): {@code current_setting(..., true)} devuelve entonces
 * {@code NULL}, y una política de RLS que compare {@code tenant_id = NULL}
 * no es nunca verdadera —cero filas en SELECT, y la política falla en
 * INSERT/UPDATE—, sin arriesgar un error de conversión de tipo.
 */
final class TenantAwareDataSource extends DelegatingDataSource {

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
        String sql = tenant != null ? "SET app.tenant_id = '" + tenant + "'" : "RESET app.tenant_id";
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
