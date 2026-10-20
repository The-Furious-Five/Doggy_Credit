package com.doggycredit.consent;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto que implementa el módulo customer (B-07): documento normalizado de un
 * solicitante del tenant. Así consent lista por {@code solicitanteId} sin
 * depender de customer, que ya depende de consent (sin ciclo entre módulos).
 */
public interface ApplicantDocuments {

	Optional<String> documentOf(UUID tenantId, UUID applicantId);

	/**
	 * C-11 (RF-CON-03): el solicitante de un documento, para que la bitácora sepa de qué
	 * titular es cada consentimiento. Vacío si el titular aún no está registrado.
	 */
	Optional<UUID> applicantIdOf(UUID tenantId, String documento);

}
