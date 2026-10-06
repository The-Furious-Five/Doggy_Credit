package com.doggycredit.assessment.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.doggycredit.assessment.internal.SourceCollection.SourceOutcome;
import com.doggycredit.assessment.internal.SourceCollection.SourceOutcome.Status;
import com.doggycredit.consent.Source;
import com.doggycredit.customer.FinancialProfile360.SourceState;
import com.doggycredit.customer.SourceResult;
import com.doggycredit.integration.BankReport;
import com.doggycredit.integration.BureauReport;
import com.doggycredit.integration.BureauReport.Linaje;
import com.doggycredit.integration.WalletReport;
import com.doggycredit.shared.Money;

class SourceCollectionProfileTest {

	@ParameterizedTest
	@EnumSource(value = Source.class, names = { "BURO", "BANCO", "WALLET" })
	void desempaquetaElInformeCanonicoDeCadaConectorConSuLinaje(Source source) {
		Object report = report(source);
		var results = new SourceCollection(Map.of(source, SourceOutcome.received(Optional.of(report))))
			.profileResults();

		assertThat(results.get(source).state()).isEqualTo(SourceState.COMPLETA);
		assertThat(results.get(source).report()).isSameAs(report);
	}

	@ParameterizedTest
	@EnumSource(value = Source.class, names = { "BURO", "BANCO", "WALLET" })
	void un404DeDocumentoEsSinRegistroYNoUnaFuenteCaida(Source source) {
		var results = new SourceCollection(Map.of(source, SourceOutcome.received(Optional.empty())))
			.profileResults();
		assertThat(results.get(source)).isEqualTo(new SourceResult(SourceState.SIN_REGISTRO, null));
	}

	@ParameterizedTest
	@EnumSource(value = Status.class, names = { "UNAVAILABLE", "TIMED_OUT" })
	void errorYTimeoutSonDesconocidosSinInventarUnInforme(Status status) {
		var results = new SourceCollection(Map.of(Source.BURO, new SourceOutcome(status, null))).profileResults();
		assertThat(results.get(Source.BURO)).isEqualTo(SourceResult.unavailable());
	}

	@Test
	void sinConsentimientoONoHabilitadaNoCuentaComoConsultada() {
		var results = new SourceCollection(Map.of(Source.BANCO, SourceOutcome.notConsented())).profileResults();
		assertThat(results.get(Source.BANCO)).isEqualTo(SourceResult.notConsulted());
		assertThat(results).doesNotContainKey(Source.WALLET);
	}

	private Object report(Source source) {
		Linaje lineage = new Linaje(source, "v1", "v1", "fetch", "a".repeat(64), Instant.EPOCH);
		return switch (source) {
			case BURO -> new BureauReport(BureauReport.TipoTitular.PERSONA, 48, List.of(), lineage);
			case BANCO -> new BankReport(List.of(), List.of(), List.of(), LocalDate.of(2026, 9, 1), lineage);
			case WALLET -> new WalletReport(24, new Money(BigDecimal.ZERO, Currency.getInstance("BOB")),
					List.of(), LocalDate.of(2026, 9, 1), lineage);
			default -> throw new IllegalArgumentException("Fuente sin conector");
		};
	}
}
