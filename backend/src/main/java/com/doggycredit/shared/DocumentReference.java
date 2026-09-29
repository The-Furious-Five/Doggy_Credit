package com.doggycredit.shared;

import java.util.UUID;

/**
 * Referencia determinista y no reversible de un documento, separada por tenant.
 * Permite buscar y aplicar unicidad sin guardar el documento normalizado en claro.
 */
public interface DocumentReference {

	String create(UUID tenantId, String normalizedDocument);

}
