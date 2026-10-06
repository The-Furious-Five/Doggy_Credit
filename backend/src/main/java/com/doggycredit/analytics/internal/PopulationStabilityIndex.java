package com.doggycredit.analytics.internal;

import com.doggycredit.analytics.ModelDrift.Level;

/**
 * PSI = Σ (a_i − e_i) · ln(a_i / e_i), con a_i y e_i la proporción de la
 * ventana actual y de la línea base en cada tramo del score. Un tramo vacío
 * se suaviza con {@link #EPSILON} para que el logaritmo exista.
 */
final class PopulationStabilityIndex {

	/** 10 tramos de 100 puntos sobre la escala 0–1000. */
	static final int BINS = 10;
	static final double EPSILON = 1e-4;
	static final double WATCH = 0.10;
	static final double ALERT = 0.25;

	private PopulationStabilityIndex() {
	}

	static int bin(int score) {
		return Math.min(BINS - 1, Math.max(0, score / 100));
	}

	static double psi(long[] baseline, long[] actual) {
		if (baseline.length != actual.length) {
			throw new IllegalArgumentException("Los histogramas deben tener los mismos tramos");
		}
		double baselineTotal = sum(baseline);
		double actualTotal = sum(actual);
		if (baselineTotal == 0 || actualTotal == 0) {
			throw new IllegalArgumentException("Histograma vacío");
		}
		double psi = 0;
		for (int i = 0; i < baseline.length; i++) {
			double e = Math.max(baseline[i] / baselineTotal, EPSILON);
			double a = Math.max(actual[i] / actualTotal, EPSILON);
			psi += (a - e) * Math.log(a / e);
		}
		return psi;
	}

	static Level level(double psi) {
		if (psi >= ALERT) {
			return Level.ALERTA;
		}
		return psi >= WATCH ? Level.VIGILAR : Level.ESTABLE;
	}

	private static double sum(long[] values) {
		double total = 0;
		for (long value : values) {
			total += value;
		}
		return total;
	}

}
