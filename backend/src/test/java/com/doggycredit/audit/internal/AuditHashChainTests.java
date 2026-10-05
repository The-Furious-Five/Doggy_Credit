package com.doggycredit.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.audit.AuditTrail;
import com.doggycredit.audit.internal.AuditChainVerifier.Break;
import com.doggycredit.shared.TenantContext;

/**
 * C-04 (LOS-84): cadena de hash de audit.access_log contra un PostgreSQL real. Los
 * registros se escriben por la API real del módulo (AuditTrail) y la manipulación se
 * simula como en la demo: un superusuario desactiva el trigger append-only y altera o
 * borra una fila. La verificación tiene que detectarlo.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuditHashChainTests {

	private static final String GENESIS = "0".repeat(64);

	@Autowired
	AuditTrail audit;

	@Autowired
	AuditChainVerifier verifier;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PostgreSQLContainer postgres;

	@Test
	void cadaRegistroGuardaElHashDelAnterior() {
		UUID tenant = UUID.randomUUID();
		registrar(tenant, 3);

		List<Map<String, Object>> filas = comoTenant(tenant, () -> jdbc.queryForList(
				"SELECT chain_seq, prev_hash, row_hash FROM audit.access_log ORDER BY chain_seq"));

		assertThat(filas).extracting(fila -> ((Number) fila.get("chain_seq")).longValue()).containsExactly(1L, 2L, 3L);
		assertThat(filas.get(0).get("prev_hash")).isEqualTo(GENESIS);
		assertThat(filas.get(1).get("prev_hash")).isEqualTo(filas.get(0).get("row_hash"));
		assertThat(filas.get(2).get("prev_hash")).isEqualTo(filas.get(1).get("row_hash"));
		assertThat(comoTenant(tenant, () -> verifier.verify(tenant))).isEmpty();
	}

	@Test
	void detectaUnaFilaAlterada() throws SQLException {
		UUID tenant = UUID.randomUUID();
		registrar(tenant, 3);

		manipularComoSuperusuario("UPDATE audit.access_log SET action = 'MANIPULADO' WHERE tenant_id = ? AND chain_seq = 2",
				tenant);

		assertThat(comoTenant(tenant, () -> verifier.verify(tenant))).containsExactly(new Break(2, "HASH_MISMATCH"));
	}

	@Test
	void detectaUnaFilaBorrada() throws SQLException {
		UUID tenant = UUID.randomUUID();
		registrar(tenant, 3);

		manipularComoSuperusuario("DELETE FROM audit.access_log WHERE tenant_id = ? AND chain_seq = 2", tenant);

		assertThat(comoTenant(tenant, () -> verifier.verify(tenant))).containsExactly(new Break(3, "BROKEN_LINK"));
	}

	@Test
	void conEscriturasSimultaneasLaCadenaNoSeBifurca() throws Exception {
		UUID tenant = UUID.randomUUID();
		int hilos = 20;
		List<Callable<Void>> tareas = new ArrayList<>();
		for (int i = 0; i < hilos; i++) {
			tareas.add(() -> {
				registrar(tenant, 1);
				return null;
			});
		}
		try (ExecutorService pool = Executors.newFixedThreadPool(hilos)) {
			for (Future<Void> resultado : pool.invokeAll(tareas)) {
				resultado.get();
			}
		}

		List<Long> secuencias = comoTenant(tenant, () -> jdbc.queryForList(
				"SELECT chain_seq FROM audit.access_log ORDER BY chain_seq", Long.class));
		assertThat(secuencias).hasSize(hilos).doesNotHaveDuplicates().startsWith(1L).endsWith((long) hilos);
		assertThat(comoTenant(tenant, () -> verifier.verify(tenant))).isEmpty();
	}

	// --- utilidades ------------------------------------------------------------

	private void registrar(UUID tenant, int cuantos) {
		comoTenant(tenant, () -> {
			for (int i = 0; i < cuantos; i++) {
				audit.record(tenant, "APPLICANT_READ", "APPLICANT", UUID.randomUUID());
			}
			return null;
		});
	}

	private static <T> T comoTenant(UUID tenant, Supplier<T> work) {
		TenantContext.set(tenant);
		try {
			return work.get();
		}
		finally {
			TenantContext.clear();
		}
	}

	/** Lo que haría un atacante con acceso de superusuario: apagar la protección y tocar la tabla. */
	private void manipularComoSuperusuario(String sql, UUID tenant) throws SQLException {
		try (Connection admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword())) {
			admin.setAutoCommit(false);
			try (Statement st = admin.createStatement()) {
				st.execute("ALTER TABLE audit.access_log DISABLE TRIGGER audit_no_edit");
			}
			try (PreparedStatement ps = admin.prepareStatement(sql)) {
				ps.setObject(1, tenant);
				assertThat(ps.executeUpdate()).isEqualTo(1);
			}
			try (Statement st = admin.createStatement()) {
				st.execute("ALTER TABLE audit.access_log ENABLE TRIGGER audit_no_edit");
			}
			admin.commit();
		}
	}

}
