package com.doggycredit.shared.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * RF-SEG-01.1: cadena de seguridad base. Toda ruta privada sin token responde
 * 401; CSRF desactivado (API stateless con bearer tokens); sesiones STATELESS.
 * <p>
 * Rutas públicas: {@code /actuator/health} (sondas de Compose/k8s).
 * Todo lo demás exige autenticación OAuth2 (JWT de Keycloak).
 * <p>
 * RF-API-02.1: registra el {@link CorrelationFilter} como
 * {@link FilterRegistrationBean} con la prioridad más alta para que el id
 * de correlación esté disponible antes de cualquier otro filtro.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
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
		return http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health").permitAll()
						.requestMatchers("/actuator/**").denyAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
				.addFilterBefore(new TenantClaimFilter(), AuthorizationFilter.class)
				.build();
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
						response.sendError(403);
						return;
					}
				} catch (IllegalArgumentException e) {
					response.sendError(403);
					return;
				}
			}
			chain.doFilter(request, response);
		}
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

}
