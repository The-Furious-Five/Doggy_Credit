package com.doggycredit.shared;

import java.util.Optional;
import java.util.UUID;

/** Comprobación del estado del tenant, después de validar el JWT y antes de despachar la petición. */
public interface TenantAccessPolicy {

    Optional<ErrorCode> rejectionFor(UUID tenantId);
}
