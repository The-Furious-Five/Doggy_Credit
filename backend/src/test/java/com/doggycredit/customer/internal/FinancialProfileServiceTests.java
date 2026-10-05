package com.doggycredit.customer.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.consent.Source;
import com.doggycredit.customer.ApplicantNotFoundException;
import com.doggycredit.customer.ApplicantRegistration;
import com.doggycredit.customer.ApplicantRegistry;
import com.doggycredit.customer.FinancialProfile360;
import com.doggycredit.customer.FinancialProfile360.SourceState;
import com.doggycredit.customer.FinancialProfiles;
import com.doggycredit.customer.SourceResult;
import com.doggycredit.integration.BureauReport;
import com.doggycredit.integration.BureauReport.Endeudamiento;
import com.doggycredit.integration.BureauReport.Linaje;
import com.doggycredit.integration.BureauReport.TipoTitular;
import com.doggycredit.shared.Money;
import com.doggycredit.shared.TenantContext;

/** B-04 con base real: el snapshot se persiste cifrado, con RLS, y se relee igual. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FinancialProfileServiceTests {

	private static final UUID TENANT = UUID.fromString("66666666-6666-4666-8666-666666666666");
	private static final UUID OTRO_TENANT = UUID.fromString("77777777-7777-4777-8777-777777777777");

	@Autowired
	ApplicantRegistry applicants;

	@Autowired
	FinancialProfiles profiles;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void persisteCifradoConUnaFuenteCaidaYSoloLoLeeSuTenant() {
		TenantContext.set(TENANT);
		FinancialProfile360 saved;
		UUID applicantId;
		try {
			applicantId = applicants.register(TENANT, new ApplicantRegistration("CI-360-" + UUID.randomUUID(),
					"Comercio", 24, new BigDecimal("4321.00"), Currency.getInstance("BOB"), "Capital")).id();
			BureauReport bureau = new BureauReport(TipoTitular.PERSONA, 60,
					List.of(new Endeudamiento(Source.BURO, "CONSOLIDADO", bob("98765.43"), bob("1200.00"), 0, "A",
							LocalDate.of(2026, 9, 1))),
					new Linaje(Source.BURO, "c/1", "m/1", "f-1", "b".repeat(64), Instant.now()));
			saved = profiles.consolidate(TENANT, applicantId, Map.of(Source.BURO, SourceResult.of(Optional.of(bureau)),
					Source.BANCO, SourceResult.unavailable()));

			assertThat(profiles.latest(TENANT, applicantId)).contains(saved);
			assertThat(saved.sources()).extracting(FinancialProfile360.SourceLineage::state)
				.containsExactly(SourceState.COMPLETA, SourceState.NO_DISPONIBLE, SourceState.NO_CONSULTADA);

			Map<String, Object> row = jdbc.queryForMap(
					"select content_sha256, payload_ciphertext from customer.financial_profiles where id = ?", saved.id());
			assertThat(row.get("content_sha256")).isEqualTo(saved.hash());
			assertThat((String) row.get("payload_ciphertext")).doesNotContain("98765.43").doesNotContain("4321");
		}
		finally {
			TenantContext.clear();
		}

		TenantContext.set(OTRO_TENANT);
		try {
			assertThat(profiles.latest(OTRO_TENANT, applicantId)).isEmpty();
			assertThat(jdbc.queryForObject("select count(*) from customer.financial_profiles where id = ?", Long.class,
					saved.id())).isZero();
			UUID ajeno = applicantId;
			assertThatThrownBy(() -> profiles.consolidate(OTRO_TENANT, ajeno, Map.of()))
				.isInstanceOf(ApplicantNotFoundException.class);
		}
		finally {
			TenantContext.clear();
		}
	}

	private static Money bob(String amount) {
		return new Money(new BigDecimal(amount), Currency.getInstance("BOB"));
	}

}
