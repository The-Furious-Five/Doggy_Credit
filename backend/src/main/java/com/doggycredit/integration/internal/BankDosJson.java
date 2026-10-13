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
import java.util.Map;
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
 * Capa anticorrupción del segundo core bancario: JSON del proveedor
 * (contracts/connectors/bank-dos.v1.yaml) → el mismo fragmento canónico v1
 * que {@link BankJson} produce para el primer banco. Todo o nada.
 */
public final class BankDosJson {

	static final String CONNECTOR_VERSION = "bank-dos-connector/1";

	static final String MAPPING_VERSION = "bank-dos.v1->perfil-financiero.v1";

	private static final Map<String, String> PRODUCTOS = Map.of("AHORRO", "CAJA_AHORRO", "CORRIENTE", "CUENTA_CORRIENTE");

	private BankDosJson() {
	}

	/** ¿Es el 404 propio de este banco para este documento? */
	public static boolean isNotFound(byte[] body, String documento) {
		try {
			JsonNode root = parse(body);
			return text(root, "codigo").equals("CLIENTE_INEXISTENTE") && text(root, "documentoCliente").equals(documento);
		}
		catch (RuntimeException e) {
			return false;
		}
	}

	/** @param documento documento normalizado que se pidió; la cuenta debe ser de ese cliente. */
	public static BankReport toReport(byte[] body, String documento, Instant obtainedAt) {
		try {
			JsonNode root = parse(body);
			if (!text(root, "documentoCliente").equals(documento)) {
				throw new IllegalArgumentException("la cuenta es de otro documento");
			}
			LocalDate corte = date(root, "corte");

			JsonNode cuentaNode = root.get("cuenta");
			if (cuentaNode == null || !cuentaNode.isObject()) {
				throw new IllegalArgumentException("falta 'cuenta'");
			}
			String producto = PRODUCTOS.get(text(cuentaNode, "tipoProducto"));
			if (producto == null) {
				throw new IllegalArgumentException("tipoProducto desconocido: " + text(cuentaNode, "tipoProducto"));
			}
			LocalDate fechaDesde = date(cuentaNode, "fechaDesde");
			if (fechaDesde.isAfter(corte)) {
				throw new IllegalArgumentException("cuenta abierta después del corte");
			}
			Cuenta cuenta = new Cuenta(producto, money(cuentaNode, "saldoPromedio3Meses", currency(cuentaNode, "moneda")),
					(int) ChronoUnit.MONTHS.between(fechaDesde, corte));

			String moneda = cuenta.saldoPromedio3m().currency().getCurrencyCode();
			List<Money> ingresos = new ArrayList<>();
			List<Money> egresos = new ArrayList<>();
			YearMonth anterior = null;
			for (JsonNode m : array(root, "movimientos", 12)) {
				YearMonth periodo = month(m, "mes");
				if (anterior != null && !periodo.isAfter(anterior) || !periodo.isBefore(YearMonth.from(corte))) {
					throw new IllegalArgumentException("periodos desordenados o posteriores al corte");
				}
				anterior = periodo;
				ingresos.add(money(m, "creditos", moneda));
				egresos.add(money(m, "debitos", moneda));
			}
			Linaje linaje = new Linaje(Source.BANCO, CONNECTOR_VERSION, MAPPING_VERSION, UUID.randomUUID().toString(),
					ConnectorHttp.sha256(body), obtainedAt);
			return new BankReport(List.of(cuenta), ingresos, egresos, corte, linaje);
		}
		catch (IllegalArgumentException | JacksonException | java.time.DateTimeException e) {
			throw new SourceUnavailableException(Source.BANCO, "cuenta mal formada: " + e.getMessage(), e);
		}
	}

}
