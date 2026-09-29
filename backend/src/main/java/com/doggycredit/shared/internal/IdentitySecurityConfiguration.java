package com.doggycredit.shared.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Integración de identidad SPIKE-2. El aislamiento de datos sigue siendo HAB-03. */
@Configuration(proxyBeanMethods = false)
class IdentitySecurityConfiguration {
    @Bean
    SecurityFilterChain identitySecurity(HttpSecurity http) throws Exception {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> roles = new ArrayList<>();
            Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
            if (realm != null && realm.get("roles") instanceof List<?> names) {
                names.stream().filter(String.class::isInstance)
                    .forEach(role -> roles.add(new SimpleGrantedAuthority("ROLE_" + role)));
            }
            return roles;
        });
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").denyAll().anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))
            .addFilterBefore(new TenantClaimFilter(), AuthorizationFilter.class).build();
    }

    private static class TenantClaimFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwt) {
                try {
                    String tenant = jwt.getToken().getClaimAsString("tenant_id");
                    if (tenant == null || !UUID.fromString(tenant).toString().equalsIgnoreCase(tenant)
                            || jwt.getToken().getSubject() == null) {
                        response.sendError(403); return;
                    }
                } catch (IllegalArgumentException e) { response.sendError(403); return; }
            }
            chain.doFilter(request, response);
        }
    }
}
