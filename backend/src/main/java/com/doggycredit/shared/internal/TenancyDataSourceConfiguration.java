package com.doggycredit.shared.internal;

import java.util.UUID;

import javax.sql.DataSource;

import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

import org.springframework.beans.factory.ObjectProvider;
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
     * Dos conexiones a la misma base, con dos propósitos distintos. Ambas
     * se construyen a mano, a partir de {@link JdbcConnectionDetails} y no
     * de las propiedades {@code spring.datasource.*} directamente, para
     * que funcionen igual en producción y en los tests con Testcontainers
     * (la URL dinámica que arma {@code @ServiceConnection} llega por ahí,
     * no por properties).
     * <p>
     * Se definen los dos bean a mano —no se deja que
     * {@code DataSourceAutoConfiguration} cree el suyo y se envuelve
     * después— porque intentarlo así (bean de tipo {@code DataSource}
     * adicional, o un {@code BeanPostProcessor} que lo envuelve) hace que
     * {@code @ConditionalOnMissingBean(DataSource.class)} nunca cree el
     * original, o que otros beans terminen enganchados al bean equivocado
     * sin un {@code @Primary} explícito. Comprobado dos veces: primero con
     * una referencia circular en el arranque, después con
     * {@code JdbcTemplate.getDataSource()} devolviendo el datasource de
     * Flyway en vez del de la aplicación.
     */
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource rawAppDataSource(ObjectProvider<JdbcConnectionDetails> details,
            DataSourceProperties properties) {
        return builderFor(details, properties).type(HikariDataSource.class).build();
    }

    /**
     * La que usan JPA y JdbcTemplate: hace {@code SET ROLE} y fija
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
        return builderFor(details, properties).type(SimpleDriverDataSource.class).build();
    }

    /**
     * {@code @ServiceConnection} (tests) registra un {@link JdbcConnectionDetails};
     * en producción no existe ninguno: Spring Boot solo crea el suyo dentro de
     * la configuración que se desactiva cuando ya hay un {@code DataSource}
     * definido, como aquí. Por eso se cae a {@link DataSourceProperties}
     * ({@code spring.datasource.*}), que sí se registra siempre. Lo cubre
     * {@code ProductionDataSourceWiringTest}.
     */
    private static DataSourceBuilder<?> builderFor(ObjectProvider<JdbcConnectionDetails> details,
            DataSourceProperties properties) {
        JdbcConnectionDetails connection = details.getIfAvailable();
        if (connection != null) {
            return DataSourceBuilder.create()
                    .url(connection.getJdbcUrl())
                    .username(connection.getUsername())
                    .password(connection.getPassword());
        }
        return DataSourceBuilder.create()
                .url(properties.determineUrl())
                .username(properties.determineUsername())
                .password(properties.determinePassword());
    }
}
