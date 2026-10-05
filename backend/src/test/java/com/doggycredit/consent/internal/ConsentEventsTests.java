package com.doggycredit.consent.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.consent.ConsentGranted;
import com.doggycredit.consent.Source;
import com.doggycredit.shared.TenantContext;

/** D-03: otorgar un consentimiento publica {@link ConsentGranted} en el outbox. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@EnableScenarios
class ConsentEventsTests {

	private static final UUID TENANT = UUID.fromString("66666666-6666-4666-8666-666666666666");

	@Autowired
	ConsentService consents;

	@Test
	void otorgarConsentimientoPublicaConsentGranted(Scenario scenario) {
		// SPIKE-1 (RLS): fuera de una petición HTTP nadie fija el tenant.
		TenantContext.set(TENANT);
		try {
			scenario.stimulate(() -> consents.grant(TENANT, "CI-EVENT-0001", new String[] { "BANCO", "BURO" },
					"Evaluación de crédito", 30, "analista-evento"))
				.andWaitForEventOfType(ConsentGranted.class)
				.toArriveAndVerify((event, consent) -> {
					assertThat(event.consentId()).isEqualTo(consent.getId());
					assertThat(event.tenantId()).isEqualTo(TENANT);
					assertThat(event.fuentes()).containsExactly(Source.BANCO, Source.BURO);
					assertThat(event.finalidad()).isEqualTo("Evaluación de crédito");
					assertThat(event.vigenciaDias()).isEqualTo(30);
					assertThat(event.expiraEn()).isEqualTo(consent.getExpiraEn());
					assertThat(event.eventId()).isNotNull();
				});
		}
		finally {
			TenantContext.clear();
		}
	}

}
