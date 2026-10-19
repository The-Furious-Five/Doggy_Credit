package com.doggycredit.integration.internal;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.doggycredit.audit.AuditTrail;
import com.doggycredit.integration.ConnectorCredentials;
import com.doggycredit.shared.FieldEncryption;
import com.doggycredit.shared.TenantContext;

@Service
class IntegrationCredentialsService implements ConnectorCredentials {
    record State(boolean configured, String lastFour) { }

    private final JdbcTemplate jdbc;
    private final FieldEncryption encryption;
    private final AuditTrail audit;
    private final TransactionTemplate reads;
    private final Map<Provider, String> defaults;

    IntegrationCredentialsService(JdbcTemplate jdbc, FieldEncryption encryption, AuditTrail audit,
            PlatformTransactionManager manager,
            @Value("${doggycredit.integration.bureau.api-key:}") String bureau,
            @Value("${doggycredit.integration.bank.api-key:}") String bank,
            @Value("${doggycredit.integration.bank-dos.api-key:}") String bankDos,
            @Value("${doggycredit.integration.wallet.api-key:}") String wallet) {
        this.jdbc = jdbc;
        this.encryption = encryption;
        this.audit = audit;
        reads = new TransactionTemplate(manager);
        reads.setReadOnly(true);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        defaults = Map.of(Provider.BURO, bureau, Provider.BANCO, bank,
                Provider.BANCO_DOS, bankDos, Provider.WALLET, wallet);
    }

    @Transactional(readOnly = true)
    public State state(UUID tenantId, Provider provider) {
        return jdbc.query("SELECT last_four FROM integration.tenant_credentials WHERE tenant_id = ? AND provider = ?",
                (rs, row) -> new State(true, rs.getString(1)), tenantId, provider.name())
            .stream().findFirst().orElse(new State(false, ""));
    }

    @Transactional
    public State save(UUID tenantId, Provider provider, String key) {
        // Defensa adicional al contrato: nunca propagar el valor rechazado ni permitir CR/LF en headers.
        if (key == null || !key.matches("[!-~]{8,2048}")) {
            throw new IllegalArgumentException("La clave debe tener de 8 a 2048 caracteres ASCII visibles.");
        }
        String lastFour = key.substring(key.length() - 4);
        UUID id = jdbc.queryForObject("""
                INSERT INTO integration.tenant_credentials(id, tenant_id, provider, secret_ciphertext, last_four)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, provider) DO UPDATE SET
                    secret_ciphertext = EXCLUDED.secret_ciphertext, last_four = EXCLUDED.last_four, updated_at = now()
                RETURNING id
                """, UUID.class, UUID.randomUUID(), tenantId, provider.name(), encryption.encrypt(tenantId, key), lastFour);
        audit.record(tenantId, "INTEGRATION_CREDENTIALS_SAVED", "INTEGRATION_CREDENTIALS_" + provider.name(), id);
        return new State(true, lastFour);
    }

    @Transactional
    public void delete(UUID tenantId, Provider provider) {
        jdbc.query("DELETE FROM integration.tenant_credentials WHERE tenant_id = ? AND provider = ? RETURNING id",
                (rs, row) -> rs.getObject(1, UUID.class), tenantId, provider.name())
            .forEach(id -> audit.record(tenantId, "INTEGRATION_CREDENTIALS_DELETED",
                    "INTEGRATION_CREDENTIALS_" + provider.name(), id));
    }

    @Override
    public String apiKey(UUID tenantId, Provider provider) {
        // ConnectorGateway ejecuta fetch en workers sin contexto. El ID ya viene del JWT validado.
        // Abrir una conexión nueva después de instalar el tenant aplica RLS incluso en esos workers.
        UUID previous = TenantContext.currentOrNone();
        TenantContext.set(tenantId);
        try {
            return reads.execute(tx -> jdbc.query(
                    "SELECT secret_ciphertext FROM integration.tenant_credentials WHERE tenant_id = ? AND provider = ?",
                    (rs, row) -> encryption.decrypt(tenantId, rs.getString(1)), tenantId, provider.name())
                .stream().findFirst().orElse(defaults.get(provider)));
        }
        finally {
            if (previous == null) TenantContext.clear();
            else TenantContext.set(previous);
        }
    }
}
