package com.doggycredit.tenancy.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.doggycredit.shared.internal.IdentitySecurityConfiguration;
import com.doggycredit.shared.internal.ProblemDetailAccessDeniedHandler;
import com.doggycredit.shared.internal.ProblemDetailAuthenticationEntryPoint;
import com.doggycredit.shared.internal.SecurityProblemDetailWriter;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.doggycredit.shared.contract.model.TenantPatch;
import com.doggycredit.billing.OperatorUsageQuery;
import com.doggycredit.billing.PlanCatalog;
import com.doggycredit.billing.PlanInfo;

@WebMvcTest(AdminController.class)
@Import({IdentitySecurityConfiguration.class, SecurityProblemDetailWriter.class,
        ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class,
        AdminControllerTests.DecoderFixture.class})
class AdminControllerTests {

    @Autowired
    MockMvc mvc;

    ObjectMapper mapper = new ObjectMapper();

    @MockitoBean
    TenantRegistry tenantRegistry;

    @MockitoBean
    PlanChangeService planChanges;

    @MockitoBean
    TenantStatusService statuses;

    @MockitoBean
    OperatorUsageQuery usage;

    @MockitoBean
    PlanCatalog plans;

    @BeforeEach
    void usageFixture() {
        when(usage.evaluationsForMonth(any())).thenReturn(Map.of());
        when(plans.requireById("PRO")).thenReturn(
                new PlanInfo("PRO", "Pro", 0, 50, 999, true, true, true, true, true));
    }

    @Test
    void allAdminRequestsAreMappedAndRoleProtected() throws Exception {
        mvc.perform(get("/admin/tenants"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DecoderFixture {
        @Bean
        JwtDecoder decoder() {
            return token -> {
                var builder = Jwt.withTokenValue(token).header("alg", "RS256").subject("demo")
                    .claim("tenant_id", "123e4567-e89b-12d3-a456-426614174000");
                if (token.equals("analyst")) {
                    builder.claim("realm_access", Map.of("roles", List.of("ANALYST")));
                } else if (token.equals("tenant-admin")) {
                    builder.claim("realm_access", Map.of("roles", List.of("TENANT_ADMIN")));
                } else if (token.equals("admin")) {
                    builder.claim("realm_access", Map.of("roles", List.of("PLATFORM_ADMIN")));
                }
                return builder.build();
            };
        }
    }

    // A-11: subir de plan es del operador. Ni el administrador del tenant puede subirse a sí mismo.
    @Test
    void onlyPlatformAdminCanChangeAPlan() throws Exception {
        UUID tenantId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        for (String token : List.of("analyst", "tenant-admin")) {
            mvc.perform(patch("/admin/tenants/{id}", tenantId)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PRO\"}")
                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
        }
        mvc.perform(patch("/admin/tenants/{id}", tenantId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PRO\"}"))
                .andExpect(status().isUnauthorized());
        verify(planChanges, never()).changeNow(any(), any());
    }

    @Test
    void platformAdminChangesThePlanOfTheTenantInThePath() throws Exception {
        UUID tenantId = UUID.fromString("9c7e7c1a-0000-4000-8000-000000000001");
        when(tenantRegistry.requireById(TenantId.of(tenantId))).thenReturn(
                new TenantInfo(tenantId, "Progreso Andino", "1000000010", "IMF", "PRO", "BO", "BOB", true));

        mvc.perform(patch("/admin/tenants/{id}", tenantId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PRO\"}")
                .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tenantId.toString()))
                .andExpect(jsonPath("$.plan").value("PRO"));

        // El tenant afectado es el de la ruta, no el del token del operador.
        verify(planChanges).changeNow(tenantId, "PRO");
        verify(tenantRegistry, never()).update(any(), any(), any());
    }

    @Test
    void anUnknownPlanIsRejectedBeforeChangingAnything() throws Exception {
        mvc.perform(patch("/admin/tenants/{id}", UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PLATINUM\"}")
                .header("Authorization", "Bearer admin"))
                .andExpect(status().isBadRequest());
        verify(planChanges, never()).changeNow(any(), any());
    }

    @Test
    void nonAdminGets403() throws Exception {
        mvc.perform(get("/admin/tenants")
                .header("Authorization", "Bearer analyst"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
    }

    @Test
    void platformAdminGets200() throws Exception {
        UUID tenantId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        TenantInfo info = new TenantInfo(tenantId, "Mi Fintech", "1234567", "FINTECH", "PRO", "BO", "BOB", true);
        
        when(tenantRegistry.findAll()).thenReturn(List.of(info));

        mvc.perform(get("/admin/tenants")
                .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(tenantId.toString()))
                .andExpect(jsonPath("$[0].nombre").value("Mi Fintech"));
    }

    @Test
    void platformAdminCanToggleActiveStatus() throws Exception {
        UUID tenantId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        TenantInfo info = new TenantInfo(tenantId, "Mi Fintech", "1234567", "FINTECH", "PRO", "BO", "BOB", false);
        
        when(statuses.setActive(tenantId, false)).thenReturn(info);

        TenantPatch patch = new TenantPatch().activo(false);

        mvc.perform(patch("/admin/tenants/{id}", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(patch))
                .header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void platformAdminCannotSubmitAnEmptyTenantPatch() throws Exception {
        mvc.perform(patch("/admin/tenants/{id}", UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")
                .header("Authorization", "Bearer admin"))
                .andExpect(status().isBadRequest());
    }
}
