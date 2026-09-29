package com.doggycredit.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.consent.ConsentRequiredException;
import com.doggycredit.consent.Source;
import com.doggycredit.shared.TenantContext;

/**
 * Criterio de salida de RF-CON-01.1: sin consentimiento vigente para la
 * fuente, el conector no se llama y la respuesta es CONSENT_REQUIRED.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ConnectorGatewayTests {

	private static final UUID TENANT = UUID.randomUUID();

	@Autowired
	ConnectorGateway gateway;

	@Autowired
	JdbcTemplate jdbc;

	/** Conector falso que cuenta sus invocaciones. */
	static class FakeBureau implements SourceConnector<String> {

		final AtomicInteger calls = new AtomicInteger();

		@Override
		public Source source() {
			return Source.BURO;
		}

		@Override
		public String fetch(UUID tenantId, String documento) {
			calls.incrementAndGet();
			return "informe";
		}

	}

	private final FakeBureau bureau = new FakeBureau();

	private final String documento = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

	/**
	 * SPIKE-1 (RLS): sin esto, gateway.fetch(bureau, TENANT, ...) no vería ni
	 * su propio consentimiento — TenantContext (y con él, app.tenant_id) no
	 * lo puebla nadie fuera de una petición HTTP real, que esta prueba
	 * deliberadamente no usa (llama al gateway directo, como componente).
	 */
	@BeforeEach
	void actuaComoElTenantDePrueba() {
		TenantContext.set(TENANT);
	}

	@AfterEach
	void limpiaContexto() {
		TenantContext.clear();
	}

	@Test
	void sinConsentimientoNoLlamaAlConector() {
		assertRejected();
	}

	@Test
	void consentimientoVigenteLlamaAlConectorConDocumentoNormalizado() {
		insert(TENANT, "{BURO,BANCO}", "ACTIVO", "now() + interval '1 day'");

		assertThat(gateway.fetch(bureau, TENANT, " " + documento.toLowerCase() + " ")).isEqualTo("informe");
		assertThat(bureau.calls).hasValue(1);
	}

	@Test
	void consentimientoExpiradoNoCuenta() {
		insert(TENANT, "{BURO}", "ACTIVO", "now() - interval '1 second'");
		assertRejected();
	}

	@Test
	void consentimientoRevocadoNoCuenta() {
		insert(TENANT, "{BURO}", "REVOCADO", "now() + interval '1 day'");
		assertRejected();
	}

	@Test
	void consentimientoDeOtraFuenteNoCuenta() {
		insert(TENANT, "{BANCO,WALLET}", "ACTIVO", "now() + interval '1 day'");
		assertRejected();
	}

	@Test
	void consentimientoDeOtroTenantNoCuenta() {
		insert(UUID.randomUUID(), "{BURO}", "ACTIVO", "now() + interval '1 day'");
		assertRejected();
	}

	private void assertRejected() {
		assertThatThrownBy(() -> gateway.fetch(bureau, TENANT, documento))
			.isInstanceOfSatisfying(ConsentRequiredException.class, e -> {
				assertThat(e.getBody().getStatus()).isEqualTo(422);
				assertThat(e.getBody().getProperties()).containsEntry("code", "CONSENT_REQUIRED");
				assertThat(e.getBody().getDetail()).isEqualTo("No hay consentimiento vigente para la fuente BURO.");
			});
		assertThat(bureau.calls).hasValue(0);
	}

	private void insert(UUID tenant, String fuentes, String estado, String expiraEn) {
		// RLS (V2026_09_29_1400) exige que app.tenant_id coincida con la fila
		// que se inserta; consentimientoDeOtroTenantNoCuenta siembra a
		// propósito para un tenant distinto de TENANT, así que el INSERT en
		// sí necesita actuar como ESE tenant. Se restaura TENANT al salir:
		// es la identidad ambiente del resto de la prueba (ver @BeforeEach).
		TenantContext.set(tenant);
		try {
			jdbc.update("""
					insert into consent.consents (id, tenant_id, titular_documento, fuentes, finalidad, vigencia_dias,
					  otorgado_en, expira_en, estado, otorgado_por, payload_sha256)
					values (?, ?, ?, ?::text[], 'evaluacion', 90, now() - interval '90 days', %s, ?, 'test', 'x')
					""".formatted(expiraEn), UUID.randomUUID(), tenant, documento, fuentes, estado);
		} finally {
			TenantContext.set(TENANT);
		}
	}

}
