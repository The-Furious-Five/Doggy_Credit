package com.doggycredit.shared.internal;

import java.util.UUID;

import javax.sql.DataSource;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import com.zaxxer.hikari.HikariDataSource;

/**
 * SPIKE-1: aislamiento por tenant a nivel de persistencia — separado de
 * {@link IdentitySecurityConfiguration} (que solo se ocupa de la cadena de
 * seguridad HTTP) a propósito: un {@code @WebMvcTest} que hace
 * {@code @Import(IdentitySecurityConfiguration.class)} para probar solo la
 * capa web no debería arrastrar datasources ni Hibernate. Mezclarlos en la
 * misma clase rompía justo esa prueba (necesita un bean
 * {@code JdbcConnectionDetails} que ese slice de test nunca provee).
 * <p>
 * {@code TenantClaimFilter}, en {@link IdentitySecurityConfiguration}, es
 * el único lugar que puebla {@link TenantContext} tras validar el
 * {@code tenant_id} del JWT. De ahí se alimentan las dos capas
 * independientes de aislamiento que se definen aquí: el discriminador
 * {@code @TenantId} de Hibernate ({@link TenantIdentifierResolver}) y la
 * variable de sesión de PostgreSQL para Row-Level Security
 * ({@link TenantAwareDataSource}).
 */
@Configuration(proxyBeanMethods = false)
class TenancyDataSourceConfiguration {

    /** Alimenta el discriminador {@code @TenantId} de Hibernate desde {@link TenantContext}. */
    @Bean
    CurrentTenantIdentifierResolver<UUID> tenantIdentifierResolver() {
        return new TenantIdentifierResolver();
    }

    @Bean
    HibernatePropertiesCustomizer tenantIdentifierResolverCustomizer(
            CurrentTenantIdentifierResolver<UUID> resolver) {
        return properties -> properties.put(
                MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }

    /**
     * La conexión de aplicación usa credenciales restringidas de
     * {@code doggycredit.datasource.*}; la de Flyway conserva las credenciales
     * administrativas de {@code spring.datasource.*}. Ambas reutilizan la URL
     * dinámica de {@link JdbcConnectionDetails} cuando Testcontainers la aporta.
     */
    @Bean
    @ConfigurationProperties("doggycredit.datasource.hikari")
    HikariDataSource rawAppDataSource(ObjectProvider<JdbcConnectionDetails> details,
            @Value("${doggycredit.datasource.url}") String url,
            @Value("${doggycredit.datasource.username}") String username,
            @Value("${doggycredit.datasource.password}") String password) {
        return builderFor(details, url, username, password).type(HikariDataSource.class).build();
    }

    /**
     * La que usan JPA y JdbcTemplate: fija
     * {@code app.tenant_id} en cada conexión (ver {@link TenantAwareDataSource}).
     */
    @Bean
    @Primary
    DataSource dataSource(HikariDataSource rawAppDataSource) {
        return new TenantAwareDataSource(rawAppDataSource);
    }

    /**
     * La que usa Flyway: nunca pasa por {@link TenantAwareDataSource}.
     * Migrar necesita privilegios de administrador —crea el rol
     * restringido, entre otras cosas— y corre antes de que ese rol exista.
     * Sin pool propio (un {@link SimpleDriverDataSource}): Flyway abre unas
     * pocas conexiones secuenciales al arrancar, no necesita HikariCP.
     */
    @Bean
    @FlywayDataSource
    DataSource flywayDataSource(ObjectProvider<JdbcConnectionDetails> details,
            DataSourceProperties properties) {
        return builderFor(details, properties.determineUrl(), properties.determineUsername(), properties.determinePassword())
                .type(SimpleDriverDataSource.class).build();
    }

    private static DataSourceBuilder<?> builderFor(ObjectProvider<JdbcConnectionDetails> details,
            String url, String username, String password) {
        JdbcConnectionDetails connection = details.getIfAvailable();
        if (connection != null) {
            return DataSourceBuilder.create()
                    .url(connection.getJdbcUrl())
                    .username(username)
                    .password(password);
        }
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password);
    }
}
