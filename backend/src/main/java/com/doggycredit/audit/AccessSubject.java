package com.doggycredit.audit;

import java.util.UUID;

/**
 * C-11 (LOS-112), RF-CON-03: de quién son los datos que tocó una acción y con qué
 * autorización. Lo lee el reporte de accesos para responder «quién consultó los datos de
 * este titular y con qué consentimiento». Ambos campos pueden faltar: no toda acción toca
 * datos de un titular, ni toda acción necesita un consentimiento.
 *
 * @param applicantId titular (solicitante) cuyos datos se tocaron.
 * @param consentId consentimiento con el que se hizo la acción.
 */
public record AccessSubject(UUID applicantId, UUID consentId) {

	/** La acción no toca datos de un titular. */
	public static final AccessSubject NONE = new AccessSubject(null, null);

	public static AccessSubject applicant(UUID applicantId) {
		return new AccessSubject(applicantId, null);
	}

	public static AccessSubject applicant(UUID applicantId, UUID consentId) {
		return new AccessSubject(applicantId, consentId);
	}

}
