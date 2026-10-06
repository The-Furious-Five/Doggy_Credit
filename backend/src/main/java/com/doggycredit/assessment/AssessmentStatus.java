package com.doggycredit.assessment;

import java.util.Optional;

public enum AssessmentStatus {
	PENDIENTE, PARCIAL, PREAPROBADO, REVISION_MANUAL, SOLICITAR_DATOS, FALLIDA;

	/**
	 * Estado publicado en la API y en los eventos v1, cuyo enum no incluye SOLICITAR_DATOS
	 * (agregarlo rompería a los consumidores): pedir datos también necesita a una persona.
	 */
	public AssessmentStatus published() {
		return this == SOLICITAR_DATOS ? REVISION_MANUAL : this;
	}

	/**
	 * C-05: decisión de las políticas. PARCIAL nunca se preaprueba: queda en revisión
	 * manual. Vacía si la evaluación no terminó. Nunca es un rechazo.
	 */
	public Optional<AssessmentStatus> decision() {
		return switch (this) {
			case PREAPROBADO, REVISION_MANUAL, SOLICITAR_DATOS -> Optional.of(this);
			case PARCIAL -> Optional.of(REVISION_MANUAL);
			case PENDIENTE, FALLIDA -> Optional.empty();
		};
	}
}
