package com.doggycredit.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantNotFoundException;
import com.doggycredit.tenancy.TenantRegistry;

/**
 * HAB-03: el tenantId del token JWT coincide exactamente con el tenantId
 * de seed/tenants.yaml (verificado por este test sobre Testcontainers).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TenantRegistryTest {

    /** Los mismos UUID de seed/tenants.yaml — lección 2 de MedicalSys. */
    private static final UUID PROGRESO_ANDINO = UUID.fromString("9c7e7c1a-0000-4000-8000-000000000001");
    private static final UUID FINTECH_VECINA = UUID.fromString("9c7e7c1a-0000-4000-8000-000000000002");

    @Autowired
    TenantRegistry registry;

    @Test
    void progresoAndinoExisteConDatosDelSeed() {
        TenantInfo t = registry.requireById(TenantId.of(PROGRESO_ANDINO));
        assertThat(t.id()).isEqualTo(PROGRESO_ANDINO);
        assertThat(t.nombre()).isEqualTo("Progreso Andino");
        assertThat(t.nit()).isEqualTo("1000000010");
        assertThat(t.tipo()).isEqualTo("IMF");
        assertThat(t.plan()).isEqualTo("FREE");
        assertThat(t.pais()).isEqualTo("BO");
        assertThat(t.moneda()).isEqualTo("BOB");
        assertThat(t.activo()).isTrue();
    }

    @Test
    void fintechVecinaExisteConDatosDelSeed() {
        TenantInfo t = registry.requireById(TenantId.of(FINTECH_VECINA));
        assertThat(t.id()).isEqualTo(FINTECH_VECINA);
        assertThat(t.nombre()).isEqualTo("Fintech Vecina");
        assertThat(t.plan()).isEqualTo("PRO");
    }

    @Test
    void tenantInexistenteRetornaVacio() {
        UUID desconocido = UUID.fromString("00000000-0000-0000-0000-000000000099");
        Optional<TenantInfo> result = registry.findById(TenantId.of(desconocido));
        assertThat(result).isEmpty();
    }

    @Test
    void requireByIdConDesconocidoLanzaExcepcion() {
        UUID desconocido = UUID.fromString("00000000-0000-0000-0000-000000000099");
        assertThatThrownBy(() -> registry.requireById(TenantId.of(desconocido)))
                .isInstanceOf(TenantNotFoundException.class);
    }
}
