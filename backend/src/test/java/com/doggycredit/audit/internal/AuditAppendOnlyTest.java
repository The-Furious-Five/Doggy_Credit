package com.doggycredit.audit.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.doggycredit.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuditAppendOnlyTest {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void elTriggerBloqueaManipulacionInclusoParaElPropietarioDeLaTabla() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into audit.access_log
				(id, tenant_id, actor, action, resource_type, resource_id, correlation_id)
				values (?, ?, 'elevated-test', 'READ', 'APPLICANT', ?, 'test-correlation')
				""", id, UUID.randomUUID(), UUID.randomUUID());

		assertThatThrownBy(() -> jdbc.update(
				"update audit.access_log set action = 'TAMPERED' where id = ?", id))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.update("delete from audit.access_log where id = ?", id))
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("append-only");
	}

}
