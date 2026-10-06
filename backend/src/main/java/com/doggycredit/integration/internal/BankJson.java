package com.doggycredit.integration.internal;

import static com.doggycredit.integration.internal.JsonFields.array;
import static com.doggycredit.integration.internal.JsonFields.currency;
import static com.doggycredit.integration.internal.JsonFields.date;
import static com.doggycredit.integration.internal.JsonFields.money;
import static com.doggycredit.integration.internal.JsonFields.month;
import static com.doggycredit.integration.internal.JsonFields.parse;
import static com.doggycredit.integration.internal.JsonFields.text;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.doggycredit.consent.Source;
import com.doggycredit.integration.BankReport;
import com.doggycredit.integration.BankReport.Cuenta;
import com.doggycredit.integration.BureauReport.Linaje;
import com.doggycredit.integration.SourceUnavailableException;
import com.doggycredit.shared.Money;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * Capa anticorrupción del core bancario: JSON del proveedor
 * (contracts/connectors/bank.v1.yaml) → fragmento canónico v1. Todo o nada.
 */
public final class BankJson {

	static final String CONNECTOR_VERSION = "bank-connector/1";

	static final String MAPPING_VERSION = "bank.v1->perfil-financiero.v1";

	private static final Set<String> PRODUCTS = Set.of("CAJA_AHORRO", "CUENTA_CORRIENTE");

	private BankJson() {
	}

	/** ¿Es el 404 propio del banco para este documento? */
	public static boolean isNotFound(byte[] body, String documento) {
		try {
			JsonNode root = parse(body);
			return text(root, "codigo").equals("CLIENTE_NO_ENCONTRADO")
					&& text(root, "numeroDocumento").equals(documento);
		}
		catch (RuntimeException e) {
			return false;
		}
	}

	/** @param documento documento normalizado que se pidió; el resumen debe ser de ese cliente. */
	public static BankReport toReport(byte[] body, String documento, Instant obtainedAt) {
		try {
			JsonNode root = parse(body);
			if (!text(root, "numeroDocumento").equals(documento)) {
				throw new IllegalArgumentException("el resumen es de otro documento");
			}
			LocalDate fechaCorte = date(root, "fechaCorte");

			List<Cuenta> cuentas = new ArrayList<>();
			for (JsonNode c : array(root, "cuentas", 20)) {
				String producto = text(c, "producto");
				if (!PRODUCTS.contains(producto)) {
					throw new IllegalArgumentException("producto desconocido: " + producto);
				}
				LocalDate apertura = date(c, "fechaApertura");
				if (apertura.isAfter(fechaCorte)) {
					throw new IllegalArgumentException("cuenta abierta después de la fecha de corte");
				}
				cuentas.add(new Cuenta(producto, money(c, "saldoPromedio90d", currency(c, "moneda")),
						(int) ChronoUnit.MONTHS.between(apertura, fechaCorte)));
			}
			if (cuentas.isEmpty()) {
				throw new IllegalArgumentException("cliente sin cuentas");
			}

			// Los flujos van en la moneda de la cuenta principal (la primera).
			String moneda = cuentas.getFirst().saldoPromedio3m().currency().getCurrencyCode();
			List<Money> ingresos = new ArrayList<>();
			List<Money> egresos = new ArrayList<>();
			YearMonth anterior = null;
			for (JsonNode m : array(root, "movimientosMensuales", 12)) {
				YearMonth periodo = month(m, "periodo");
				if (anterior != null && !periodo.isAfter(anterior) || !periodo.isBefore(YearMonth.from(fechaCorte))) {
					throw new IllegalArgumentException("periodos desordenados o posteriores al corte");
				}
				anterior = periodo;
				ingresos.add(money(m, "totalAbonos", moneda));
				egresos.add(money(m, "totalCargos", moneda));
			}
			Linaje linaje = new Linaje(Source.BANCO, CONNECTOR_VERSION, MAPPING_VERSION, UUID.randomUUID().toString(),
					ConnectorHttp.sha256(body), obtainedAt);
			return new BankReport(cuentas, ingresos, egresos, fechaCorte, linaje);
		}
		catch (IllegalArgumentException | JacksonException | java.time.DateTimeException e) {
			throw new SourceUnavailableException(Source.BANCO, "resumen mal formado: " + e.getMessage(), e);
		}
	}

}
