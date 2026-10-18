package com.doggycredit.integration.internal;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.integration.ConnectorCredentials.Provider;
import com.doggycredit.shared.FieldEncryption;
import com.doggycredit.shared.TenantContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class IntegrationCredentialsTests {
    private static final UUID A = UUID.fromString("9c7e7c1a-0000-4000-8000-000000000001");
    private static final UUID B = UUID.fromString("9c7e7c1a-0000-4000-8000-000000000002");
    private static final String KEY = "tenant-only-test-key-1357";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired FieldEncryption encryption;
    @Autowired IntegrationCredentialsService credentials;
    @Autowired PlatformTransactionManager manager;

    @ParameterizedTest
    @EnumSource(Provider.class)
    void cifraAislaAuditaYEliminaSinExponerElSecreto(Provider provider, CapturedOutput output) throws Exception {
        String path = path(provider);
        mvc.perform(delete(path).with(as(A, "TENANT_ADMIN"))).andExpect(status().isNoContent());
        mvc.perform(get(path).with(as(A, "TENANT_ADMIN"))).andExpect(content().json("{\"configuradas\":false,\"ultimosCuatro\":\"\"}"));
        try {
            save(A, provider, KEY).andExpect(status().isOk())
                .andExpect(content().json("{\"configuradas\":true,\"ultimosCuatro\":\"1357\"}"))
                .andExpect(jsonPath("$.clave").doesNotExist());
            TenantContext.set(A);
            assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("doggycredit_app");
            String raw = jdbc.queryForObject("SELECT secret_ciphertext FROM integration.tenant_credentials WHERE provider = ?",
                    String.class, provider.name());
            assertThat(raw).doesNotContain(KEY).doesNotContain("tenant-only");
            assertThat(encryption.decrypt(A, raw)).isEqualTo(KEY);
            assertThatThrownBy(() -> encryption.decrypt(B, raw)).isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.access_log WHERE action = 'INTEGRATION_CREDENTIALS_SAVED' AND resource_type = ? AND actor = 'admin-e15'",
                    Integer.class, "INTEGRATION_CREDENTIALS_" + provider)).isPositive();
            TenantContext.set(B);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.tenant_credentials WHERE tenant_id = ?", Integer.class, A)).isZero();
            assertThatThrownBy(() -> jdbc.update("INSERT INTO integration.tenant_credentials VALUES (?, ?, ?, ?, ?, now())",
                    UUID.randomUUID(), A, provider.name(), "cipher", "1234")).isInstanceOf(org.springframework.dao.DataAccessException.class);
            TenantContext.clear();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM integration.tenant_credentials", Integer.class)).isZero();
            mvc.perform(get(path).with(as(B, "TENANT_ADMIN"))).andExpect(jsonPath("$.configuradas").value(false));
            mvc.perform(delete(path).with(as(B, "TENANT_ADMIN"))).andExpect(status().isNoContent());
            mvc.perform(get(path).with(as(A, "TENANT_ADMIN"))).andExpect(jsonPath("$.ultimosCuatro").value("1357"));
            save(A, provider, "replacement-test-key-2468").andExpect(status().isOk()).andExpect(jsonPath("$.ultimosCuatro").value("2468"));
            mvc.perform(delete(path).with(as(A, "TENANT_ADMIN"))).andExpect(status().isNoContent());
            mvc.perform(delete(path).with(as(A, "TENANT_ADMIN"))).andExpect(status().isNoContent());
            mvc.perform(get(path).with(as(A, "TENANT_ADMIN"))).andExpect(jsonPath("$.configuradas").value(false));
            TenantContext.set(A);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.access_log WHERE action = 'INTEGRATION_CREDENTIALS_DELETED' AND resource_type = ?",
                    Integer.class, "INTEGRATION_CREDENTIALS_" + provider)).isPositive();
            assertThat(output.getAll()).doesNotContain(KEY).doesNotContain("replacement-test-key-2468");
        } finally {
            TenantContext.clear();
            mvc.perform(delete(path).with(as(A, "TENANT_ADMIN")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ANALYST", "PLATFORM_ADMIN", "API_CLIENT"})
    void soloElAdministradorDelTenantPuedeLeerGuardarOEliminar(String role) throws Exception {
        mvc.perform(get(path(Provider.BURO)).with(as(A, role))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
        mvc.perform(put(path(Provider.BURO)).with(as(A, role)).contentType(MediaType.APPLICATION_JSON).content("{\"clave\":\"" + KEY + "\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete(path(Provider.BURO)).with(as(A, role))).andExpect(status().isForbidden());
    }

    @Test
    void exigeAutenticacionYRechazaClavesInvalidasSinReflejarlas(CapturedOutput output) throws Exception {
        mvc.perform(get(path(Provider.BURO))).andExpect(status().isUnauthorized());
        for (String body : new String[] {"{}", "{\"clave\":\"1234\"}", "{\"clave\":\"invalid secret\"}",
                "{\"clave\":\"header-injection\\r\\nX-Other: value\"}", "{\"clave\":\"" + "x".repeat(2049) + "\"}"}) {
            var result = mvc.perform(put(path(Provider.BURO)).with(as(A, "TENANT_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("invalid secret").doesNotContain("header-injection");
        }
        mvc.perform(get("/integraciones/OPEN_BANKING/credenciales").with(as(A, "TENANT_ADMIN"))).andExpect(status().isBadRequest());
        assertThat(output.getAll()).doesNotContain("invalid secret").doesNotContain("header-injection");
    }

    @Test
    void claveYAuditoriaSeConfirmanEnLaMismaTransaccion() {
        TenantContext.set(B);
        try {
            int before = jdbc.queryForObject("SELECT count(*) FROM audit.access_log WHERE action = 'INTEGRATION_CREDENTIALS_SAVED'", Integer.class);
            assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(tx -> {
                credentials.save(B, Provider.BANCO_DOS, KEY);
                throw new IllegalStateException("rollback deliberado");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(credentials.state(B, Provider.BANCO_DOS).configured()).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.access_log WHERE action = 'INTEGRATION_CREDENTIALS_SAVED'", Integer.class)).isEqualTo(before);
        } finally { TenantContext.clear(); }
    }

    private ResultActions save(UUID tenant, Provider provider, String key) throws Exception {
        return mvc.perform(put(path(provider)).with(as(tenant, "TENANT_ADMIN"))
                .header("X-Tenant-Id", B.toString()).contentType(MediaType.APPLICATION_JSON).content("{\"clave\":\"" + key + "\"}"));
    }
    private static String path(Provider provider) { return "/integraciones/" + provider + "/credenciales"; }
    private static RequestPostProcessor as(UUID tenant, String role) {
        return jwt().jwt(token -> token.subject("admin-e15").claim("tenant_id", tenant.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
