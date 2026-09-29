package com.doggycredit.shared.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.access.AccessDeniedException;
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
import com.doggycredit.shared.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Integración de identidad SPIKE-2.
 * <p>
 * RF-API-02.1: también registra el {@link CorrelationFilter} como
 * {@link FilterRegistrationBean} con la prioridad más alta, para que el id de
 * correlación exista antes de que corra la cadena de Spring Security. Vive
 * aquí (y no en una configuración de seguridad separada) porque Spring
 * Security solo admite un {@link SecurityFilterChain} que responda a
 * "cualquier ruta"; tener dos —uno por configuración— rompe el arranque de
 * la aplicación con {@code UnreachableFilterChainException}.
 * <p>
 * SPIKE-1: {@code TenantClaimFilter} es también el único lugar que puebla
 * {@link TenantContext} tras validar el {@code tenant_id} del JWT. El
 * resto del aislamiento por tenant (el discriminador {@code @TenantId} de
 * Hibernate y la variable de sesión de PostgreSQL para RLS) vive aparte,
 * en {@link TenancyDataSourceConfiguration} — ver esa clase para el porqué
 * de la separación.
 */
@Configuration(proxyBeanMethods = false)
class IdentitySecurityConfiguration {
    @Bean
    SecurityFilterChain identitySecurity(HttpSecurity http,
            ProblemDetailAuthenticationEntryPoint entryPoint,
            ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
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
            .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
            .oauth2ResourceServer(o -> {
                o.jwt(j -> j.jwtAuthenticationConverter(converter));
                // El recurso OAuth2 registra su propio AuthenticationEntryPoint por
                // defecto (sin cuerpo RFC 9457); este lo reemplaza explícitamente.
                o.authenticationEntryPoint(entryPoint);
            })
            .addFilterBefore(new TenantClaimFilter(), AuthorizationFilter.class).build();
    }

    @Bean
    FilterRegistrationBean<CorrelationFilter> correlationFilterRegistration(
            @Value("${doggycredit.correlation.header:X-Correlation-Id}") String headerName) {
        FilterRegistrationBean<CorrelationFilter> reg = new FilterRegistrationBean<>(
                new CorrelationFilter(headerName));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        return reg;
    }

    private static class TenantClaimFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwt) {
                String tenant = jwt.getToken().getClaimAsString("tenant_id");
                try {
                    if (tenant == null || !UUID.fromString(tenant).toString().equalsIgnoreCase(tenant)
                            || jwt.getToken().getSubject() == null) {
                        // Lanzar (no response.sendError) para que ExceptionTranslationFilter
                        // la enrute a ProblemDetailAccessDeniedHandler: sendError salta
                        // ProblemDetailAdvice y responde sin el formato RFC 9457 del resto
                        // de la API, ni correlationId.
                        throw new AccessDeniedException("tenant_id inválido o ausente");
                    }
                } catch (IllegalArgumentException e) {
                    throw new AccessDeniedException("tenant_id inválido o ausente", e);
                }
                TenantContext.set(UUID.fromString(tenant));
            }
            try {
                chain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
        }
    }
}
