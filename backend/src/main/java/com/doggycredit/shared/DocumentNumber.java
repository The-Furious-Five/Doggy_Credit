package com.doggycredit.shared;

import java.util.Locale;
import java.util.Objects;

/**
 * Normalización única del documento del titular. Todo módulo que guarde o
 * busque por documento pasa por aquí, para que "12.345.678-k" y
 * "12345678-K" sean el mismo titular.
 */
public final class DocumentNumber {

	private DocumentNumber() {
	}

	/** Recorta, pasa a mayúsculas y elimina todo espacio en blanco y todo punto. */
	public static String normalize(String raw) {
		return Objects.requireNonNull(raw, "documento").replaceAll("[\\s.]", "").toUpperCase(Locale.ROOT);
	}

}
