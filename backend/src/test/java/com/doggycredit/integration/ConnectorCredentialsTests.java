package com.doggycredit.integration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.integration.ConnectorCredentials.Provider;
import com.doggycredit.shared.TenantContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"doggycredit.integration.bureau.api-key=doggy-test-BURO",
    "doggycredit.integration.bank.api-key=doggy-test-BANCO", "doggycredit.integration.bank-dos.api-key=doggy-test-BANCO_DOS",
    "doggycredit.integration.wallet.api-key=doggy-test-WALLET"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ConnectorCredentialsTests {
    static final GenericContainer<?> WIREMOCK = new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.9.1"))
        .withCommand("--disable-request-logging", "--no-request-journal")
        .withExposedPorts(8080).waitingFor(Wait.forHttp("/__admin/health"));
    static { WIREMOCK.start(); }
    @Autowired ConnectorCredentials credentials;
    @Autowired MockMvc mvc;
    private static final UUID A = UUID.fromString("9c7e7c1a-0000-4000-8000-000000000001");
    private static final UUID B = UUID.fromString("9c7e7c1a-0000-4000-8000-000000000002");

    @ParameterizedTest
    @EnumSource(Provider.class)
    void wiremockRecibeLaClaveDelTenantEnWorkersYDoggyAlEliminar(Provider provider, CapturedOutput output) throws Exception {
        var auth = jwt().jwt(token -> token.subject("e15").claim("tenant_id", A.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"));
        String path = "/integraciones/" + provider + "/credenciales";
        String key = "tenant-wiremock-test-" + provider;
        SourceConnector<?> connector = connector(provider);
        try {
            mvc.perform(put(path).with(auth).contentType(MediaType.APPLICATION_JSON).content("{\"clave\":\"" + key + "\"}")).andExpect(status().isOk());
            expectKey(provider, key);
            try (var workers = Executors.newSingleThreadExecutor()) {
                workers.submit(() -> {
                    assertThat(TenantContext.currentOrNone()).isNull();
                    assertThatThrownBy(() -> connector.fetch(A, "CI-DEMO-0001001")).hasMessageContaining("HTTP 503");
                    assertThat(TenantContext.currentOrNone()).isNull();
                }).get();
                expectKey(provider, "doggy-test-" + provider);
                workers.submit(() -> assertThatThrownBy(() -> connector.fetch(B, "CI-DEMO-0001001")).hasMessageContaining("HTTP 503")).get();
                mvc.perform(delete(path).with(auth)).andExpect(status().isNoContent());
                workers.submit(() -> assertThatThrownBy(() -> connector.fetch(A, "CI-DEMO-0001001")).hasMessageContaining("HTTP 503")).get();
            }
            assertThat(WIREMOCK.getLogs()).doesNotContain(key).doesNotContain("doggy-test-" + provider);
            assertThat(output.getAll()).doesNotContain(key).doesNotContain("doggy-test-" + provider);
        } finally { mvc.perform(delete(path).with(auth)); }
    }

    @Test
    void restauraContextoInclusoSiFallaLaLectura() {
        TenantContext.set(B);
        try {
            assertThat(credentials.apiKey(A, Provider.BANCO)).isEqualTo("doggy-test-BANCO");
            assertThat(TenantContext.currentOrNone()).isEqualTo(B);
            assertThatThrownBy(() -> credentials.apiKey(A, null)).isInstanceOf(RuntimeException.class);
            assertThat(TenantContext.currentOrNone()).isEqualTo(B);
        } finally { TenantContext.clear(); }
    }

    private SourceConnector<?> connector(Provider provider) {
        Duration timeout = Duration.ofSeconds(2);
        return switch (provider) {
            case BURO -> new BureauConnector(url(), timeout, timeout, credentials);
            case BANCO -> new BankConnector(url(), timeout, timeout, credentials);
            case BANCO_DOS -> new BankDosConnector(url(), timeout, timeout, credentials);
            case WALLET -> new WalletConnector(url(), timeout, timeout, credentials);
        };
    }
    private static void expectKey(Provider provider, String key) {
        var admin = RestClient.create(url());
        admin.delete().uri("/__admin/mappings").retrieve().toBodilessEntity();
        String path = switch (provider) {
            case BURO -> "/bureau/v1/informe/CI-DEMO-0001001";
            case BANCO -> "/bank/v1/clientes/CI-DEMO-0001001";
            case BANCO_DOS -> "/bank-dos/v1/cuentas/CI-DEMO-0001001";
            case WALLET -> "/wallet/v1/titulares/CI-DEMO-0001001";
        };
        admin.post().uri("/__admin/mappings").contentType(MediaType.APPLICATION_JSON)
            .body("{\"request\":{\"method\":\"GET\",\"url\":\"" + path + "\",\"headers\":{\"X-API-Key\":{\"equalTo\":\"" + key + "\"}}},\"response\":{\"status\":503}}")
            .retrieve().toBodilessEntity();
    }
    private static String url() { return "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080); }
}
