package com.doggycredit.integration;

import java.util.UUID;

/** Credenciales resueltas por tenant y proveedor. Solo para E/S externa; nunca para respuestas ni logs. */
public interface ConnectorCredentials {
    enum Provider { BURO, BANCO, BANCO_DOS, WALLET }

    /** Clave propia, o la de DoggyCredit; cadena vacía conserva los simuladores sin autenticación. */
    String apiKey(UUID tenantId, Provider provider);
}
