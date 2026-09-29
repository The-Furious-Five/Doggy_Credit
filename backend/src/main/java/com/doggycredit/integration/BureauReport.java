package com.doggycredit.integration;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.doggycredit.consent.Source;
import com.doggycredit.shared.Money;

/**
 * Fragmento del Perfil Financiero 360 v1 (contracts/canonical/perfil-financiero.v1.schema.json)
 * que aporta el buró. Los nombres de campo son los del esquema canónico; ningún
 * código ni nombre del proveedor pasa de la capa anticorrupción. No lleva el
 * documento en claro: {@code titular.ref} (HMAC por tenant) lo arma el orquestador.
 *
 * @param tipo                             {@code titular.tipo}
 * @param antiguedadSistemaFinancieroMeses {@code titular.antiguedadSistemaFinancieroMeses}
 * @param endeudamiento                    ítems de {@code endeudamiento[]}; vacío si el titular no tiene historial
 * @param linaje                           ítem de {@code linaje[]} (ADR-016: solo el hash del crudo)
 */
public record BureauReport(TipoTitular tipo, int antiguedadSistemaFinancieroMeses,
		List<Endeudamiento> endeudamiento, Linaje linaje) {

	public enum TipoTitular {
		PERSONA, PYME
	}

	/** {@code calificacion}: escala regulatoria A–F (ASFI). */
	public record Endeudamiento(Source fuente, String tipoCredito, Money saldo, Money cuota, int diasMoraMax12m,
			String calificacion, LocalDate fechaCorte) {
	}

	public record Linaje(Source fuente, String conectorVersion, String mapeoVersion, String fetchId,
			String sha256Crudo, Instant obtenidoEn) {
	}

}
