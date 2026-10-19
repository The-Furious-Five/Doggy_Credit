package com.doggycredit.customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.doggycredit.consent.Source;
import com.doggycredit.shared.Money;

/**
 * Perfil Financiero 360 (B-04): exactamente lo que muestra {@code /app/clientes/:id}
 * en las pestañas Resumen y Fuentes. El snapshot canónico v1 completo se guarda
 * cifrado junto a este resumen; {@link #hash} es el SHA-256 de ese canónico.
 * Un campo {@code null} significa "no se sabe" (la fuente que lo aporta no respondió
 * o no se consultó), nunca cero.
 *
 * @param monthlyIncome            ingreso mensual promedio de los últimos meses (Resumen)
 * @param incomeOrigin             de dónde sale el ingreso
 * @param currentDebt              deuda vigente según el buró
 * @param obligations              cantidad de obligaciones en el buró
 * @param monthlyInstallment       suma de cuotas mensuales según el buró
 * @param debtToIncomePercent      endeudamiento: cuota sobre ingreso, en %
 * @param paymentCapacity          capacidad de pago mensual: ingreso − egresos del banco o, sin banco,
 *                                 ingreso − cuotas del buró; nunca negativa
 * @param financialSeniorityMonths antigüedad en el sistema financiero (buró)
 * @param sources                  una fila por fuente, siempre BURO, BANCO y WALLET (pestaña Fuentes)
 * @param declaredMonthlyIncome    ingreso mensual declarado por el solicitante al alta (B-11)
 * @param incomeDiscrepancy        B-11 (RF-PER-06): {@code true} si el declarado difiere en más de
 *                                 30 % del observado (banco o billetera); {@code null} si no hay
 *                                 observado con que compararlo ({@code incomeOrigin} es DECLARADO)
 */
public record FinancialProfile360(UUID id, UUID applicantId, Instant createdAt, String hash, Money monthlyIncome,
		IncomeOrigin incomeOrigin, Money currentDebt, Integer obligations, Money monthlyInstallment,
		BigDecimal debtToIncomePercent, Money paymentCapacity, Integer financialSeniorityMonths,
		List<SourceLineage> sources, Money declaredMonthlyIncome, Boolean incomeDiscrepancy) {

	public FinancialProfile360 {
		sources = List.copyOf(sources);
	}

	/** {@code flujos.origen} del canónico. */
	public enum IncomeOrigin {
		BANCO, WALLET, DECLARADO
	}

	/** Estado de la fuente en este snapshot, con su linaje si respondió (hash del crudo y fecha). */
	public record SourceLineage(Source source, SourceState state, Instant obtainedAt, String sha256) {
	}

	public enum SourceState {
		/** Respondió con datos. */
		COMPLETA,
		/** Respondió que no tiene registro del titular (resultado válido). */
		SIN_REGISTRO,
		/** Caída, error o timeout: la evaluación queda PARCIAL. */
		NO_DISPONIBLE,
		/** No se consultó: sin consentimiento o integración inactiva. */
		NO_CONSULTADA
	}

}
