package com.doggycredit.shared.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.doggycredit.shared.FieldEncryption;

/**
 * RF-SEG-05.1: roundtrip de cifrado, aislamiento por tenant, IV aleatorio,
 * y rechazo de clave demo en producción.
 */
class FieldEncryptionTest {

    private static final UUID TENANT_A = UUID.fromString("9c7e7c1a-0000-4000-8000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("9c7e7c1a-0000-4000-8000-000000000002");

    private FieldEncryption encryption() {
        MasterKeyProperties props = new MasterKeyProperties(new MockEnvironment());
        props.setMasterKey("test-key-only-for-unit-tests-32ch");
        props.validate();
        return new AesGcmFieldEncryption(props);
    }

    @Test
    void roundtrip() {
        FieldEncryption enc = encryption();
        String original = "CI-DEMO-0001001";
        String encrypted = enc.encrypt(TENANT_A, original);
        String decrypted = enc.decrypt(TENANT_A, encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void distintosTenantProducenDistintoCiphertext() {
        FieldEncryption enc = encryption();
        String text = "MISMO-DOCUMENTO";
        String a = enc.encrypt(TENANT_A, text);
        String b = enc.encrypt(TENANT_B, text);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void ivAleatorioProduceCiphertextsDistintos() {
        FieldEncryption enc = encryption();
        String text = "CI-DEMO-0001001";
        String first = enc.encrypt(TENANT_A, text);
        String second = enc.encrypt(TENANT_A, text);
        assertThat(first).isNotEqualTo(second);
        // Ambos descifran al mismo valor
        assertThat(enc.decrypt(TENANT_A, first)).isEqualTo(text);
        assertThat(enc.decrypt(TENANT_A, second)).isEqualTo(text);
    }

    @Test
    void noDescifraConOtroTenant() {
        FieldEncryption enc = encryption();
        String encrypted = enc.encrypt(TENANT_A, "secreto");
        // GCM tag verification should fail
        assertThatThrownBy(() -> enc.decrypt(TENANT_B, encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rechazaClaveDemoEnProduccion() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        MasterKeyProperties props = new MasterKeyProperties(env);
        props.setMasterKey(MasterKeyProperties.DEMO_KEY);
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("producción");
    }

    @Test
    void aceptaClaveDemoFueraDeProduccion() {
        MasterKeyProperties props = new MasterKeyProperties(new MockEnvironment());
        props.setMasterKey(MasterKeyProperties.DEMO_KEY);
        // No debe lanzar excepción en dev
        props.validate();
    }
}
