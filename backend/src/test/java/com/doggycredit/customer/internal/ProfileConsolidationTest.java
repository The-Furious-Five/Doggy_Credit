package com.doggycredit.customer.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.doggycredit.consent.Source;
import com.doggycredit.customer.FinancialProfile360;
import com.doggycredit.customer.FinancialProfile360.IncomeOrigin;
import com.doggycredit.customer.FinancialProfile360.SourceState;
import com.doggycredit.customer.SourceResult;
import com.doggycredit.integration.BankReport;
import com.doggycredit.integration.BureauReport;
import com.doggycredit.integration.BureauReport.Endeudamiento;
import com.doggycredit.integration.BureauReport.Linaje;
import com.doggycredit.integration.BureauReport.TipoTitular;
import com.doggycredit.integration.WalletReport;
import com.doggycredit.shared.Money;

/** B-04 sin base de datos: qué muestra el Perfil 360 según qué fuentes respondieron. */
class ProfileConsolidationTest {

	private static final Instant T = Instant.parse("2026-10-05T12:00:00Z");

	private static final Money DECLARED = bob("5000.00");

	private static final BureauReport BUREAU = new BureauReport(TipoTitular.PERSONA, 92,
			List.of(new Endeudamiento(Source.BURO, "CONSOLIDADO", bob("8580.00"), bob("1930.00"), 0, "A",
					LocalDate.of(2026, 9, 1))),
			linaje(Source.BURO));

	private static final BankReport BANK = new BankReport(
			List.of(new BankReport.Cuenta("CAJA_AHORRO", bob("8700.00"), 48)),
			List.of(bob("9000.00"), bob("10000.00")), List.of(bob("6000.00"), bob("7000.00")),
			LocalDate.of(2026, 9, 1), linaje(Source.BANCO));

	private static final WalletReport WALLET = new WalletReport(12, bob("300.00"),
			List.of(new WalletReport.Mes(YearMonth.of(2026, 7), 10, bob("800.00"), bob("500.00")),
					new WalletReport.Mes(YearMonth.of(2026, 8), 20, bob("1000.00"), bob("700.00"))),
			LocalDate.of(2026, 9, 1), linaje(Source.WALLET));

	@Test
	void conLasTresFuentesElIngresoSaleDelBanco() {
		FinancialProfile360 p = view(Map.of(Source.BURO, ok(BUREAU), Source.BANCO, ok(BANK), Source.WALLET, ok(WALLET)));

		assertThat(p.incomeOrigin()).isEqualTo(IncomeOrigin.BANCO);
		assertThat(p.monthlyIncome().amount()).isEqualByComparingTo("9500.00");
		assertThat(p.currentDebt().amount()).isEqualByComparingTo("8580.00");
		assertThat(p.obligations()).isEqualTo(1);
		assertThat(p.monthlyInstallment().amount()).isEqualByComparingTo("1930.00");
		assertThat(p.debtToIncomePercent()).isEqualByComparingTo("20.3");
		// 9500 de ingreso − 6500 de egresos promedio del banco.
		assertThat(p.paymentCapacity().amount()).isEqualByComparingTo("3000.00");
		assertThat(p.financialSeniorityMonths()).isEqualTo(92);
		assertThat(p.sources()).extracting(FinancialProfile360.SourceLineage::state)
			.containsExactly(SourceState.COMPLETA, SourceState.COMPLETA, SourceState.COMPLETA);
		assertThat(p.sources()).allSatisfy(s -> assertThat(s.sha256()).hasSize(64));
	}

	@Test
	void ingresoObservadoMuyDistintoDelDeclaradoDisparaLaAlerta() {
		// Declarado 5000, observado (banco) 9500: 90 % de diferencia, > 30 % (RF-PER-06).
		FinancialProfile360 p = view(Map.of(Source.BURO, ok(BUREAU), Source.BANCO, ok(BANK)));

		assertThat(p.declaredMonthlyIncome()).isEqualTo(DECLARED);
		assertThat(p.incomeDiscrepancy()).isTrue();
	}

	@Test
	void ingresoObservadoParecidoAlDeclaradoNoDisparaLaAlerta() {
		Money declaradoParecido = bob("9000.00"); // observado 9500: ~5 % de diferencia.
		FinancialProfile360 p = ProfileConsolidation
			.consolidate(UUID.randomUUID(), T, UUID.randomUUID(), "ref", declaradoParecido,
					Map.of(Source.BURO, ok(BUREAU), Source.BANCO, ok(BANK)))
			.view();

		assertThat(p.incomeDiscrepancy()).isFalse();
	}

	@Test
	void sinObservadoConQueCompararNoHayAlerta() {
		FinancialProfile360 p = view(Map.of(Source.BURO, SourceResult.unavailable()));

		assertThat(p.incomeOrigin()).isEqualTo(IncomeOrigin.DECLARADO);
		assertThat(p.incomeDiscrepancy()).isNull();
	}

	@Test
	void conElBancoCaidoElIngresoSaleDeLaBilleteraYLaFuenteQuedaNoDisponible() {
		FinancialProfile360 p = view(Map.of(Source.BURO, ok(BUREAU), Source.BANCO, SourceResult.unavailable(),
				Source.WALLET, ok(WALLET)));

		assertThat(p.incomeOrigin()).isEqualTo(IncomeOrigin.WALLET);
		assertThat(p.monthlyIncome().amount()).isEqualByComparingTo("900.00");
		// Sin egresos del banco, la capacidad descuenta solo las cuotas del buró; nunca negativa.
		assertThat(p.paymentCapacity().amount()).isEqualByComparingTo("0.00");
		assertThat(p.sources().get(1)).satisfies(s -> {
			assertThat(s.source()).isEqualTo(Source.BANCO);
			assertThat(s.state()).isEqualTo(SourceState.NO_DISPONIBLE);
			assertThat(s.obtainedAt()).isNull();
			assertThat(s.sha256()).isNull();
		});
	}

	@Test
	void conElBuroCaidoLaDeudaEsDesconocidaNoCero() {
		FinancialProfile360 p = view(Map.of(Source.BURO, SourceResult.unavailable()));

		assertThat(p.incomeOrigin()).isEqualTo(IncomeOrigin.DECLARADO);
		assertThat(p.monthlyIncome()).isEqualTo(DECLARED);
		assertThat(p.currentDebt()).isNull();
		assertThat(p.obligations()).isNull();
		assertThat(p.debtToIncomePercent()).isNull();
		assertThat(p.paymentCapacity()).isNull();
		assertThat(p.financialSeniorityMonths()).isNull();
		assertThat(p.sources()).extracting(FinancialProfile360.SourceLineage::state)
			.containsExactly(SourceState.NO_DISPONIBLE, SourceState.NO_CONSULTADA, SourceState.NO_CONSULTADA);
	}

	@Test
	void sinRegistroEnElBuroEsDeudaCeroConocida() {
		FinancialProfile360 p = view(Map.of(Source.BURO, SourceResult.of(Optional.empty())));

		assertThat(p.currentDebt().amount()).isEqualByComparingTo("0");
		assertThat(p.obligations()).isZero();
		assertThat(p.financialSeniorityMonths()).isZero();
		assertThat(p.sources().getFirst().state()).isEqualTo(SourceState.SIN_REGISTRO);
	}

	@Test
	void elHashDependeDelContenidoNoDelIdNiLaFecha() {
		Map<Source, SourceResult> results = Map.of(Source.BURO, ok(BUREAU), Source.BANCO, ok(BANK));
		var a = ProfileConsolidation.consolidate(UUID.randomUUID(), T, UUID.randomUUID(), "ref", DECLARED, results);
		var b = ProfileConsolidation.consolidate(UUID.randomUUID(), T.plusSeconds(60), UUID.randomUUID(), "ref",
				DECLARED, results);
		var c = ProfileConsolidation.consolidate(UUID.randomUUID(), T, UUID.randomUUID(), "ref", DECLARED,
				Map.of(Source.BURO, ok(BUREAU)));

		assertThat(a.view().hash()).hasSize(64).isEqualTo(b.view().hash()).isNotEqualTo(c.view().hash());
		assertThat(a.canonical()).containsKeys("id", "hash", "versionEsquema", "creadoEn", "titular", "linaje");
		assertThat(a.canonical().get("titular")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
			.containsEntry("ref", "ref").containsEntry("tipo", "PERSONA");
	}

	private static FinancialProfile360 view(Map<Source, SourceResult> results) {
		return ProfileConsolidation.consolidate(UUID.randomUUID(), T, UUID.randomUUID(), "ref", DECLARED, results).view();
	}

	private static SourceResult ok(Object report) {
		return SourceResult.of(Optional.of(report));
	}

	private static Linaje linaje(Source source) {
		return new Linaje(source, "c/1", "m/1", "fetch-" + source, "a".repeat(64), T);
	}

	private static Money bob(String amount) {
		return new Money(new BigDecimal(amount), Currency.getInstance("BOB"));
	}

}
