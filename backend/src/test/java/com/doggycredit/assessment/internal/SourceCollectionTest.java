package com.doggycredit.assessment.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.doggycredit.assessment.internal.SourceCollection.SourceOutcome;
import com.doggycredit.consent.Source;
import com.doggycredit.customer.FinancialProfile360.SourceState;
import com.doggycredit.customer.SourceResult;

/** B-04: lo que recolectó la evaluación se traduce sin inventar datos al estado de cada fuente del Perfil 360. */
class SourceCollectionTest {

	@Test
	void traduceCadaResultadoAlEstadoDelPerfil() {
		Object report = new Object();
		var collection = new SourceCollection(Map.of(
				Source.BURO, SourceOutcome.received(Optional.of(report)),
				Source.BANCO, SourceOutcome.received(Optional.empty()),
				Source.WALLET, SourceOutcome.timedOut()));

		Map<Source, SourceResult> results = collection.toSourceResults();

		assertThat(results.get(Source.BURO)).isEqualTo(new SourceResult(SourceState.COMPLETA, report));
		assertThat(results.get(Source.BANCO)).isEqualTo(new SourceResult(SourceState.SIN_REGISTRO, null));
		assertThat(results.get(Source.WALLET)).isEqualTo(SourceResult.unavailable());
	}

	@Test
	void unaFuenteCaidaOSinConsentimientoNoSeConsideraSinRegistro() {
		var collection = new SourceCollection(Map.of(
				Source.BURO, SourceOutcome.unavailable(),
				Source.WALLET, SourceOutcome.notConsented()));

		Map<Source, SourceResult> results = collection.toSourceResults();

		assertThat(results.get(Source.BURO).state()).isEqualTo(SourceState.NO_DISPONIBLE);
		assertThat(results.get(Source.WALLET).state()).isEqualTo(SourceState.NO_CONSULTADA);
		// Desactivada para el tenant (B-06): no se recolectó y el perfil la cuenta como no consultada.
		assertThat(results).doesNotContainKey(Source.BANCO);
	}

}
