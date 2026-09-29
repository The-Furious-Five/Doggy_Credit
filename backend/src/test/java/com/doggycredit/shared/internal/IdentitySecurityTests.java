package com.doggycredit.shared.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IdentitySecurityTests.Probe.class)
@Import({IdentitySecurityConfiguration.class, IdentitySecurityTests.Probe.class, IdentitySecurityTests.DecoderFixture.class})
class IdentitySecurityTests {
    @Autowired MockMvc mvc;
    @TestConfiguration(proxyBeanMethods = false)
    static class DecoderFixture {
        @Bean
        JwtDecoder decoder() {
            return token -> {
                var builder = Jwt.withTokenValue(token).header("alg", "RS256").subject("demo");
                if (token.equals("valid")) {
                    builder.claim("tenant_id", UUID.randomUUID().toString())
                        .claim("realm_access", Map.of("roles", List.of("ANALYST")));
                } else if (token.equals("malformed")) {
                    builder.claim("tenant_id", "1-1-1-1-1");
                }
                return builder.build();
            };
        }
    }

    @RestController
    static class Probe {
        @GetMapping("/identity-test")
        List<String> identity(Authentication auth) {
            return auth.getAuthorities().stream().map(Object::toString).toList();
        }
    }

    @Test
    void requiresBearer() throws Exception {
        mvc.perform(get("/identity-test")).andExpect(status().isUnauthorized());
    }

    @Test
    void mapsKeycloakRoles() throws Exception {
        mvc.perform(get("/identity-test").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value("ROLE_ANALYST"));
    }

    @Test
    void cannotInjectTenantThroughHeader() throws Exception {
        mvc.perform(get("/identity-test").header("Authorization", "Bearer missing")
                .header("X-Tenant-Id", UUID.randomUUID().toString())).andExpect(status().isForbidden());
    }

    @Test
    void rejectsMalformedTenant() throws Exception {
        mvc.perform(get("/identity-test").header("Authorization", "Bearer malformed"))
                .andExpect(status().isForbidden());
    }
}
