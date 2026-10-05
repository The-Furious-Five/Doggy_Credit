package com.doggycredit.customer;

import java.util.UUID;

public interface ApplicantRegistry {

	ApplicantProfile register(UUID tenantId, ApplicantRegistration registration);

	ApplicantProfile findRequired(UUID tenantId, String document);

	/** B-08: @throws com.doggycredit.shared.UnknownApplicantException si no es de este tenant. */
	ApplicantProfile findById(UUID tenantId, UUID applicantId);

	/**
	 * B-08: listado del más reciente al más antiguo. {@code segment} y {@code document}
	 * son filtros opcionales; el documento se busca por su HMAC, nunca en claro.
	 *
	 * @param page 1 = primera página
	 */
	ApplicantPage list(UUID tenantId, ApplicantSegment segment, String document, int page, int size);

}
