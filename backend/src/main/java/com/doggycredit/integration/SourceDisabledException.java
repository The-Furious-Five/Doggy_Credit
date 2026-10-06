package com.doggycredit.integration;

import com.doggycredit.consent.Source;

/**
 * B-06: el tenant no tiene activa la fuente. No es una caída ni falta de
 * consentimiento: la fuente simplemente no se consulta (NO_CONSULTADA en el Perfil 360).
 */
public class SourceDisabledException extends RuntimeException {

	private final Source source;

	public SourceDisabledException(Source source) {
		super("Fuente " + source + " inactiva para el tenant");
		this.source = source;
	}

	public Source source() {
		return source;
	}

}
