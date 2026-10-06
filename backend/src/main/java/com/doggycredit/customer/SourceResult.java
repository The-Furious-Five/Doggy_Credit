package com.doggycredit.customer;

import java.util.Optional;

import com.doggycredit.customer.FinancialProfile360.SourceState;

/**
 * Lo que obtuvo el orquestador (C-01) de una fuente, para consolidar el Perfil 360.
 * {@code report} es el fragmento canónico del conector ({@code BureauReport},
 * {@code BankReport} o {@code WalletReport}) y solo existe en {@link SourceState#COMPLETA}.
 */
public record SourceResult(SourceState state, Object report) {

	/** Resultado de un conector: con informe → COMPLETA; vacío → SIN_REGISTRO. */
	public static SourceResult of(Optional<?> fetched) {
		return fetched.<SourceResult>map(r -> new SourceResult(SourceState.COMPLETA, r))
			.orElseGet(() -> new SourceResult(SourceState.SIN_REGISTRO, null));
	}

	public static SourceResult unavailable() {
		return new SourceResult(SourceState.NO_DISPONIBLE, null);
	}

	public static SourceResult notConsulted() {
		return new SourceResult(SourceState.NO_CONSULTADA, null);
	}

}
