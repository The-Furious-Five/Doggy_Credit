package com.doggycredit.assessment.internal;

import java.util.Map;
import java.util.Optional;

import com.doggycredit.consent.Source;

/** Resultado de la recolección en paralelo (C-01): qué pasó con cada fuente. */
record SourceCollection(Map<Source, SourceOutcome> outcomes) {

	SourceCollection {
		outcomes = Map.copyOf(outcomes);
	}

	/** PARCIAL (RN-15): alguna fuente consultada no respondió a tiempo o falló. */
	boolean partial() {
		return outcomes.values().stream().anyMatch(SourceOutcome::failed);
	}

	boolean failed(Source source) {
		return outcomes.containsKey(source) && outcomes.get(source).failed();
	}

	/** Lo que devolvió el conector, solo si la fuente respondió. */
	Optional<Object> payload(Source source) {
		SourceOutcome outcome = outcomes.get(source);
		return outcome == null || outcome.status() != SourceOutcome.Status.RECEIVED
				? Optional.empty() : Optional.ofNullable(outcome.payload());
	}

	/** Qué pasó con una fuente que respondió, falló, no se consultó por consentimiento o no existe aún. */
	record SourceOutcome(Status status, Object payload) {

		enum Status {
			RECEIVED, UNAVAILABLE, TIMED_OUT, NOT_CONSENTED
		}

		static SourceOutcome received(Object payload) {
			return new SourceOutcome(Status.RECEIVED, payload);
		}

		static SourceOutcome unavailable() {
			return new SourceOutcome(Status.UNAVAILABLE, null);
		}

		static SourceOutcome timedOut() {
			return new SourceOutcome(Status.TIMED_OUT, null);
		}

		static SourceOutcome notConsented() {
			return new SourceOutcome(Status.NOT_CONSENTED, null);
		}

		boolean failed() {
			return status == Status.UNAVAILABLE || status == Status.TIMED_OUT;
		}

	}

}
