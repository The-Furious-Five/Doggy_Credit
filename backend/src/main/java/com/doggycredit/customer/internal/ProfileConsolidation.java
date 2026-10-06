package com.doggycredit.customer.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.doggycredit.consent.Source;
import com.doggycredit.customer.FinancialProfile360;
import com.doggycredit.customer.FinancialProfile360.IncomeOrigin;
import com.doggycredit.customer.FinancialProfile360.SourceLineage;
import com.doggycredit.customer.FinancialProfile360.SourceState;
import com.doggycredit.customer.SourceResult;
import com.doggycredit.integration.BankReport;
import com.doggycredit.integration.BureauReport;
import com.doggycredit.integration.BureauReport.Linaje;
import com.doggycredit.integration.WalletReport;
import com.doggycredit.shared.Money;

import tools.jackson.databind.json.JsonMapper;

/**
 * Consolidación pura (sin E/S) de las fuentes en el Perfil Financiero 360 v1
 * (contracts/canonical/perfil-financiero.v1.schema.json) y en el resumen que
 * muestra la vista. Los montos se expresan en la moneda declarada del solicitante.
 */
final class ProfileConsolidation {

	static final JsonMapper JSON = JsonMapper.builder().build();

	private static final List<Source> SOURCES = List.of(Source.BURO, Source.BANCO, Source.WALLET);

	private ProfileConsolidation() {
	}

	record Snapshot(Map<String, Object> canonical, FinancialProfile360 view) {
	}

	static Snapshot consolidate(UUID id, Instant now, UUID applicantId, String documentRef, Money declaredIncome,
			Map<Source, SourceResult> results) {
		Currency currency = declaredIncome.currency();
		Optional<BureauReport> bureau = report(results, Source.BURO, BureauReport.class);
		Optional<BankReport> bank = report(results, Source.BANCO, BankReport.class);
		Optional<WalletReport> wallet = report(results, Source.WALLET, WalletReport.class);
		boolean debtKnown = known(results, Source.BURO);

		// Ingreso: lo observado le gana a lo declarado; el banco, a la billetera.
		IncomeOrigin origin = IncomeOrigin.DECLARADO;
		Money income = declaredIncome;
		List<Money> bankIncome = bank.map(b -> inCurrency(b.ingresosMensuales(), currency)).orElse(List.of());
		List<Money> walletIncome = wallet.map(w -> inCurrency(w.meses().stream().map(WalletReport.Mes::recibido).toList(),
				currency)).orElse(List.of());
		if (!bankIncome.isEmpty()) {
			origin = IncomeOrigin.BANCO;
			income = average(bankIncome, currency);
		}
		else if (!walletIncome.isEmpty()) {
			origin = IncomeOrigin.WALLET;
			income = average(walletIncome, currency);
		}

		List<BureauReport.Endeudamiento> debts = bureau.map(BureauReport::endeudamiento).orElse(List.of());
		Money debt = debtKnown ? sum(debts, BureauReport.Endeudamiento::saldo, currency) : null;
		Money installment = debtKnown ? sum(debts, BureauReport.Endeudamiento::cuota, currency) : null;
		Integer obligations = debtKnown ? debts.size() : null;
		Integer seniority = !debtKnown ? null : bureau.map(BureauReport::antiguedadSistemaFinancieroMeses).orElse(0);

		BigDecimal debtToIncome = installment == null || income.amount().signum() == 0 ? null
				: installment.amount().multiply(BigDecimal.valueOf(100))
					.divide(income.amount(), 1, RoundingMode.HALF_UP);
		List<Money> bankOutflow = bank.map(b -> inCurrency(b.egresosMensuales(), currency)).orElse(List.of());
		Money capacity = !bankOutflow.isEmpty() ? nonNegative(income, average(bankOutflow, currency))
				: installment != null ? nonNegative(income, installment) : null;

		List<SourceLineage> lineage = new ArrayList<>();
		List<Linaje> linajes = new ArrayList<>();
		for (Source source : SOURCES) {
			SourceState state = results.getOrDefault(source, SourceResult.notConsulted()).state();
			Optional<Linaje> linaje = Optional.ofNullable(results.get(source)).map(SourceResult::report).map(r -> switch (r) {
				case BureauReport b -> b.linaje();
				case BankReport b -> b.linaje();
				case WalletReport w -> w.linaje();
				default -> throw new IllegalArgumentException("informe desconocido para " + source + ": " + r.getClass());
			});
			linaje.ifPresent(linajes::add);
			lineage.add(new SourceLineage(source, state, linaje.map(Linaje::obtenidoEn).orElse(null),
					linaje.map(Linaje::sha256Crudo).orElse(null)));
		}

		Map<String, Object> canonical = canonical(documentRef, bureau, bank, wallet, origin, bankIncome, walletIncome,
				bankOutflow, lineage, linajes);
		String hash = sha256(canonical);
		canonical.put("id", id);
		canonical.put("hash", hash);
		canonical.put("creadoEn", now);
		FinancialProfile360 view = new FinancialProfile360(id, applicantId, now, hash, income, origin, debt,
				obligations, installment, debtToIncome, capacity, seniority, lineage);
		return new Snapshot(canonical, view);
	}

	private static Map<String, Object> canonical(String documentRef, Optional<BureauReport> bureau,
			Optional<BankReport> bank, Optional<WalletReport> wallet, IncomeOrigin origin, List<Money> bankIncome,
			List<Money> walletIncome, List<Money> bankOutflow, List<SourceLineage> lineage, List<Linaje> linajes) {
		Map<String, Object> titular = new LinkedHashMap<>();
		titular.put("ref", documentRef);
		// ponytail: sin buró no se conoce el tipo de titular; PERSONA por defecto hasta que el alta lo declare.
		titular.put("tipo", bureau.map(b -> b.tipo().name()).orElse("PERSONA"));
		bureau.ifPresent(b -> titular.put("antiguedadSistemaFinancieroMeses", b.antiguedadSistemaFinancieroMeses()));

		Map<String, Object> p = new LinkedHashMap<>();
		p.put("versionEsquema", 1);
		p.put("titular", titular);
		p.put("endeudamiento", bureau.map(BureauReport::endeudamiento).orElse(List.of()));
		p.put("cuentas", bank.map(b -> b.cuentas().stream().map(c -> {
			Map<String, Object> cuenta = new LinkedHashMap<>();
			cuenta.put("fuente", Source.BANCO);
			cuenta.put("tipo", c.tipo());
			cuenta.put("moneda", c.saldoPromedio3m().currency().getCurrencyCode());
			cuenta.put("saldoPromedio3m", c.saldoPromedio3m());
			cuenta.put("antiguedadMeses", c.antiguedadMeses());
			return cuenta;
		}).toList()).orElse(List.of()));
		if (origin != IncomeOrigin.DECLARADO) {
			Map<String, Object> flujos = new LinkedHashMap<>();
			flujos.put("ingresosMensuales", origin == IncomeOrigin.BANCO ? bankIncome : walletIncome);
			if (origin == IncomeOrigin.BANCO) {
				flujos.put("egresosMensuales", bankOutflow);
			}
			flujos.put("origen", origin);
			p.put("flujos", flujos);
		}
		wallet.ifPresent(w -> {
			Map<String, Object> billetera = new LinkedHashMap<>();
			billetera.put("transaccionesMensuales", w.transaccionesMensuales());
			billetera.put("saldoPromedio", w.saldoPromedio());
			billetera.put("antiguedadMeses", w.antiguedadMeses());
			p.put("wallet", billetera);
		});
		Map<String, Object> frescura = new LinkedHashMap<>();
		linajes.forEach(l -> frescura.put(l.fuente().name(), l.obtenidoEn()));
		long answered = lineage.stream()
			.filter(l -> l.state() == SourceState.COMPLETA || l.state() == SourceState.SIN_REGISTRO)
			.count();
		// LinkedHashMap y no Map.of: el orden de Map.of cambia entre JVM y el hash debe ser estable.
		Map<String, Object> calidad = new LinkedHashMap<>();
		calidad.put("completitudPorcentaje", answered * 100 / SOURCES.size());
		calidad.put("frescuraPorFuente", frescura);
		calidad.put("conflictosDetectados", List.of());
		p.put("calidad", calidad);
		p.put("linaje", linajes);
		return p;
	}

	/** La fuente respondió (con o sin registro): lo que aporta es conocido. */
	private static boolean known(Map<Source, SourceResult> results, Source source) {
		SourceState state = results.getOrDefault(source, SourceResult.notConsulted()).state();
		return state == SourceState.COMPLETA || state == SourceState.SIN_REGISTRO;
	}

	private static <T> Optional<T> report(Map<Source, SourceResult> results, Source source, Class<T> type) {
		return Optional.ofNullable(results.get(source)).map(SourceResult::report).filter(type::isInstance).map(type::cast);
	}

	// ponytail: sin conversión de moneda; un monto en otra moneda que la declarada se ignora.
	private static List<Money> inCurrency(List<Money> amounts, Currency currency) {
		return amounts.stream().filter(m -> m.currency().equals(currency)).toList();
	}

	private static Money average(List<Money> amounts, Currency currency) {
		BigDecimal total = amounts.stream().map(Money::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
		return new Money(total.divide(BigDecimal.valueOf(amounts.size()), 2, RoundingMode.HALF_UP), currency);
	}

	private static <T> Money sum(List<T> items, Function<T, Money> amount, Currency currency) {
		return new Money(inCurrency(items.stream().map(amount).toList(), currency).stream()
			.map(Money::amount)
			.reduce(new BigDecimal("0.00"), BigDecimal::add), currency);
	}

	private static Money nonNegative(Money income, Money outflow) {
		return new Money(income.amount().subtract(outflow.amount()).max(new BigDecimal("0.00")), income.currency());
	}

	private static String sha256(Map<String, Object> canonical) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(JSON.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
