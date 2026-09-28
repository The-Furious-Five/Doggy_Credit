package com.doggycredit.integration;

import com.doggycredit.consent.Source;

/**
 * La fuente no respondió a tiempo, respondió con error o con un contenido
 * que no se pudo mapear. No es "titular sin registro": el orquestador
 * (HAB-04) la trata como fuente caída y la evaluación queda PARCIAL.
 */
public class SourceUnavailableException extends RuntimeException {

	private final Source source;

	public SourceUnavailableException(Source source, String message, Throwable cause) {
		super("Fuente " + source + " no disponible: " + message, cause);
		this.source = source;
	}

	public Source source() {
		return source;
	}

}
