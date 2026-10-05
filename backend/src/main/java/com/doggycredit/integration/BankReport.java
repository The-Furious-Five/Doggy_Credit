package com.doggycredit.integration;

import java.time.LocalDate;
import java.util.List;

import com.doggycredit.integration.BureauReport.Linaje;
import com.doggycredit.shared.Money;

/**
 * Fragmento del Perfil Financiero 360 v1 que aporta el core bancario (B-03).
 * Nombres de campo del esquema canónico; nada del vocabulario del banco pasa
 * de la capa anticorrupción. No lleva el documento.
 *
 * @param cuentas            ítems de {@code cuentas[]} (fuente BANCO)
 * @param ingresosMensuales  {@code flujos.ingresosMensuales}, del mes más antiguo al más reciente
 * @param egresosMensuales   {@code flujos.egresosMensuales}, mismo orden
 * @param fechaCorte         fecha de corte informada por el banco
 * @param linaje             ítem de {@code linaje[]} (ADR-016: solo el hash del crudo)
 */
public record BankReport(List<Cuenta> cuentas, List<Money> ingresosMensuales, List<Money> egresosMensuales,
		LocalDate fechaCorte, Linaje linaje) {

	public BankReport {
		cuentas = List.copyOf(cuentas);
		ingresosMensuales = List.copyOf(ingresosMensuales);
		egresosMensuales = List.copyOf(egresosMensuales);
	}

	/** {@code tipo}: CAJA_AHORRO o CUENTA_CORRIENTE; antigüedad en meses completos a la fecha de corte. */
	public record Cuenta(String tipo, Money saldoPromedio3m, int antiguedadMeses) {
	}

}
