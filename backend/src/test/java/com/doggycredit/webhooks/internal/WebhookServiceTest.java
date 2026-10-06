package com.doggycredit.webhooks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.doggycredit.billing.Feature;
import com.doggycredit.billing.FeatureNotInPlanException;
import com.doggycredit.billing.PlanCatalog;
import com.doggycredit.billing.PlanGate;
import com.doggycredit.shared.FieldEncryption;
import com.doggycredit.shared.TenantContext;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantRegistry;

class WebhookServiceTest {
    private final UUID tenant = UUID.randomUUID();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final PlanGate gate = mock(PlanGate.class);
    private final TenantRegistry tenants = mock(TenantRegistry.class);
    private WebhookService service;

    @BeforeEach
    void setup() {
        when(tenants.requireById(new TenantId(tenant))).thenReturn(new TenantInfo(tenant, "Tenant", "test", "FINTECH", "PRO", "BO", "BOB", true));
        when(manager.getTransaction(any())).thenAnswer(invocation -> {
            assertThat(TenantContext.currentOrNone()).isEqualTo(tenant);
            return new SimpleTransactionStatus();
        });
        service = new WebhookService(jdbc, new TransactionTemplate(manager), tenants, gate, mock(PlanCatalog.class),
                mock(FieldEncryption.class), new WebhookTarget(true), mock(WebhookHttp.class));
    }

    @Test
    void elPlanDelRegistroSeVerificaAntesDeLeerOEscribirDatos() {
        doThrow(new FeatureNotInPlanException("WEBHOOKS", "Gratis")).when(gate).requireFeature("PRO", Feature.WEBHOOKS);
        assertThatThrownBy(() -> service.get(tenant)).isInstanceOf(FeatureNotInPlanException.class);
        assertThatThrownBy(() -> service.put(tenant, URI.create("http://localhost/events"), List.of("ConsentGranted.v1"), "x".repeat(32)))
                .isInstanceOf(FeatureNotInPlanException.class);
        verifyNoInteractions(jdbc, manager);
    }

    @Test
    void rechazaEventosDesconocidosDuplicadosConfiguracionIncompletaYSecretoCorto() {
        URI url = URI.create("http://localhost/events");
        for (var events : List.of(List.of("unknown"), List.of("ConsentGranted.v1", "ConsentGranted.v1"), List.<String>of())) {
            assertThatThrownBy(() -> service.put(tenant, url, events, "x".repeat(32)))
                    .isInstanceOf(org.springframework.web.ErrorResponseException.class);
        }
        assertThatThrownBy(() -> service.put(tenant, null, List.of("ConsentGranted.v1"), null))
                .isInstanceOf(org.springframework.web.ErrorResponseException.class);
        assertThatThrownBy(() -> service.put(tenant, url, List.of("ConsentGranted.v1"), "short"))
                .isInstanceOf(org.springframework.web.ErrorResponseException.class);
        verifyNoInteractions(jdbc, manager);
    }

    @Test
    @SuppressWarnings("unchecked")
    void laTransaccionSeAbreConElTenantCorrectoYSeRestauraElContextoDelLlamador() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        UUID previous = UUID.randomUUID();
        TenantContext.set(previous);
        try {
            assertThat(service.get(tenant)).isEqualTo(new WebhookService.Configuration(null, List.of(), false, null));
            assertThat(TenantContext.currentOrNone()).isEqualTo(previous);
        } finally { TenantContext.clear(); }
    }

    @Test
    @SuppressWarnings("unchecked")
    void unFalloDePersistenciaNoFiltraElTenantYRevierteLaTransaccion() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"));
        assertThatThrownBy(() -> service.get(tenant)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(TenantContext.currentOrNone()).isNull();
        verify(manager).rollback(any());
    }
}
