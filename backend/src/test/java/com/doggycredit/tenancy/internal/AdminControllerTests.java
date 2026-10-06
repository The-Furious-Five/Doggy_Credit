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

import static org.mockito.Mockito.when;
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

@WebMvcTest(AdminController.class)
@Import({IdentitySecurityConfiguration.class, SecurityProblemDetailWriter.class,
        ProblemDetailAuthenticationEntryPoint.class, ProblemDetailAccessDeniedHandler.class,
        AdminControllerTests.DecoderFixture.class, AdminControllerTests.ControllerFixture.class})
class AdminControllerTests {

    @Autowired
    MockMvc mvc;

    ObjectMapper mapper = new ObjectMapper();

    @MockitoBean
    TenantRegistry tenantRegistry;

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
                } else if (token.equals("admin")) {
                    builder.claim("realm_access", Map.of("roles", List.of("PLATFORM_ADMIN")));
                }
                return builder.build();
            };
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ControllerFixture {
        @Bean
        AdminController adminController(TenantRegistry registry) {
            return new AdminController(registry);
        }
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
        
        when(tenantRegistry.update(any(), any(), any())).thenReturn(info);

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
