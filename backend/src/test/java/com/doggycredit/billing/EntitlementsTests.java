package com.doggycredit.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.doggycredit.TestTenants;
import com.doggycredit.TestcontainersConfiguration;

/**
 * A-02 (LOS-65): {@code Entitlements.of(tenantId)} es la fuente única de límites y
 * funciones, y coincide con lo que la landing publica en {@code #planes}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
// Registra tenants y filas propias: se descarta su contenedor para no alterar a
// TenantRegistryTest ni a ProductCatalogTableTests, que cuentan lo sembrado.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EntitlementsTests {

    /** La landing que se publica; la prueba corre desde backend/. */
    private static final Path LANDING = Path.of("..", "web", "src", "app", "landing", "landing.ts");

    @Autowired
    Entitlements entitlements;

    @Autowired
    PostgreSQLContainer postgres;

    @Test
    void freeYProTienenLosLimitesYFuncionesDelCatalogo() {
        var free = entitlements.of(tenant("FREE"));
        assertThat(free.plan()).isEqualTo("FREE");
        assertThat(free.evaluacionesMes()).isEqualTo(500);
        assertThat(free.maxUsuarios()).isEqualTo(3);
        assertThat(free.maxIntegraciones()).isEqualTo(1);
        assertThat(free.features()).isEmpty();
        assertThat(free.allows(Feature.RECOMENDACIONES)).isFalse();

        var pro = entitlements.of(tenant("PRO"));
        assertThat(pro.evaluacionesMes()).isZero();
        assertThat(pro.maxIntegraciones()).as("999 en billing.plans se publica como sin límite").isZero();
        assertThat(pro.features()).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Feature.class));
    }

    @Test
    void unCambioDePlanSeNotaEnLaSiguienteConsulta() throws Exception {
        UUID tenant = tenant("FREE");
        assertThat(entitlements.of(tenant).allows(Feature.ANALYTICS)).isFalse();

        try (var admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()); var update = admin.prepareStatement(
                        "update tenancy.tenants set plan = 'PRO' where id = ?")) {
            update.setObject(1, tenant);
            update.executeUpdate();
        }

        assertThat(entitlements.of(tenant).allows(Feature.ANALYTICS)).isTrue();
    }

    @Test
    void unTenantInexistenteNoTieneEntitlements() {
        assertThatThrownBy(() -> entitlements.of(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void losLimitesCoincidenConLosPlanesPublicadosEnLaLanding() throws Exception {
        String landing = Files.readString(LANDING);
        var free = entitlements.of(tenant("FREE"));
        var pro = entitlements.of(tenant("PRO"));

        assertThat(landing).contains("'" + free.evaluacionesMes() + " consultas al mes'");
        assertThat(landing).contains("'" + free.maxIntegraciones() + " integración'");
        assertThat(landing).contains("'Hasta " + free.maxUsuarios() + " usuarios'");
        assertThat(pro.evaluacionesMes()).isZero();
        assertThat(landing).contains("'Consultas ilimitadas'");
        assertThat(pro.maxIntegraciones()).isZero();
        assertThat(landing).contains("'Integraciones ilimitadas'");
    }

    private UUID tenant(String plan) {
        UUID id = UUID.randomUUID();
        TestTenants.register(postgres, id.toString(), plan);
        return id;
    }

}
