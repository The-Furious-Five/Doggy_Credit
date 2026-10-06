package com.doggycredit.tenancy.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.doggycredit.billing.PlanCatalog;
import com.doggycredit.billing.UsageMeter;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.doggycredit.shared.internal.IdentitySecurityConfiguration;
import com.doggycredit.shared.internal.ProblemDetailAccessDeniedHandler;
import com.doggycredit.shared.internal.ProblemDetailAuthenticationEntryPoint;
import com.doggycredit.shared.internal.SecurityProblemDetailWriter;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantNotFoundException;
import com.doggycredit.tenancy.TenantRegistry;
import com.doggycredit.billing.PlanInfo;
import com.doggycredit.shared.contract.model.PreferenciasTenant;
import com.doggycredit.shared.contract.model.OrganizacionTenantPatch;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(TenantController.class)
@Import({IdentitySecurityConfiguration.class, SecurityProblemDetailWriter.class,
        ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class,
        TenantControllerTests.DecoderFixture.class, TenantControllerTests.ControllerFixture.class})
class TenantControllerTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TenantRegistry tenantRegistry;

    @MockitoBean
    TenantPreferencesService preferences;

    @MockitoBean
    PlanCatalog planCatalog;

    @MockitoBean
    UsageMeter usageMeter;

    @MockitoBean
    KeycloakClients keycloak;

    @TestConfiguration(proxyBeanMethods = false)
    static class DecoderFixture {
        @Bean
        JwtDecoder decoder() {
            return token -> {
                var builder = Jwt.withTokenValue(token).header("alg", "RS256").subject("demo");
                if (token.equals("valid") || token.equals("unknown")) {
                    builder.claim("tenant_id", "123e4567-e89b-12d3-a456-426614174000")
                        .claim("realm_access", Map.of("roles", List.of("ANALYST")));
                } else if (token.equals("tenant-admin")) {
                    builder.claim("tenant_id", "123e4567-e89b-12d3-a456-426614174000")
                        .claim("realm_access", Map.of("roles", List.of("TENANT_ADMIN")));
                }
                return builder.build();
            };
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ControllerFixture {
        @Bean
        TenantController tenantController(TenantRegistry registry, TenantPreferencesService preferences,
                PlanCatalog plans, UsageMeter usage, KeycloakClients keycloak) {
            return new TenantController(registry, preferences, plans, usage, keycloak);
        }
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mvc.perform(get("/tenant/actual"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withValidToken_returns200() throws Exception {
        UUID tenantId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        TenantInfo info = new TenantInfo(tenantId, "Mi Fintech", "1234567", "FINTECH", "PRO", "BO", "BOB", true);
        
        when(tenantRegistry.requireById(TenantId.of(tenantId))).thenReturn(info);

        mvc.perform(get("/tenant/actual")
                .header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tenantId.toString()))
                .andExpect(jsonPath("$.nombre").value("Mi Fintech"))
                .andExpect(jsonPath("$.nit").value("1234567"))
                .andExpect(jsonPath("$.tipo").value("FINTECH"))
                .andExpect(jsonPath("$.plan").value("PRO"))
                .andExpect(jsonPath("$.pais").value("BO"))
                .andExpect(jsonPath("$.moneda").value("BOB"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void withUnknownTenant_returns403() throws Exception {
        UUID tenantId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        
        when(tenantRegistry.requireById(TenantId.of(tenantId)))
                .thenThrow(new TenantNotFoundException(TenantId.of(tenantId)));

        mvc.perform(get("/tenant/actual")
                .header("Authorization", "Bearer unknown"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantAdminCanPersistMaskingPreference() throws Exception {
        when(preferences.update(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), false))
                .thenReturn(new TenantSecurityPreferences(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), false));
        var body = new PreferenciasTenant().enmascararDatos(false);

        mvc.perform(patch("/tenant/actual/preferencias").contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(body)).header("Authorization", "Bearer tenant-admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enmascararDatos").value(false));
        verify(preferences).update(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), false);
    }

    @Test
    void analystCannotChangeMaskingPreference() throws Exception {
        var body = new PreferenciasTenant().enmascararDatos(false);
        mvc.perform(patch("/tenant/actual/preferencias").contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(body)).header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
    }

    @Test
    void tenantAdminCanUpdateItsOwnOrganizationName() throws Exception {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(tenantRegistry.updateName(TenantId.of(id), "Mi institución"))
                .thenReturn(new TenantInfo(id, "Mi institución", "1234567", "FINTECH", "PRO", "BO", "BOB", true));
        var body = new OrganizacionTenantPatch().nombre("Mi institución");

        mvc.perform(patch("/tenant/actual/organizacion").contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(body)).header("Authorization", "Bearer tenant-admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Mi institución"));
    }

    @Test
    void actualUsageReturnsTenantPlanLimitAndCurrentCount() throws Exception {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(tenantRegistry.requireById(TenantId.of(id)))
                .thenReturn(new TenantInfo(id, "Mi Fintech", "1234567", "FINTECH", "FREE", "BO", "BOB", true));
        when(planCatalog.requireById("FREE")).thenReturn(new PlanInfo("FREE", "Gratis", 500, 3, 1, false, false, false, false, false));
        when(usageMeter.currentMonthEvaluations(id)).thenReturn(81);

        mvc.perform(get("/tenant/actual/uso").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evaluacionesMes").value(81))
                .andExpect(jsonPath("$.limiteMensual").value(500));
    }

    @Test
    void teamListComesFromKeycloakForTheTenantInTheToken() throws Exception {
        UUID tenantId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        when(keycloak.listTeamMembers(tenantId)).thenReturn(List.of(
                new KeycloakClients.TeamMember("Ana Pérez", "ana@example.test", "TENANT_ADMIN", true),
                new KeycloakClients.TeamMember("Luis Rojas", "luis@example.test", "ANALYST", false)));

        mvc.perform(get("/tenant/actual/equipo").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$[0].rol").value("TENANT_ADMIN"))
                .andExpect(jsonPath("$[1].estado").value("DESACTIVADO"));
        verify(keycloak).listTeamMembers(tenantId);
    }
}
