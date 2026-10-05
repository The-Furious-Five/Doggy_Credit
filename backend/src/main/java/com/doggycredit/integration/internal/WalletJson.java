package com.doggycredit.integration.internal;

import static com.doggycredit.integration.internal.JsonFields.array;
import static com.doggycredit.integration.internal.JsonFields.currency;
import static com.doggycredit.integration.internal.JsonFields.date;
import static com.doggycredit.integration.internal.JsonFields.field;
import static com.doggycredit.integration.internal.JsonFields.money;
import static com.doggycredit.integration.internal.JsonFields.month;
import static com.doggycredit.integration.internal.JsonFields.nonNegativeInt;
import static com.doggycredit.integration.internal.JsonFields.parse;
import static com.doggycredit.integration.internal.JsonFields.text;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.doggycredit.consent.Source;
import com.doggycredit.integration.BureauReport.Linaje;
import com.doggycredit.integration.SourceUnavailableException;
import com.doggycredit.integration.WalletReport;
import com.doggycredit.integration.WalletReport.Mes;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * Capa anticorrupción de la billetera móvil: JSON del proveedor
 * (contracts/connectors/wallet.v1.yaml) → fragmento canónico v1. Todo o nada.
 */
public final class WalletJson {

	static final String CONNECTOR_VERSION = "wallet-connector/1";

	static final String MAPPING_VERSION = "wallet.v1->perfil-financiero.v1";

	private WalletJson() {
	}

	/** ¿Es el 404 propio de la billetera para este documento? */
	public static boolean isNotFound(byte[] body, String documento) {
		try {
			JsonNode root = parse(body);
			return text(root, "error").equals("TITULAR_NO_REGISTRADO") && text(root, "documento").equals(documento);
		}
		catch (RuntimeException e) {
			return false;
		}
	}

	/** @param documento documento normalizado que se pidió; los movimientos deben ser de ese titular. */
	public static WalletReport toReport(byte[] body, String documento, Instant obtainedAt) {
		try {
			JsonNode root = parse(body);
			JsonNode titular = field(root, "titular");
			if (!text(titular, "documento").equals(documento)) {
				throw new IllegalArgumentException("los movimientos son de otro documento");
			}
			LocalDate fechaCorte = date(root, "fechaCorte");
			LocalDate alta = date(titular, "fechaAlta");
			if (alta.isAfter(fechaCorte)) {
				throw new IllegalArgumentException("alta posterior a la fecha de corte");
			}
			String moneda = currency(root, "moneda");

			List<Mes> meses = new ArrayList<>();
			YearMonth anterior = null;
			for (JsonNode m : array(root, "resumenMensual", 12)) {
				YearMonth periodo = month(m, "mes");
				if (anterior != null && !periodo.isAfter(anterior) || !periodo.isBefore(YearMonth.from(fechaCorte))) {
					throw new IllegalArgumentException("meses desordenados o posteriores al corte");
				}
				anterior = periodo;
				meses.add(new Mes(periodo, nonNegativeInt(m, "cantidadTransacciones"),
						money(m, "montoRecibido", moneda), money(m, "montoEnviado", moneda)));
			}
			Linaje linaje = new Linaje(Source.WALLET, CONNECTOR_VERSION, MAPPING_VERSION,
					UUID.randomUUID().toString(), ConnectorHttp.sha256(body), obtainedAt);
			return new WalletReport((int) ChronoUnit.MONTHS.between(alta, fechaCorte),
					money(root, "saldoPromedio", moneda), meses, fechaCorte, linaje);
		}
		catch (IllegalArgumentException | JacksonException | DateTimeException e) {
			throw new SourceUnavailableException(Source.WALLET, "movimientos mal formados: " + e.getMessage(), e);
		}
	}

}
