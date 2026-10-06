package com.doggycredit.tenancy.internal;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TenantPreferencesService {
    private final JdbcTemplate jdbc;

    TenantPreferencesService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    TenantSecurityPreferences get(UUID tenantId) {
        return jdbc.query(
                "select tenant_id, enmascarar_datos from tenancy.tenant_preferences where tenant_id = ?",
                (rs, row) -> new TenantSecurityPreferences(rs.getObject("tenant_id", UUID.class), rs.getBoolean("enmascarar_datos")),
                tenantId).stream().findFirst().orElseGet(() -> new TenantSecurityPreferences(tenantId, true));
    }

    @Transactional
    TenantSecurityPreferences update(UUID tenantId, boolean enmascararDatos) {
        jdbc.update("""
                insert into tenancy.tenant_preferences (tenant_id, enmascarar_datos) values (?, ?)
                on conflict (tenant_id) do update set enmascarar_datos = excluded.enmascarar_datos
                """, tenantId, enmascararDatos);
        return get(tenantId);
    }
}
