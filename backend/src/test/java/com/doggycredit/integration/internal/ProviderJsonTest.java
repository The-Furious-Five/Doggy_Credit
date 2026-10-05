package com.doggycredit.integration.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.doggycredit.consent.Source;
import com.doggycredit.integration.BankReport;
import com.doggycredit.integration.SourceUnavailableException;
import com.doggycredit.integration.WalletReport;

/** Capas anticorrupción de banco y billetera sin red: todo o nada ante JSON fuera de contrato. */
class ProviderJsonTest {

	private static final String DOC = "CI-DEMO-0001001";

	private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

	private static final String BANK = """
			{"numeroDocumento":"%s","fechaCorte":"2026-09-01",
			 "cuentas":[{"producto":"%s","moneda":"BOB","fechaApertura":"2022-09-01","saldoPromedio90d":"%s"}],
			 "movimientosMensuales":[{"periodo":"2026-07","totalAbonos":"100.00","totalCargos":"80.00"},
			                         {"periodo":"%s","totalAbonos":"120.00","totalCargos":"90.00"}]}
			""";

	private static final String WALLET = """
			{"titular":{"documento":"%s","fechaAlta":"2025-09-01"},"moneda":"BOB","fechaCorte":"2026-09-01",
			 "saldoPromedio":"50.00",
			 "resumenMensual":[{"mes":"2026-08","cantidadTransacciones":%s,"montoRecibido":"10.00","montoEnviado":"5.00"}]}
			""";

	private static BankReport bank(String doc, String producto, String saldo, String periodo) {
		return BankJson.toReport(BANK.formatted(doc, producto, saldo, periodo).getBytes(StandardCharsets.UTF_8), DOC, NOW);
	}

	private static WalletReport wallet(String doc, String transacciones) {
		return WalletJson.toReport(WALLET.formatted(doc, transacciones).getBytes(StandardCharsets.UTF_8), DOC, NOW);
	}

	@Test
	void bancoValidoSeMapea() {
		BankReport r = bank(DOC, "CAJA_AHORRO", "8700.00", "2026-08");
		assertThat(r.cuentas()).singleElement().satisfies(c -> assertThat(c.antiguedadMeses()).isEqualTo(48));
		assertThat(r.ingresosMensuales()).extracting(m -> m.amount().toPlainString()).containsExactly("100.00", "120.00");
		assertThat(r.linaje().conectorVersion()).isEqualTo(BankJson.CONNECTOR_VERSION);
	}

	@Test
	void bancoFueraDeContratoEsFuenteCaida() {
		assertBankUnavailable(() -> bank("CI-DEMO-0001002", "CAJA_AHORRO", "8700.00", "2026-08"));
		assertBankUnavailable(() -> bank(DOC, "TARJETA", "8700.00", "2026-08"));
		assertBankUnavailable(() -> bank(DOC, "CAJA_AHORRO", "8700.5", "2026-08"));
		assertBankUnavailable(() -> bank(DOC, "CAJA_AHORRO", "-1.00", "2026-08"));
		// Periodos desordenados o del mes de corte en adelante.
		assertBankUnavailable(() -> bank(DOC, "CAJA_AHORRO", "8700.00", "2026-06"));
		assertBankUnavailable(() -> bank(DOC, "CAJA_AHORRO", "8700.00", "2026-09"));
		assertBankUnavailable(() -> BankJson.toReport("no es json".getBytes(StandardCharsets.UTF_8), DOC, NOW));
	}

	@Test
	void billeteraValidaYFueraDeContrato() {
		assertThat(wallet(DOC, "7").transaccionesMensuales()).isEqualTo(7);
		assertWalletUnavailable(() -> wallet("CI-DEMO-0001002", "7"));
		assertWalletUnavailable(() -> wallet(DOC, "-1"));
		assertWalletUnavailable(() -> wallet(DOC, "\"7\""));
	}

	@Test
	void soloEl404PropioEsSinRegistro() {
		assertThat(BankJson.isNotFound(bytes("{\"codigo\":\"CLIENTE_NO_ENCONTRADO\",\"numeroDocumento\":\"" + DOC + "\"}"), DOC)).isTrue();
		assertThat(BankJson.isNotFound(bytes("{\"codigo\":\"CLIENTE_NO_ENCONTRADO\",\"numeroDocumento\":\"OTRO\"}"), DOC)).isFalse();
		assertThat(BankJson.isNotFound(bytes("<html>Not Found</html>"), DOC)).isFalse();
		assertThat(WalletJson.isNotFound(bytes("{\"error\":\"TITULAR_NO_REGISTRADO\",\"documento\":\"" + DOC + "\"}"), DOC)).isTrue();
		assertThat(WalletJson.isNotFound(bytes(""), DOC)).isFalse();
	}

	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}

	private static void assertBankUnavailable(Runnable call) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(SourceUnavailableException.class,
				e -> assertThat(e.source()).isEqualTo(Source.BANCO));
	}

	private static void assertWalletUnavailable(Runnable call) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(SourceUnavailableException.class,
				e -> assertThat(e.source()).isEqualTo(Source.WALLET));
	}

}
