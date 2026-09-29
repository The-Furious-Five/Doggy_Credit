package com.doggycredit.shared.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

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
		return http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> { }))
				.build();
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
