package com.doggycredit.shared;

import java.util.UUID;

/**
 * RF-SEG-05.1: cifrado por campo. Los módulos que almacenan datos sensibles
 * (documento de identidad, NIT) cifran y descifran a través de esta interfaz
 * del kernel compartido. La implementación usa AES-256-GCM con una DEK
 * derivada por tenant (ADR-009).
 */
public interface FieldEncryption {

    /** Cifra {@code plaintext} con la DEK del tenant. Resultado: Base64-URL(IV ‖ ciphertext ‖ tag). */
    String encrypt(UUID tenantId, String plaintext);

    /** Descifra un valor producido por {@link #encrypt}. */
    String decrypt(UUID tenantId, String ciphertext);
}
