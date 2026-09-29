package com.doggycredit.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.yaml.snakeyaml.Yaml;

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

    @Autowired
    JdbcTemplate jdbc;

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

    /**
     * HAB-03, criterio de salida: la base coincide con seed/tenants.yaml —el
     * mismo archivo del que se genera el realm de Keycloak— leyendo el
     * archivo, no una copia de sus UUID. Si alguien cambia la semilla y no la
     * migración (o al revés), los tokens traerían un tenant_id que la base no
     * conoce: exactamente la lección 2 de MedicalSys.
     */
    @Test
    @SuppressWarnings("unchecked")
    void laBaseCoincideExactamenteConSeedTenantsYaml() throws Exception {
        Map<String, Object> seed = new Yaml().load(Files.readString(repoPath("seed/tenants.yaml")));
        List<Map<String, Object>> tenants = (List<Map<String, Object>>) seed.get("tenants");
        assertThat(tenants).isNotEmpty();

        for (Map<String, Object> s : tenants) {
            UUID id = UUID.fromString((String) s.get("tenant_id"));
            TenantInfo t = registry.findById(TenantId.of(id))
                    .orElseThrow(() -> new AssertionError("Falta en la base el tenant de la semilla " + id));
            assertThat(t.nombre()).as(id.toString()).isEqualTo(s.get("nombre"));
            assertThat(t.nit()).as(id.toString()).isEqualTo(s.get("nit"));
            assertThat(t.tipo()).as(id.toString()).isEqualTo(s.get("tipo"));
            assertThat(t.plan()).as(id.toString()).isEqualTo(s.get("plan"));
            assertThat(t.pais()).as(id.toString()).isEqualTo(s.get("pais"));
            assertThat(t.moneda()).as(id.toString()).isEqualTo(s.get("moneda"));
        }
        assertThat(jdbc.queryForObject("select count(*) from tenancy.tenants", Integer.class))
                .as("la base no debe tener tenants que la semilla no declare")
                .isEqualTo(tenants.size());
    }

    private static Path repoPath(String relative) {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve(relative))) {
                return dir.resolve(relative);
            }
        }
        throw new IllegalStateException("No se encontró " + relative);
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
