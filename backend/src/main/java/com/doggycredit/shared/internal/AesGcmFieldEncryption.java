package com.doggycredit.shared.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.doggycredit.shared.FieldEncryption;

/**
 * RF-SEG-05.1: cifrado AES-256-GCM con DEK derivada por tenant.
 * <p>
 * Derivación de la DEK: {@code HMAC-SHA256(masterKey, tenantId)} produce
 * 32 bytes que se usan directamente como clave AES-256. Cada cifrado usa
 * un IV de 12 bytes aleatorio (nunca repetido) prepended al ciphertext.
 * El resultado se codifica en Base64 URL-safe sin padding.
 */
@Service
class AesGcmFieldEncryption implements FieldEncryption {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final byte[] masterKeyBytes;
    private final SecureRandom random = new SecureRandom();

    AesGcmFieldEncryption(MasterKeyProperties props) {
        this.masterKeyBytes = Objects.requireNonNull(props.getMasterKey(),
                "masterKey").getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String encrypt(UUID tenantId, String plaintext) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(plaintext, "plaintext");
        try {
            SecretKey dek = deriveDek(tenantId);
            byte[] iv = new byte[GCM_IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, dek, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // IV || ciphertext (includes GCM tag)
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error al cifrar campo", e);
        }
    }

    @Override
    public String decrypt(UUID tenantId, String encoded) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(encoded, "encoded");
        try {
            byte[] data = Base64.getUrlDecoder().decode(encoded);
            if (data.length < GCM_IV_BYTES + 1) {
                throw new IllegalArgumentException("Datos cifrados demasiado cortos");
            }

            byte[] iv = new byte[GCM_IV_BYTES];
            System.arraycopy(data, 0, iv, 0, GCM_IV_BYTES);

            SecretKey dek = deriveDek(tenantId);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, dek, new GCMParameterSpec(GCM_TAG_BITS, iv));

            byte[] plainBytes = cipher.doFinal(data, GCM_IV_BYTES, data.length - GCM_IV_BYTES);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error al descifrar campo", e);
        }
    }

    /**
     * Deriva una DEK de 256 bits para el tenant: HMAC-SHA256(masterKey, tenantId).
     */
    private SecretKey deriveDek(UUID tenantId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(masterKeyBytes, "HmacSHA256"));
            byte[] derived = mac.doFinal(tenantId.toString().getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(derived, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error al derivar DEK", e);
        }
    }
}
