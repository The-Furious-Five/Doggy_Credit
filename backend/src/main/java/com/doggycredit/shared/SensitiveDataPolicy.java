package com.doggycredit.shared;

/**
 * A-10 (LOS-71): enmascarado de documentos y montos en las respuestas. Lo decide el
 * servidor en cada petición, con la preferencia del tenant (Configuración › Seguridad)
 * y los roles del JWT, para que un analista no reciba el dato completo aunque llame a
 * la API sin pasar por la consola. Lo implementa tenancy, dueño de la preferencia.
 */
public interface SensitiveDataPolicy {

	String MASK = "••••••";

	/** true si el usuario de la petición actual no debe ver documentos ni montos completos. */
	boolean maskForCurrentUser();

	/**
	 * Decide una vez por respuesta y enmascara cada campo con el resultado: un listado no
	 * consulta la preferencia por fila.
	 */
	default Masker forCurrentUser() {
		return new Masker(maskForCurrentUser());
	}

	record Masker(boolean active) {

		/** Conserva los últimos 4 caracteres: lo mismo que muestra la consola. */
		public String document(String value) {
			if (value == null || !active) {
				return value;
			}
			int visible = Math.min(4, value.length());
			return "•".repeat(Math.max(4, value.length() - visible)) + value.substring(value.length() - visible);
		}

		public String amount(String value) {
			return value == null || !active ? value : MASK;
		}

	}

}
