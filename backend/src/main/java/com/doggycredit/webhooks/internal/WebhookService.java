package com.doggycredit.webhooks.internal;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.billing.Feature;
import com.doggycredit.billing.PlanCatalog;
import com.doggycredit.billing.PlanGate;
import com.doggycredit.shared.ErrorCode;
import com.doggycredit.shared.FieldEncryption;
import com.doggycredit.shared.TenantContext;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantRegistry;

import tools.jackson.databind.json.JsonMapper;

@Service
class WebhookService {
    static final List<String> EVENTS = List.of("ConsentGranted.v1", "ConsentRevoked.v1", "CreditAssessmentCompleted.v1");
    static final int MAX_ATTEMPTS = 5;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    record Configuration(String url, List<String> events, boolean secretConfigured, Instant updatedAt) {}
    private record Subscription(String url, List<String> events, String secret, Instant updatedAt) {
        Configuration view() { return new Configuration(url, events, secret != null, updatedAt); }
    }
    private record Delivery(UUID id, String type, String url, String secret, String payload, int attempts) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final TenantRegistry tenants;
    private final PlanGate gate;
    private final PlanCatalog plans;
    private final FieldEncryption encryption;
    private final WebhookTarget targets;
    private final WebhookHttp http;

    WebhookService(JdbcTemplate jdbc, TransactionTemplate transactions, TenantRegistry tenants, PlanGate gate,
            PlanCatalog plans, FieldEncryption encryption, WebhookTarget targets, WebhookHttp http) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactions.getTransactionManager());
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tenants = tenants;
        this.gate = gate;
        this.plans = plans;
        this.encryption = encryption;
        this.targets = targets;
        this.http = http;
    }

    Configuration get(UUID tenant) {
        requirePlan(tenant);
        return scoped(tenant, () -> {
            Subscription current = subscription(tenant, false);
            return current == null ? new Configuration(null, List.of(), false, null) : current.view();
        });
    }

    Configuration put(UUID tenant, java.net.URI url, List<String> events, String secret) {
        requirePlan(tenant);
        try {
            if (events == null || events.size() > 3 || !EVENTS.containsAll(events)
                    || events.stream().distinct().count() != events.size()
                    || (url == null) != events.isEmpty()) {
                throw new IllegalArgumentException("Selecciona eventos válidos y una URL, o desactiva ambos.");
            }
            if (url != null) targets.validate(url);
            if (secret != null && (secret.length() < 32 || secret.length() > 128)) {
                throw new IllegalArgumentException("El secreto debe tener entre 32 y 128 caracteres.");
            }
            return scoped(tenant, () -> {
                // Crea la fila antes de bloquearla: dos primeras configuraciones
                // concurrentes también deben serializarse, sin cancelar una cola ajena.
                jdbc.update("""
                    insert into webhooks.subscriptions (tenant_id, events, updated_at)
                    values (?, '[]'::jsonb, ?) on conflict (tenant_id) do nothing
                    """, tenant, Timestamp.from(Instant.now()));
                Subscription old = subscription(tenant, true);
                String encryptedSecret = old == null ? null : old.secret();
                boolean rotated = secret != null && (encryptedSecret == null
                        || !secret.equals(encryption.decrypt(tenant, encryptedSecret)));
                if (rotated) encryptedSecret = encryption.encrypt(tenant, secret);
                if (url != null && encryptedSecret == null) {
                    throw new IllegalArgumentException("La primera configuración requiere un secreto HMAC.");
                }
                String destination = url == null ? null : url.toString();
                if (destination == null) encryptedSecret = null;
                boolean changed = old != null && (!java.util.Objects.equals(old.url(), destination)
                        || !java.util.Set.copyOf(old.events()).equals(java.util.Set.copyOf(events)) || rotated);
                if (changed) cancelPending(tenant);
                Instant now = Instant.now();
                jdbc.update("""
                    insert into webhooks.subscriptions (tenant_id, url, events, secret_ciphertext, updated_at)
                    values (?, ?, cast(? as jsonb), ?, ?)
                    on conflict (tenant_id) do update set url = excluded.url, events = excluded.events,
                        secret_ciphertext = excluded.secret_ciphertext, updated_at = excluded.updated_at
                    """, tenant, destination, JSON.writeValueAsString(events), encryptedSecret, Timestamp.from(now));
                return new Configuration(destination, List.copyOf(events), encryptedSecret != null, now);
            });
        } catch (IllegalArgumentException e) {
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR.toProblemDetail(e.getMessage()), null);
        }
    }

    /** El outbox solo se completa después de confirmar esta inserción idempotente. */
    void enqueue(UUID tenant, UUID eventId, String type, Object event) {
        scoped(tenant, () -> {
            if (!enabled(tenant)) return null;
            Subscription current = subscription(tenant, true);
            if (current == null || current.url() == null || !current.events().contains(type)) return null;
            jdbc.update("""
                insert into webhooks.deliveries
                    (tenant_id, event_id, event_type, url, secret_ciphertext, payload_ciphertext, next_attempt_at)
                values (?, ?, ?, ?, ?, ?, ?) on conflict (tenant_id, event_id) do nothing
                """, tenant, eventId, type, current.url(), current.secret(),
                    encryption.encrypt(tenant, JSON.writeValueAsString(event)), Timestamp.from(Instant.now()));
            return null;
        });
    }

    /** Una entrega por transacción; SKIP LOCKED impide envíos simultáneos del mismo recurso. */
    boolean deliverNext(UUID tenant, Instant now) {
        return Boolean.TRUE.equals(scoped(tenant, () -> {
            if (!enabled(tenant)) {
                cancelPending(tenant);
                return false;
            }
            List<Delivery> pending = jdbc.query("""
                select event_id, event_type, url, secret_ciphertext, payload_ciphertext, attempts
                from webhooks.deliveries where tenant_id = ? and status = 'PENDING' and next_attempt_at <= ?
                order by next_attempt_at, event_id limit 1 for update skip locked
                """, (rs, n) -> new Delivery(rs.getObject("event_id", UUID.class), rs.getString("event_type"),
                    rs.getString("url"), rs.getString("secret_ciphertext"), rs.getString("payload_ciphertext"),
                    rs.getInt("attempts")), tenant, Timestamp.from(now));
            if (pending.isEmpty()) return false;
            Delivery delivery = pending.getFirst();
            Integer status = null;
            try {
                status = http.send(delivery.url(), delivery.type(), delivery.id(),
                        encryption.decrypt(tenant, delivery.secret()), encryption.decrypt(tenant, delivery.payload()), now);
            } catch (RuntimeException e) {
                // Sin URL, secreto, payload ni mensajes del proveedor en los logs/BD.
            }
            int attempts = delivery.attempts() + 1;
            boolean success = status != null && status >= 200 && status < 300;
            jdbc.update("""
                update webhooks.deliveries set status = ?, attempts = ?, next_attempt_at = ?,
                    last_http_status = ?, delivered_at = ? where tenant_id = ? and event_id = ?
                """, success ? "DELIVERED" : attempts >= MAX_ATTEMPTS ? "FAILED" : "PENDING",
                    attempts, Timestamp.from(now.plus(backoff(attempts))), status,
                    success ? Timestamp.from(now) : null, tenant, delivery.id());
            return true;
        }));
    }

    static Duration backoff(int attempts) {
        return Duration.ofSeconds(30L << Math.min(Math.max(attempts - 1, 0), 4));
    }

    private Subscription subscription(UUID tenant, boolean lock) {
        List<Subscription> rows = jdbc.query("""
            select url, events::text as events, secret_ciphertext, updated_at from webhooks.subscriptions
            where tenant_id = ?
            """ + (lock ? " for update" : ""), (rs, n) -> new Subscription(rs.getString("url"),
                JSON.readTree(rs.getString("events")).valueStream().map(node -> node.asString()).toList(),
                rs.getString("secret_ciphertext"), rs.getTimestamp("updated_at").toInstant()), tenant);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void cancelPending(UUID tenant) {
        jdbc.update("update webhooks.deliveries set status = 'CANCELLED' where tenant_id = ? and status = 'PENDING'", tenant);
    }

    private void requirePlan(UUID tenant) {
        gate.requireFeature(tenants.requireById(new TenantId(tenant)).plan(), Feature.WEBHOOKS);
    }

    private boolean enabled(UUID tenant) {
        var current = tenants.requireById(new TenantId(tenant));
        return current.activo() && plans.requireById(current.plan()).webhooks();
    }

    private <T> T scoped(UUID tenant, Supplier<T> action) {
        UUID previous = TenantContext.currentOrNone();
        TenantContext.set(tenant);
        try {
            // El contexto debe existir ANTES de abrir la conexión/EntityManager, también en un listener async.
            return transactions.execute(tx -> action.get());
        } finally {
            if (previous == null) TenantContext.clear(); else TenantContext.set(previous);
        }
    }
}
