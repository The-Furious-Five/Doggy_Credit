package com.doggycredit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Meta-test de aislamiento (capítulo 5.4 de la guía): toda tabla con columna
 * tenant_id debe tener RLS habilitado, forzado y la política tenant_isolation.
 * Una tabla nueva que lo olvide rompe el build; no es una convención de palabra.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RowLevelSecurityCoverageTests {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void todaTablaConTenantIdTieneRlsForzadoYPoliticaTenantIsolation() {
		List<String> tablesWithoutRls = jdbc.queryForList("""
				SELECT c.oid::regclass::text
				FROM pg_class c
				JOIN pg_namespace n ON n.oid = c.relnamespace
				JOIN pg_attribute a ON a.attrelid = c.oid
				     AND a.attname = 'tenant_id' AND NOT a.attisdropped
				WHERE c.relkind IN ('r', 'p')
				  AND n.nspname NOT IN ('pg_catalog', 'information_schema')
				  AND (NOT c.relrowsecurity
				       OR NOT c.relforcerowsecurity
				       OR NOT EXISTS (SELECT 1 FROM pg_policy p
				                      WHERE p.polrelid = c.oid AND p.polname = 'tenant_isolation'))
				ORDER BY 1
				""", String.class);

		assertThat(tablesWithoutRls)
				.as("Tablas con tenant_id sin RLS completo (ENABLE + FORCE + tenant_isolation)")
				.isEmpty();
	}
}
