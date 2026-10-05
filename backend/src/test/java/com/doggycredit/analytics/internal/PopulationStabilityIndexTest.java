package com.doggycredit.analytics.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import com.doggycredit.analytics.ModelDrift.Level;

/** D-08 — criterio de "Lista cuando": prueba del cálculo del PSI. */
class PopulationStabilityIndexTest {

	@Test
	void distribucionesIgualesNoTienenDeriva() {
		long[] same = { 5, 10, 20, 30, 20, 10, 5, 0, 0, 0 };
		assertThat(PopulationStabilityIndex.psi(same, same)).isZero();
		// Mismas proporciones con distinto tamaño de muestra: tampoco.
		long[] doubled = { 10, 20, 40, 60, 40, 20, 10, 0, 0, 0 };
		assertThat(PopulationStabilityIndex.psi(same, doubled)).isCloseTo(0, within(1e-12));
	}

	@Test
	void coincideConElCalculoAMano() {
		// (0,8 − 0,5)·ln(0,8/0,5) + (0,2 − 0,5)·ln(0,2/0,5) = 0,415888…
		assertThat(PopulationStabilityIndex.psi(new long[] { 50, 50 }, new long[] { 80, 20 }))
			.isCloseTo(0.4158883083, within(1e-9));
	}

	@Test
	void unTramoVacioSeSuavizaYElResultadoEsFinito() {
		double psi = PopulationStabilityIndex.psi(new long[] { 100, 0 }, new long[] { 50, 50 });
		assertThat(psi).isFinite().isGreaterThan(PopulationStabilityIndex.ALERT);
	}

	@Test
	void umbrales() {
		assertThat(PopulationStabilityIndex.level(0.0)).isEqualTo(Level.ESTABLE);
		assertThat(PopulationStabilityIndex.level(0.0999)).isEqualTo(Level.ESTABLE);
		assertThat(PopulationStabilityIndex.level(0.10)).isEqualTo(Level.VIGILAR);
		assertThat(PopulationStabilityIndex.level(0.2499)).isEqualTo(Level.VIGILAR);
		assertThat(PopulationStabilityIndex.level(0.25)).isEqualTo(Level.ALERTA);
	}

	@Test
	void tramosDelScore() {
		assertThat(PopulationStabilityIndex.bin(0)).isZero();
		assertThat(PopulationStabilityIndex.bin(99)).isZero();
		assertThat(PopulationStabilityIndex.bin(100)).isEqualTo(1);
		assertThat(PopulationStabilityIndex.bin(999)).isEqualTo(9);
		assertThat(PopulationStabilityIndex.bin(1000)).isEqualTo(9);
	}

	@Test
	void histogramasInvalidos() {
		assertThatThrownBy(() -> PopulationStabilityIndex.psi(new long[] { 1 }, new long[] { 1, 2 }))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PopulationStabilityIndex.psi(new long[] { 0, 0 }, new long[] { 1, 2 }))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
